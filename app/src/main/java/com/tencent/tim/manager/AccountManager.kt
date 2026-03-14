package com.tencent.tim.manager

import android.content.Context
import android.util.Log
import com.tencent.tim.utils.MSDKTea
import com.tencent.tim.utils.ShellUtils
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AccountInfo(
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
    val lastLogout: String = "未知",
    val totalRecharge: String = "0",
    val todayLogin: String = "0"
)

data class ChannelInfo(
    val accessToken: String,
    val payToken: String,
    val expired: Int,
    val expireTs: Long,
    val channelOpenid: String,
    val healthGameExt: String,
    val extend: String = "",
    val cgToken: String = "",
    val allowEncryption: Boolean = true,
    val loginType: Int = 1,
    val refreshToken: String = "",
    val scope: String = "",
    val funcs: String = "",
    val otherFuncs: String = ""
) {
    companion object {
        fun fromJson(json: JSONObject): ChannelInfo {
            return ChannelInfo(
                accessToken = json.optString("access_token"),
                payToken = json.optString("pay_token"),
                expired = json.optInt("expired"),
                expireTs = json.optLong("expire_ts"),
                channelOpenid = json.optString("channel_openid"),
                healthGameExt = json.optString("health_game_ext"),
                extend = json.optString("extend"),
                cgToken = json.optString("cgToken"),
                allowEncryption = json.optBoolean("allow_encryption", true),
                loginType = json.optInt("login_type"),
                refreshToken = json.optString("refresh_token"),
                scope = json.optString("scope"),
                funcs = json.optString("funcs"),
                otherFuncs = json.optString("other_funcs")
            )
        }
    }
}

data class Account(
    val ret: Int,
    val msg: String,
    val retCode: Int,
    val retMsg: String,
    val extraJson: String,
    val methodNameID: Int,
    val openid: String,
    val tokenExpireTime: Long,
    val first: Int,
    val regChannelDis: String,
    val userName: String,
    val pictureUrl: String,
    val needNameAuth: Boolean,
    val channelInfo: ChannelInfo,
    val uid: String,
    val healthGameExt: String,
    val seq: String,
    val pfKey: String,
    val bindList: String,
    val confirmCode: String,
    val confirmCodeExpireTime: Long,
    val channelId: Int,
    val token: String,
    val gender: Int,
    val birthdate: String,
    val pf: String,
    val channelIDAttr: Int,
    val channel: String
) {
    companion object {
        fun fromJson(json: JSONObject): Account {
            return Account(
                ret = json.optInt("ret"),
                msg = json.optString("msg"),
                retCode = json.optInt("retCode"),
                retMsg = json.optString("retMsg"),
                extraJson = json.optString("extraJson"),
                methodNameID = json.optInt("methodNameID"),
                openid = json.optString("openid"),
                tokenExpireTime = json.optLong("token_expire_time"),
                first = json.optInt("first"),
                regChannelDis = json.optString("reg_channel_dis"),
                userName = json.optString("user_name"),
                pictureUrl = json.optString("picture_url"),
                needNameAuth = json.optBoolean("need_name_auth"),
                channelInfo = ChannelInfo.fromJson(json.getJSONObject("channel_info")),
                uid = json.optString("uid"),
                healthGameExt = json.optString("health_game_ext"),
                seq = json.optString("seq"),
                pfKey = json.optString("pf_key"),
                bindList = json.optString("bind_list"),
                confirmCode = json.optString("confirm_code"),
                confirmCodeExpireTime = json.optLong("confirm_code_expire_time"),
                channelId = json.optInt("channelid"),
                token = json.optString("token"),
                gender = json.optInt("gender"),
                birthdate = json.optString("birthdate"),
                pf = json.optString("pf"),
                channelIDAttr = json.optInt("channelID"),
                channel = json.optString("channel")
            )
        }
    }
}

data class SaveResult(
    val success: Boolean,
    val characName: String? = null,
    val error: String? = null
)

class AccountManager(val context: Context) {
    companion object {
        private const val TAG = "AccountManager"
        private const val ENABLE_DIAGNOSTICS = false
    }

    private val pubgPackage = "com.tencent.tmgp.pubgmhd"
    private var actualDataPath: String? = null
    private val prefs = context.getSharedPreferences("account_names", Context.MODE_PRIVATE)

    private fun getActualDataPath(): String {
        actualDataPath?.let { return it }
        val paths = listOf("/data/user/0/$pubgPackage", "/data/data/$pubgPackage")
        for (path in paths) {
            val output = ShellUtils.execRootGetOutput("ls -d $path")
            if (output != null && output.contains(pubgPackage)) {
                actualDataPath = path
                Log.d(TAG, "Detected data path: $path")
                return path
            }
        }
        Log.e(TAG, "Failed to detect data path, defaulting to /data/user/0/$pubgPackage")
        return "/data/user/0/$pubgPackage"
    }

    private val pubgFilePath get() = "${getActualDataPath()}/files/itop_login.txt"
    private val accountsDir = File(context.filesProviderDir(), "accounts").apply { if (!exists()) mkdirs() }
    private val teaKey = "ITOPITOPITOPITOP".toByteArray()

    private fun Context.filesProviderDir(): File = filesDir

    private fun getAccountDir(openid: String): File = File(accountsDir, openid).apply { if (!exists()) mkdirs() }

    private fun runDiagnosticsIfNeeded() {
        if (!ENABLE_DIAGNOSTICS) return
        val diag = listOf(
            "id",
            "getenforce",
            "ls -ld /data/user/0",
            "ls -ld /data/data",
            "mount | grep -E '/data | / '"
        )
        diag.forEach { cmd ->
            val out = ShellUtils.execRootGetOutput(cmd)
            Log.d(TAG, "[Diag] $cmd -> $out")
        }
    }

    private fun getRank(pointsStr: String): String {
        val points = pointsStr.toFloatOrNull() ?: return "未知段位"
        return when {
            points >= 4300 -> {
                val stars = ((points - 4300) / 100).toInt() + 1
                "王牌 $stars 星"
            }
            points >= 4200 -> "王牌"
            points >= 3700 -> "皇冠"
            points >= 3200 -> "钻石"
            points >= 2700 -> "铂金"
            points >= 2200 -> "黄金"
            points >= 1700 -> "白银"
            points >= 1200 -> "青铜"
            else -> "未知段位"
        }
    }

    fun refreshOnlineData(openid: String): SaveResult {
        val datFile = File(getAccountDir(openid), "login.dat")
        if (!datFile.exists()) return SaveResult(false, error = "本地数据不存在")
        
        val data = datFile.readBytes()
        val decrypted = MSDKTea.decrypt(data, teaKey) ?: return SaveResult(false, error = "解密失败")
        
        return try {
            val json = JSONObject(String(decrypted, Charsets.UTF_8))
            val account = Account.fromJson(json)
            val accessToken = account.channelInfo.accessToken
            if (accessToken.isNotEmpty()) {
                val info = fetchFullAccountInfo(openid, accessToken)
                if (info != null) {
                    saveAccountInfo(openid, info)
                    prefs.edit().putString(openid, info.roleName).apply()
                    return SaveResult(true, characName = info.roleName)
                }
            }
            SaveResult(false, error = "获取在线数据失败")
        } catch (e: Exception) {
            SaveResult(false, error = "更新失败: ${e.message}")
        }
    }

    private fun fetchFullAccountInfo(openid: String, accessToken: String): AccountInfo? {
        return try {
            val urlStr = "https://comm.aci.game.qq.com/main?game=cjm&area=2&platid=1&sCloudApiName=ams.gameattr.role"
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Cookie", "acctype=qc; openid=$openid; access_token=$accessToken; appid=1106467070")
            conn.setRequestProperty("Referer", "https://gp.qq.com/")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

                val responseCode = conn.responseCode
                Log.d(TAG, "Sync response code: $responseCode")
                if (responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val match = Regex("data:'(.*?)'").find(response)
                    if (match == null) {
                        Log.e(TAG, "No data match in response: $response")
                        return null
                    }
                    val encodedData = match.groupValues[1]
                    val decodedData = URLDecoder.decode(encodedData, "UTF-8")
                    
                    val params = mutableMapOf<String, String>()
                    decodedData.split("&").forEach { pair ->
                        val parts = pair.split("=")
                        if (parts.size == 2) {
                            params[parts[0]] = parts[1]
                        }
                    }

                    val lastLogoutTime = params["lastlogouttime"]?.toLongOrNull() ?: 0L
                    val lastLogoutFormatted = if (lastLogoutTime > 0) {
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(lastLogoutTime * 1000))
                    } else "未知"

                    AccountInfo(
                        roleName = params["charac_name"] ?: "未知",
                        roleId = params["charac_no"] ?: "未知",
                        level = params["level"] ?: "0",
                        isOnline = if (params["is_online"] == "1") "在线" else "不在线",
                        isBan = if (params["isbanuser"] == "1") "已封号" else "正常",
                        isFace = if (params["schemeindex"] == "1") "是" else "否",
                        aceMark = params["historyhighestranktimes"] ?: "0",
                        heatValue = params["reli"] ?: "0",
                        rank = getRank(params["tppseasonduorating"] ?: "0"),
                        rankPoints = params["tppseasonduorating"] ?: "0",
                        lastLogout = lastLogoutFormatted,
                        totalRecharge = params["accumulatechargenum"] ?: "0",
                        todayLogin = params["logintoday"] ?: "0"
                    )
                } else {
                    Log.e(TAG, "Sync failed with code: $responseCode")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch account info: ${e.message}", e)
                null
            }
    }

    private fun saveAccountInfo(openid: String, info: AccountInfo) {
        val infoFile = File(getAccountDir(openid), "info.json")
        val json = JSONObject().apply {
            put("roleName", info.roleName)
            put("roleId", info.roleId)
            put("level", info.level)
            put("isOnline", info.isOnline)
            put("isBan", info.isBan)
            put("isFace", info.isFace)
            put("aceMark", info.aceMark)
            put("heatValue", info.heatValue)
            put("rank", info.rank)
            put("rankPoints", info.rankPoints)
            put("lastLogout", info.lastLogout)
            put("totalRecharge", info.totalRecharge)
            put("todayLogin", info.todayLogin)
        }
        infoFile.writeText(json.toString())
    }

    fun getAccountInfo(openid: String): AccountInfo? {
        val infoFile = File(getAccountDir(openid), "info.json")
        if (!infoFile.exists()) return null
        return try {
            val json = JSONObject(infoFile.readText())
            AccountInfo(
                roleName = json.optString("roleName", "未知"),
                roleId = json.optString("roleId", "未知"),
                level = json.optString("level", "0"),
                isOnline = json.optString("isOnline", "不在线"),
                isBan = json.optString("isBan", "正常"),
                isFace = json.optString("isFace", "否"),
                aceMark = json.optString("aceMark", "0"),
                heatValue = json.optString("heatValue", "0"),
                rank = json.optString("rank", "未知"),
                rankPoints = json.optString("rankPoints", "0"),
                lastLogout = json.optString("lastLogout", "未知"),
                totalRecharge = json.optString("totalRecharge", "0"),
                todayLogin = json.optString("todayLogin", "0")
            )
        } catch (e: Exception) {
            null
        }
    }

    fun saveCurrentAccount(): SaveResult {
        runDiagnosticsIfNeeded()
        
        val targetPath = pubgFilePath
        Log.d(TAG, "Attempting to save from: $targetPath")
        
        val data = ShellUtils.execRootGetBytes("cat \"$targetPath\"")
        
        if (data == null || data.isEmpty()) {
            Log.e(TAG, "Failed to read file via shell: $targetPath")
            return SaveResult(false, error = "读取登录文件失败")
        }

        val decrypted = MSDKTea.decrypt(data, teaKey)
        if (decrypted == null) {
            Log.e(TAG, "Decryption failed (MSDKTea.decrypt returned null)")
            return SaveResult(false, error = "解密失败")
        }
        
        val json = try {
            JSONObject(String(decrypted, Charsets.UTF_8))
        } catch (e: Exception) {
            Log.e(TAG, "JSON parse failed", e)
            return SaveResult(false, error = "解析登录数据失败")
        }

        val account = try {
            Account.fromJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "Account mapping failed", e)
            return SaveResult(false, error = "解析登录实体失败")
        }

        val openid = account.openid
        if (openid.isEmpty()) {
            Log.e(TAG, "openid not found in login data")
            return SaveResult(false, error = "未找到 OpenID")
        }

        val accessToken = account.channelInfo.accessToken
        var currentRoleName: String? = null
        if (accessToken.isNotEmpty()) {
            val info = fetchFullAccountInfo(openid, accessToken)
            if (info != null) {
                saveAccountInfo(openid, info)
                prefs.edit().putString(openid, info.roleName).apply()
                currentRoleName = info.roleName
                Log.d(TAG, "Saved account info for $openid: ${info.roleName}")
            }
        }

        val targetFile = File(getAccountDir(openid), "login.dat")
        try {
            targetFile.writeBytes(data)
            Log.d(TAG, "Successfully saved account for openid: $openid to ${targetFile.absolutePath}")
            return SaveResult(true, characName = currentRoleName ?: openid)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write account file", e)
            return SaveResult(false, error = "保存文件失败: ${e.message}")
        }
    }

    fun switchAccount(openid: String): Boolean {
        val accountFile = File(getAccountDir(openid), "login.dat")
        if (!accountFile.exists()) return false

        val targetPath = pubgFilePath
        val dataDir = getActualDataPath()
        val command = "cp ${accountFile.absolutePath} $targetPath && chown $(stat -c %u:%g $dataDir) $targetPath && chmod 600 $targetPath"
        return ShellUtils.execRoot(command)
    }

    fun listAccounts(): List<Pair<String, String>> {
        migrateIfNeeded()
        return accountsDir.listFiles { file -> file.isDirectory }?.map { dir ->
            val openid = dir.name
            val displayName = prefs.getString(openid, null) ?: openid
            openid to displayName
        } ?: emptyList()
    }

    private fun migrateIfNeeded() {
        accountsDir.listFiles { file -> file.isFile }?.forEach { file ->
            val openid = file.nameWithoutExtension
            val targetDir = getAccountDir(openid)
            when (file.extension) {
                "dat" -> file.renameTo(File(targetDir, "login.dat"))
                "info" -> file.renameTo(File(targetDir, "info.json"))
            }
        }
    }

    fun clearCurrentAccount(): Boolean {
        val command = "rm -f $pubgFilePath"
        return ShellUtils.execRoot(command)
    }

    fun restartApp(): Boolean {
        val command = "am force-stop $pubgPackage && monkey -p $pubgPackage -c android.intent.category.LAUNCHER 1"
        return ShellUtils.execRoot(command)
    }

    fun deleteAccount(openid: String): Boolean {
        val dir = getAccountDir(openid)
        return dir.deleteRecursively()
    }

    fun getDecryptedLoginData(openid: String): String? {
        return getAccountJsonObject(openid)?.toString(4)
    }

    fun getAccountJsonObject(openid: String): JSONObject? {
        val accountFile = File(getAccountDir(openid), "login.dat")
        if (!accountFile.exists()) return null
        val data = accountFile.readBytes()
        val decrypted = MSDKTea.decrypt(data, teaKey) ?: return null
        return try {
            JSONObject(String(decrypted, Charsets.UTF_8))
        } catch (e: Exception) {
            null
        }
    }
}

