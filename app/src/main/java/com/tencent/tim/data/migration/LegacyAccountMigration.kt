package com.tencent.tim.data.migration

import android.content.Context
import android.util.Log
import com.tencent.tim.data.local.AccountDao
import com.tencent.tim.data.local.AccountEntity
import com.tencent.tim.utils.MSDKTea
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class LegacyLoginJson(
    val openid: String = "",
    val user_name: String = "",
    val picture_url: String = "",
    @SerialName("channel_info") val channelInfo: LegacyChannelInfoJson? = null,
    val uid: String = "",
    val pf: String = "",
    val pf_key: String = "",
    val channelid: Int = 2,
    val token: String = "",
    val gender: Int = 0,
    val birthdate: String = "",
    val channel: String = "QQ"
)

@Serializable
private data class LegacyChannelInfoJson(
    val access_token: String = "",
    val pay_token: String = "",
    val expired: Int = 0,
    val expire_ts: Long = 0,
    val health_game_ext: String = ""
)

@Serializable
private data class LegacyInfoJson(
    val roleName: String = "未知",
    val roleId: String = "未知",
    val level: String = "0",
    val isOnline: String = "不在线",
    val isBan: String = "正常",
    val isFace: String = "否",
    val aceMark: String = "0",
    val heatValue: String = "0",
    val rank: String = "未知",
    val rankPoints: String = "0",
    val lastLogout: String = "未知"
)

class LegacyAccountMigration(
    private val context: Context,
    private val accountDao: AccountDao
) {
    companion object {
        private const val TAG = "LegacyAccountMigration"
        private const val PREFS_NAME = "migration_state"
        private const val KEY_LEGACY_ACCOUNTS_IMPORTED = "legacy_accounts_imported_v1"
        private val TEA_KEY = "ITOPITOPITOPITOP".toByteArray()
        private val jsonParser = Json {
            ignoreUnknownKeys = true
        }
    }

    suspend fun migrateIfNeeded() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_LEGACY_ACCOUNTS_IMPORTED, false)) return

        val accountsDir = File(context.filesDir, "accounts")
        if (!accountsDir.exists() || !accountsDir.isDirectory) {
            prefs.edit().putBoolean(KEY_LEGACY_ACCOUNTS_IMPORTED, true).apply()
            return
        }

        var importedCount = 0

        accountsDir.listFiles { file -> file.isDirectory }?.forEach { accountDir ->
            val loginDat = File(accountDir, "login.dat")
            if (!loginDat.exists()) return@forEach

            runCatching {
                val encryptedData = loginDat.readBytes()
                val decrypted = MSDKTea.decrypt(encryptedData, TEA_KEY)
                    ?: throw IllegalStateException("login.dat 解密失败")

                val loginJson = jsonParser.decodeFromString<LegacyLoginJson>(
                    String(decrypted, Charsets.UTF_8)
                )

                if (loginJson.openid.isBlank()) {
                    throw IllegalStateException("缺少 openid")
                }

                val info = readLegacyInfo(File(accountDir, "info.json"))
                accountDao.insertAccount(loginJson.toEntity(info))
                importedCount += 1
            }.onFailure { e ->
                Log.e(TAG, "迁移账号失败: ${accountDir.name}", e)
            }
        }

        prefs.edit().putBoolean(KEY_LEGACY_ACCOUNTS_IMPORTED, true).apply()
        Log.i(TAG, "旧版账号迁移完成，导入数量: $importedCount")
    }

    private fun readLegacyInfo(infoFile: File): LegacyInfoJson? {
        if (!infoFile.exists()) return null
        return runCatching {
            jsonParser.decodeFromString<LegacyInfoJson>(infoFile.readText())
        }.getOrNull()
    }

    private fun LegacyLoginJson.toEntity(info: LegacyInfoJson?): AccountEntity {
        return AccountEntity(
            openid = openid,
            roleName = info?.roleName ?: openid,
            roleId = info?.roleId ?: "未知",
            level = info?.level ?: "0",
            isOnline = info?.isOnline ?: "不在线",
            isBan = info?.isBan ?: "正常",
            isFace = info?.isFace ?: "否",
            aceMark = info?.aceMark ?: "0",
            heatValue = info?.heatValue ?: "0",
            rank = info?.rank ?: "未知",
            rankPoints = info?.rankPoints ?: "0",
            lastLogout = info?.lastLogout ?: "未知",
            accessToken = channelInfo?.access_token ?: "",
            payToken = channelInfo?.pay_token ?: "",
            expired = channelInfo?.expired ?: 0,
            expireTs = channelInfo?.expire_ts ?: 0,
            healthGameExt = channelInfo?.health_game_ext ?: "",
            pf = pf,
            pfKey = pf_key,
            uid = uid,
            channel = channel,
            channelId = channelid,
            token = token,
            gender = gender,
            birthdate = birthdate,
            pictureUrl = picture_url,
            userName = user_name
        )
    }
}
