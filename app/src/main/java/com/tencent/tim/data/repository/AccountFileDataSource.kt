package com.tencent.tim.data.repository

import android.util.Log
import com.tencent.tim.data.local.AccountEntity
import com.tencent.tim.data.system.RootManager
import com.tencent.tim.utils.MSDKTea
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class LoginJson(
    val ret: Int = 0,
    val msg: String = "success",
    val openid: String = "",
    val user_name: String = "",
    val picture_url: String = "",
    @SerialName("channel_info") val channelInfo: ChannelInfoJson? = null,
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
internal data class ChannelInfoJson(
    val access_token: String = "",
    val pay_token: String = "",
    val expired: Int = 0,
    val expire_ts: Long = 0,
    val channel_openid: String = "",
    val health_game_ext: String = ""
)

class AccountFileDataSource(
    private val rootManager: RootManager
) {
    companion object {
        private const val TAG = "AccountFileDataSource"
        private val TEA_KEY = "ITOPITOPITOPITOP".toByteArray()
        private val jsonParser = Json { 
            ignoreUnknownKeys = true 
            encodeDefaults = true
        }
    }

    fun readCurrentAccount(): Result<AccountEntity> {
        val data = rootManager.readFile(rootManager.loginFilePath) 
            ?: return Result.failure(Exception("无法读取登录文件"))

        val candidates = mutableListOf<Pair<String, ByteArray>>()
        val decrypted = MSDKTea.decrypt(data, TEA_KEY)
        if (decrypted != null) {
            candidates += "decrypted" to decrypted
        } else {
            Log.w(TAG, "Decrypt failed, fallback to raw JSON parse")
        }
        candidates += "raw" to data

        var lastError: Exception? = null
        for ((source, bytes) in candidates) {
            parseLoginJson(bytes, source)?.let { loginJson ->
                if (loginJson.openid.isNotEmpty()) {
                    return Result.success(loginJsonToEntity(loginJson))
                }
                lastError = Exception("未找到 OpenID")
            }
        }

        val error = lastError ?: Exception("登录文件解析失败")
        Log.e(TAG, "Failed to parse itop_login.txt JSON after all fallbacks", error)
        return Result.failure(error)
    }

    private fun parseLoginJson(bytes: ByteArray, source: String): LoginJson? {
        val rawText = String(bytes, Charsets.UTF_8)
        val jsonCandidate = extractJsonBlock(rawText) ?: run {
            Log.w(TAG, "[$source] no JSON object boundary found")
            return null
        }

        return try {
            jsonParser.decodeFromString<LoginJson>(jsonCandidate)
        } catch (firstError: Exception) {
            val cleaned = jsonCandidate.replace("\uFFFD", "")
            if (cleaned == jsonCandidate) {
                Log.e(TAG, "[$source] JSON parse failed", firstError)
                null
            } else {
                try {
                    Log.w(TAG, "[$source] retry parse after replacement-char cleanup")
                    jsonParser.decodeFromString<LoginJson>(cleaned)
                } catch (secondError: Exception) {
                    Log.e(TAG, "[$source] JSON parse failed after cleanup", secondError)
                    null
                }
            }
        }
    }

    private fun extractJsonBlock(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return text.substring(start, end + 1).trim()
    }

    fun writeAccount(entity: AccountEntity): Result<Unit> {
        return try {
            val loginJson = entityToLoginJson(entity)
            val jsonString = jsonParser.encodeToString(loginJson)
            val encrypted = MSDKTea.encrypt(jsonString.toByteArray(Charsets.UTF_8), TEA_KEY) 
                ?: run {
                    Log.e(TAG, "Failed to encrypt account for writing")
                    return Result.failure(Exception("加密失败"))
                }
            
            if (rootManager.writeFile(rootManager.loginFilePath, encrypted)) {
                Result.success(Unit)
            } else {
                Log.e(TAG, "Failed to write encrypted file to system")
                Result.failure(Exception("重写登录文件失败"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process account for writing", e)
            Result.failure(e)
        }
    }

    fun clearAccount(): Boolean {
        return rootManager.deleteFile(rootManager.loginFilePath)
    }

    private fun loginJsonToEntity(json: LoginJson): AccountEntity {
        return AccountEntity(
            openid = json.openid,
            accessToken = json.channelInfo?.access_token ?: "",
            payToken = json.channelInfo?.pay_token ?: "",
            expired = json.channelInfo?.expired ?: 0,
            expireTs = json.channelInfo?.expire_ts ?: 0,
            healthGameExt = json.channelInfo?.health_game_ext ?: "",
            pf = json.pf,
            pfKey = json.pf_key,
            uid = json.uid,
            channel = json.channel,
            channelId = json.channelid,
            token = json.token,
            gender = json.gender,
            birthdate = json.birthdate,
            pictureUrl = json.picture_url,
            userName = json.user_name,
            roleName = json.openid // Default
        )
    }

    private fun entityToLoginJson(entity: AccountEntity): LoginJson {
        val channelInfo = ChannelInfoJson(
            access_token = entity.accessToken,
            pay_token = entity.payToken,
            expired = entity.expired,
            expire_ts = entity.expireTs,
            channel_openid = entity.openid,
            health_game_ext = entity.healthGameExt
        )
        
        return LoginJson(
            ret = 0,
            msg = "success",
            openid = entity.openid,
            user_name = entity.userName,
            picture_url = entity.pictureUrl,
            channelInfo = channelInfo,
            uid = entity.uid,
            pf = entity.pf,
            pf_key = entity.pfKey,
            channelid = entity.channelId,
            token = entity.token,
            gender = entity.gender,
            birthdate = entity.birthdate,
            channel = entity.channel
        )
    }
}
