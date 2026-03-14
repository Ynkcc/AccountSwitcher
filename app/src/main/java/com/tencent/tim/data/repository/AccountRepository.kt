package com.tencent.tim.data.repository

import android.util.Log
import com.tencent.tim.data.local.AccountDao
import com.tencent.tim.data.local.AccountEntity
import com.tencent.tim.data.remote.TencentGameApi
import com.tencent.tim.data.system.RootManager
import com.tencent.tim.utils.MSDKTea
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.net.URLDecoder

class AccountRepository(
    private val accountDao: AccountDao,
    private val api: TencentGameApi,
    private val rootManager: RootManager,
    private val fileDataSource: AccountFileDataSource
) {
    companion object {
        private const val TAG = "AccountRepository"
    }

    val allAccounts: Flow<List<AccountEntity>> = accountDao.getAllAccounts()

    suspend fun saveCurrentAccount(): Result<String> {
        return fileDataSource.readCurrentAccount().mapCatching { entity ->
            val accessToken = entity.accessToken
            if (accessToken.isNotEmpty()) {
                fetchRemoteRoleInfo(entity.openid, accessToken)?.let { remoteInfo ->
                    // Update entity with remote info
                    entity.roleName = remoteInfo.roleName
                    entity.roleId = remoteInfo.roleId
                    entity.level = remoteInfo.level
                    entity.isOnline = remoteInfo.isOnline
                    entity.isBan = remoteInfo.isBan
                    entity.isFace = remoteInfo.isFace
                    entity.aceMark = remoteInfo.aceMark
                    entity.heatValue = remoteInfo.heatValue
                    entity.rank = remoteInfo.rank
                    entity.rankPoints = remoteInfo.rankPoints
                    entity.lastLogout = remoteInfo.lastLogout
                }
            }
            accountDao.insertAccount(entity)
            entity.roleName
        }
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

    suspend fun deleteAccount(openid: String) {
        accountDao.deleteAccountByOpenid(openid)
    }

    suspend fun restartApp(): Boolean {
        return rootManager.restartApp()
    }

    suspend fun clearCurrentAccount(): Boolean {
        return fileDataSource.clearAccount()
    }
}
