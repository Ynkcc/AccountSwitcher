package com.tencent.tim.manager

import android.content.Context
import android.util.Log
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.Region
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ListObjectsV2Request
import com.amazonaws.services.s3.model.ObjectMetadata
import com.tencent.tim.utils.MSDKTea
import org.json.JSONObject
import java.io.File

class S3SyncManager(private val context: Context, private val accountManager: AccountManager) {
    companion object {
        private const val TAG = "S3SyncManager"
        private const val PREFS_NAME = "s3_sync_config"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val teaKey = "ITOPITOPITOPITOP".toByteArray()

    data class S3Config(
        val endpoint: String,
        val bucket: String,
        val accessKey: String,
        val secretKey: String,
        val region: String
    )

    fun saveConfig(config: S3Config) {
        prefs.edit().apply {
            putString("endpoint", config.endpoint)
            putString("bucket", config.bucket)
            putString("accessKey", config.accessKey)
            putString("secretKey", config.secretKey)
            putString("region", config.region)
        }.apply()
    }

    fun getConfig(): S3Config? {
        val endpoint = prefs.getString("endpoint", "") ?: ""
        val bucket = prefs.getString("bucket", "") ?: ""
        val accessKey = prefs.getString("accessKey", "") ?: ""
        val secretKey = prefs.getString("secretKey", "") ?: ""
        val region = prefs.getString("region", "us-east-1") ?: "us-east-1"

        if (endpoint.isEmpty() || bucket.isEmpty() || accessKey.isEmpty() || secretKey.isEmpty()) return null
        return S3Config(endpoint, bucket, accessKey, secretKey, region)
    }

    private fun getS3Client(config: S3Config): AmazonS3Client {
        val credentials = BasicAWSCredentials(config.accessKey, config.secretKey)
        val s3 = AmazonS3Client(credentials)
        if (config.endpoint.isNotEmpty()) {
            s3.setEndpoint(config.endpoint)
        }
        s3.setRegion(Region.getRegion(config.region))
        return s3
    }

    fun uploadAccount(openid: String) {
        val config = getConfig() ?: return
        val s3 = getS3Client(config)
        val accountDir = File(context.filesDir, "accounts/$openid")
        if (!accountDir.exists()) return

        listOf("login.dat", "info.json").forEach { filename ->
            val file = File(accountDir, filename)
            if (file.exists()) {
                s3.putObject(config.bucket, "$openid/$filename", file)
            }
        }
    }

    fun downloadAccount(openid: String) {
        val config = getConfig() ?: return
        val s3 = getS3Client(config)
        val accountDir = File(context.filesDir, "accounts/$openid").apply { if (!exists()) mkdirs() }

        listOf("login.dat", "info.json").forEach { filename ->
            val file = File(accountDir, filename)
            val s3Object = s3.getObject(config.bucket, "$openid/$filename")
            s3Object.objectContent.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    interface SyncCallback {
        fun onConflict(openid: String, localToken: String, cloudToken: String, onChoice: (useCloud: Boolean) -> Unit)
        fun onProgress(message: String)
        fun onFinished(success: Boolean, message: String)
    }

    fun syncAll(callback: SyncCallback) {
        val config = getConfig() ?: return callback.onFinished(false, "S3 配置不完整")
        
        kotlin.concurrent.thread {
            try {
                val s3 = getS3Client(config)
                val localAccounts = accountManager.listAccounts().map { it.first }.toSet()
                
                val cloudAccounts = mutableSetOf<String>()
                val request = ListObjectsV2Request().withBucketName(config.bucket).withDelimiter("/")
                var result = s3.listObjectsV2(request)
                
                result.commonPrefixes.forEach { cloudAccounts.add(it.removeSuffix("/")) }
                
                // 1. Only local: upload
                localAccounts.filter { it !in cloudAccounts }.forEach { openid ->
                    callback.onProgress("上传新账号: $openid")
                    uploadAccount(openid)
                }
                
                // 2. Only cloud: download
                cloudAccounts.filter { it !in localAccounts }.forEach { openid ->
                    callback.onProgress("下载新账号: $openid")
                    downloadAccount(openid)
                }
                
                // 3. Both: compare access_token
                localAccounts.intersect(cloudAccounts).forEach { openid ->
                    val localJson = accountManager.getAccountJsonObject(openid)
                    val localToken = localJson?.optJSONObject("channel_info")?.optString("access_token") ?: ""
                    
                    // Fetch cloud token
                    val s3Object = s3.getObject(config.bucket, "$openid/login.dat")
                    val cloudData = s3Object.objectContent.use { it.readBytes() }
                    val decrypted = MSDKTea.decrypt(cloudData, teaKey)
                    val cloudJson = if (decrypted != null) JSONObject(String(decrypted, Charsets.UTF_8)) else null
                    val cloudToken = cloudJson?.optJSONObject("channel_info")?.optString("access_token") ?: ""
                    
                    if (localToken != cloudToken) {
                        val waitLock = java.lang.Object()
                        var choice: Boolean? = null
                        
                        callback.onConflict(openid, localToken, cloudToken) { useCloud ->
                            choice = useCloud
                            synchronized(waitLock) { waitLock.notify() }
                        }
                        
                        synchronized(waitLock) {
                            while (choice == null) {
                                waitLock.wait()
                            }
                        }
                        
                        if (choice == true) {
                            callback.onProgress("下载已选版本: $openid")
                            downloadAccount(openid)
                        } else {
                            callback.onProgress("上传已选版本: $openid")
                            uploadAccount(openid)
                        }
                    }
                }
                
                callback.onFinished(true, "同步完成")
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed", e)
                callback.onFinished(false, "同步失败: ${e.message}")
            }
        }
    }
}
