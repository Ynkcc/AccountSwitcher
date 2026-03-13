package net.game.switcher.manager

import android.content.Context
import android.util.Log
import net.game.switcher.utils.MSDKTea
import net.game.switcher.utils.ShellUtils
import org.json.JSONObject
import java.io.File
import java.nio.charset.Charset

class AccountManager(private val context: Context) {
    private val pubgPackage = "com.tencent.tmgp.pubgmhd"
    private var actualDataPath: String? = null

    private fun getActualDataPath(): String {
        actualDataPath?.let { return it }
        val paths = listOf("/data/user/0/$pubgPackage", "/data/data/$pubgPackage")
        for (path in paths) {
            val output = ShellUtils.execRootGetOutput("ls -d $path")
            if (output != null && output.contains(pubgPackage)) {
                actualDataPath = path
                Log.d("AccountManager", "Detected data path: $path")
                return path
            }
        }
        Log.e("AccountManager", "Failed to detect data path, defaulting to /data/user/0/$pubgPackage")
        return "/data/user/0/$pubgPackage"
    }

    private val pubgFilePath get() = "${getActualDataPath()}/files/itop_login.txt"
    private val accountsDir = File(context.filesProviderDir(), "accounts").apply { if (!exists()) mkdirs() }
    private val teaKey = "ITOPITOPITOPITOP".toByteArray()

    private fun Context.filesProviderDir(): File = filesDir

    fun saveCurrentAccount(): Boolean {
        // Diagnostic logs to Log.e so they appear even in filtered logs
        val diag = listOf(
            "id",
            "getenforce",
            "ls -ld /data/user/0",
            "ls -ld /data/data",
            "mount | grep -E '/data | / '"
        )
        diag.forEach { cmd ->
            val out = ShellUtils.execRootGetOutput(cmd)
            Log.e("AccountManager", "[Diag] $cmd -> $out")
        }
        
        val targetPath = pubgFilePath
        Log.d("AccountManager", "Attempting to save from: $targetPath")
        
        ShellUtils.execRootGetOutput("ls -l \"$targetPath\"")
        val data = ShellUtils.execRootGetBytes("cat \"$targetPath\"")
        
        if (data == null || data.isEmpty()) {
            Log.e("AccountManager", "Failed to read file via shell: $targetPath")
            return false
        }

        val decrypted = MSDKTea.decrypt(data, teaKey)
        if (decrypted == null) {
            Log.e("AccountManager", "Decryption failed (MSDKTea.decrypt returned null)")
            return false
        }
        
        val json = try {
            JSONObject(String(decrypted, Charset.forName("UTF-8")))
        } catch (e: Exception) {
            Log.e("AccountManager", "JSON parse failed", e)
            return false
        }

        val openid = json.optString("openid", "")
        if (openid.isEmpty()) {
            Log.e("AccountManager", "openid not found in login data: ${json}")
            return false
        }

        val targetFile = File(accountsDir, "$openid.dat")
        try {
            targetFile.writeBytes(data)
            Log.d("AccountManager", "Successfully saved account for openid: $openid to ${targetFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("AccountManager", "Failed to write account file", e)
            return false
        }
        return true
    }

    fun switchAccount(openid: String): Boolean {
        val accountFile = File(accountsDir, "$openid.dat")
        if (!accountFile.exists()) return false

        val targetPath = pubgFilePath
        val dataDir = getActualDataPath()
        val command = "cp ${accountFile.absolutePath} $targetPath && chown $(stat -c %u:%g $dataDir) $targetPath && chmod 600 $targetPath"
        return ShellUtils.execRoot(command)
    }

    fun listAccounts(): List<Pair<String, String>> {
        return accountsDir.listFiles()?.map { file ->
            val openid = file.nameWithoutExtension
            val displayName = try {
                val data = file.readBytes()
                val decrypted = MSDKTea.decrypt(data, teaKey)
                if (decrypted != null) {
                    val json = JSONObject(String(decrypted, Charset.forName("UTF-8")))
                    json.optString("user_name", openid)
                } else openid
            } catch (e: Exception) {
                openid
            }
            openid to displayName
        } ?: emptyList()
    }

    fun clearCurrentAccount(): Boolean {
        val command = "rm -f $pubgFilePath"
        return ShellUtils.execRoot(command)
    }

    fun deleteAccount(openid: String): Boolean {
        return File(accountsDir, "$openid.dat").delete()
    }
}
