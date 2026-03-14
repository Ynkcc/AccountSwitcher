package com.tencent.tim.data.repository

import android.util.Log
import com.tencent.tim.data.local.AccountDao
import com.tencent.tim.data.local.AccountEntity
import com.tencent.tim.data.remote.TencentGameApi
import com.tencent.tim.data.system.RootManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.json.JSONObject

class AccountRepository(
    private val accountDao: AccountDao,
    private val api: TencentGameApi,
    private val rootManager: RootManager,
    private val fileDataSource: AccountFileDataSource,
    private val transferFileDataSource: AccountTransferFileDataSource
) {
    companion object {
        private const val TAG = "AccountRepository"
        private const val QQ_GAME_APP_ID = "1106467070"
        private const val QQ_ME_URL = "https://openmobile.qq.com/oauth2.0/me"
    }

    val allAccounts: Flow<List<AccountEntity>> = accountDao.getAllAccounts()

    suspend fun saveCurrentAccount(): Result<String> {
        return fileDataSource.readCurrentAccount().mapCatching { entity ->
            val accessToken = entity.accessToken
            if (accessToken.isNotEmpty()) {
                fetchRemoteRoleInfo(entity.openid, accessToken)?.let { remoteInfo ->
                    applyRemoteInfo(entity, remoteInfo)
                    entity.lastUpdateTs = System.currentTimeMillis()
                }
            }
            accountDao.insertAccount(entity)
            entity.roleName
        }
    }

    suspend fun manualImportAccount(
        accessToken: String,
        openid: String,
        payToken: String
    ): Result<String> = runCatching {
        val normalizedAccessToken = accessToken.trim()
        val normalizedOpenidInput = openid.trim()
        val normalizedPayToken = payToken.trim()

        require(normalizedAccessToken.isNotBlank()) { "access_token 不能为空" }

        val openidFromApi = fetchOpenidByAccessToken(normalizedAccessToken)
        val targetOpenid = when {
            normalizedOpenidInput.isBlank() -> openidFromApi
            normalizedOpenidInput.equals(openidFromApi, ignoreCase = true) -> normalizedOpenidInput
            else -> throw IllegalArgumentException("填写的 openid 与 access_token 对应账号不一致")
        }

        val existing = accountDao.getAccountByOpenid(targetOpenid)
        val template = if (existing == null) fileDataSource.readCurrentAccount().getOrNull() else null

        val entity = buildManualImportEntity(
            openid = targetOpenid,
            accessToken = normalizedAccessToken,
            payToken = normalizedPayToken,
            existing = existing,
            template = template
        )

        fetchRemoteRoleInfo(targetOpenid, normalizedAccessToken)?.let { remoteInfo ->
            applyRemoteInfo(entity, remoteInfo)
        }
        entity.lastUpdateTs = System.currentTimeMillis()

        accountDao.insertAccount(entity)
        entity.roleName
    }

    suspend fun switchAccount(openid: String): Result<Unit> {
        val entity = accountDao.getAccountByOpenid(openid) ?: return Result.failure(Exception("账户不存在"))
        return fileDataSource.writeAccount(entity)
    }

    suspend fun buildAgentResponse(openid: String): Result<String> {
        val entity = accountDao.getAccountByOpenid(openid) ?: return Result.failure(Exception("账户不存在"))

        return runCatching {
            JSONObject().apply {
                put("access_token", entity.accessToken)
                put("openid", entity.openid)
                put("pay_token", entity.payToken)
                put("expires_in", entity.expired.toString())
                put("ret", 0)
                put("pf", entity.pf)
                put("page_type", "1")
            }.toString()
        }
    }

    suspend fun refreshAccountsOnlineInfo(): Result<Pair<Int, Int>> = runCatching {
        val accounts = allAccounts.first()
        if (accounts.isEmpty()) {
            return@runCatching 0 to 0
        }

        var refreshedCount = 0
        accounts.forEach { account ->
            if (account.accessToken.isBlank()) {
                return@forEach
            }

            val remoteInfo = fetchRemoteRoleInfo(account.openid, account.accessToken) ?: return@forEach
            applyRemoteInfo(account, remoteInfo)
            account.lastUpdateTs = System.currentTimeMillis()
            accountDao.insertAccount(account)
            refreshedCount++
        }

        refreshedCount to accounts.size
    }

    suspend fun exportAccountsToFile(uriString: String): Result<Int> = runCatching {
        val accounts = allAccounts.first()
        require(accounts.isNotEmpty()) { "暂无账号可导出" }

        transferFileDataSource.exportToUri(
            uriString = uriString,
            payload = AccountTransferPayload(accounts = accounts)
        ).getOrThrow()

        accounts.size
    }

    suspend fun importAccountsFromFile(uriString: String): Result<AccountImportSummary> = runCatching {
        val payload = transferFileDataSource.importFromUri(uriString).getOrThrow()
        require(payload.accounts.isNotEmpty()) { "导入文件中没有账号数据" }

        var insertedCount = 0
        var updatedCount = 0
        var skippedCount = 0
        var invalidCount = 0

        payload.accounts.forEach { rawAccount ->
            val normalizedAccount = normalizeImportedAccount(rawAccount)
            if (normalizedAccount == null) {
                invalidCount++
                return@forEach
            }

            val existing = accountDao.getAccountByOpenid(normalizedAccount.openid)
            when {
                existing == null -> {
                    accountDao.insertAccount(normalizedAccount)
                    insertedCount++
                }

                existing.accessToken != normalizedAccount.accessToken -> {
                    accountDao.insertAccount(
                        normalizedAccount.copy(isSelected = existing.isSelected)
                    )
                    updatedCount++
                }

                else -> skippedCount++
            }
        }

        AccountImportSummary(
            insertedCount = insertedCount,
            updatedCount = updatedCount,
            skippedCount = skippedCount,
            invalidCount = invalidCount
        )
    }

    private suspend fun fetchRemoteRoleInfo(openid: String, accessToken: String): AccountEntity? {
        return try {
            val response = api.getRoleInfo(cookie = "acctype=qc; openid=$openid; access_token=$accessToken; appid=1106467070")
            if (response.isSuccessful) {
                response.body()?.let { body ->
                    TencentRoleParser.parseRoleInfo(openid, body)
                }
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Fetch remote info failed", e)
            null
        }
    }

    private suspend fun fetchOpenidByAccessToken(accessToken: String): String {
        val response = api.queryQqOpenId("$QQ_ME_URL?access_token=$accessToken")
        if (!response.isSuccessful) {
            throw IllegalStateException("校验 access_token 失败: HTTP ${response.code()}")
        }

        val body = response.body().orEmpty()
        val jsonText = extractCallbackJson(body)
            ?: throw IllegalStateException("QQ 返回数据格式异常")
        val json = JSONObject(jsonText)

        val clientId = json.optString("client_id")
        if (clientId != QQ_GAME_APP_ID) {
            throw IllegalArgumentException("该 access_token 不属于本游戏")
        }

        val openid = json.optString("openid")
        if (openid.isBlank()) {
            throw IllegalStateException("未获取到 openid")
        }
        return openid
    }

    private fun extractCallbackJson(raw: String): String? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) {
            return null
        }
        return raw.substring(start, end + 1)
    }

    private fun buildManualImportEntity(
        openid: String,
        accessToken: String,
        payToken: String,
        existing: AccountEntity?,
        template: AccountEntity?
    ): AccountEntity {
        val base = existing
            ?: template?.copy(openid = openid, isSelected = false)
            ?: AccountEntity(openid = openid)

        val entity = base.copy(openid = openid, isSelected = false)
        entity.accessToken = accessToken
        entity.payToken = payToken
        entity.uid = openid
        entity.token = accessToken
        entity.channel = "QQ"
        entity.channelId = 2

        entity.pf = if (entity.pf.isBlank()) {
            buildDefaultPf(openid)
        } else {
            rewritePfOpenid(entity.pf, openid)
        }

        if (entity.expired <= 0) {
            entity.expired = 2_592_000
        }
        if (entity.expireTs <= 0L) {
            entity.expireTs = (System.currentTimeMillis() / 1000) + entity.expired
        }
        if (entity.roleName.isBlank() || entity.roleName == "未知") {
            entity.roleName = openid
        }

        return entity
    }

    private fun rewritePfOpenid(currentPf: String, openid: String): String {
        val parts = currentPf.split("-")
        return if (parts.size >= 2) {
            parts.dropLast(1).plus(openid).joinToString("-")
        } else {
            buildDefaultPf(openid)
        }
    }

    private fun buildDefaultPf(openid: String): String {
        return "qq_qq-10159208-android-10017385-qq-$QQ_GAME_APP_ID-$openid"
    }

    private fun normalizeImportedAccount(account: AccountEntity): AccountEntity? {
        val openid = account.openid.trim()
        val accessToken = account.accessToken.trim()
        if (openid.isBlank() || accessToken.isBlank()) {
            return null
        }

        val normalized = account.copy(
            openid = openid,
            roleName = account.roleName.ifBlank { openid },
            uid = account.uid.ifBlank { openid },
            isSelected = false,
            lastUpdateTs = maxOf(account.lastUpdateTs, System.currentTimeMillis())
        )

        normalized.accessToken = accessToken
        normalized.payToken = account.payToken.trim()
        normalized.uid = normalized.uid.ifBlank { openid }
        normalized.token = normalized.token.ifBlank { accessToken }
        normalized.channel = normalized.channel.ifBlank { "QQ" }
        normalized.channelId = if (normalized.channelId > 0) normalized.channelId else 2
        normalized.pf = if (normalized.pf.isBlank()) {
            buildDefaultPf(openid)
        } else {
            rewritePfOpenid(normalized.pf, openid)
        }

        if (normalized.expired <= 0) {
            normalized.expired = 2_592_000
        }
        if (normalized.expireTs <= 0L) {
            normalized.expireTs = (System.currentTimeMillis() / 1000) + normalized.expired
        }

        return normalized
    }

    suspend fun deleteAccount(openid: String) {
        accountDao.deleteAccountByOpenid(openid)
    }

    suspend fun restartApp(): Boolean {
        return rootManager.restartApp()
    }

    suspend fun clearCurrentAccount(): Boolean {
        return fileDataSource.clearAccount()
    }

    private fun applyRemoteInfo(target: AccountEntity, remoteInfo: AccountEntity) {
        target.roleName = remoteInfo.roleName
        target.roleId = remoteInfo.roleId
        target.level = remoteInfo.level
        target.isOnline = remoteInfo.isOnline
        target.isBan = remoteInfo.isBan
        target.isFace = remoteInfo.isFace
        target.aceMark = remoteInfo.aceMark
        target.heatValue = remoteInfo.heatValue
        target.rank = remoteInfo.rank
        target.rankPoints = remoteInfo.rankPoints
        target.lastLogoutTs = remoteInfo.lastLogoutTs
    }

    suspend fun setSelectedAccount(openid: String) {
        accountDao.setSelectedAccount(openid)
    }

    suspend fun getSelectedAccount(): AccountEntity? {
        return accountDao.getSelectedAccount()
    }
}
