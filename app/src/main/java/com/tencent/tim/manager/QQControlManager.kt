package com.tencent.tim.manager

import android.util.Log
import com.tencent.tim.data.system.RootManager
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

interface QQControlManager {
    fun hideQQ(): Boolean
    fun unhideQQ(): Boolean
    val isShizukuMode: Boolean
}

/**
 * QQ 控制管理器，实现隐藏和恢复 QQ 功能
 */
class QQControlManagerImpl(
    private val modeManager: ModeManager,
    private val rootManager: RootManager
) : QQControlManager {

    companion object {
        private const val TAG = "QQControlManager"
        private const val QQ_PACKAGE = "com.tencent.mobileqq"
    }

    override val isShizukuMode: Boolean
        get() = modeManager.currentMode.value == OperationMode.SHIZUKU

    override fun hideQQ(): Boolean {
        return when (modeManager.currentMode.value) {
            OperationMode.ROOT -> rootManager.hideApp(QQ_PACKAGE)
            OperationMode.SHIZUKU -> {
                val apkPath = getAppPathShizuku(QQ_PACKAGE) ?: return false
                val backupPath = "/data/local/tmp/$QQ_PACKAGE.apk"
                // 1. 备份 APK (Shizuku 权限通常可以执行 cp)
                if (shizukuExec("cp $apkPath $backupPath") != 0) {
                    Log.e(TAG, "Shizuku: Failed to back up APK")
                    return false
                }
                // 2. 卸载并保留数据
                shizukuExec("pm uninstall -k --user 0 $QQ_PACKAGE") == 0
            }
            else -> false
        }
    }

    override fun unhideQQ(): Boolean {
        return when (modeManager.currentMode.value) {
            OperationMode.ROOT -> rootManager.unhideApp(QQ_PACKAGE)
            OperationMode.SHIZUKU -> {
                val backupPath = "/data/local/tmp/$QQ_PACKAGE.apk"
                // 安装备份的 APK
                shizukuExec("pm install -r --user 0 $backupPath") == 0
            }
            else -> false
        }
    }

    private fun getAppPathShizuku(packageName: String): String? {
        // 使用 Shizuku 运行 pm path
        return try {
            val newProcessMethod = rikka.shizuku.Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcessMethod.isAccessible = true
            val process = newProcessMethod.invoke(
                null,
                arrayOf("pm", "path", packageName),
                null,
                null
            ) as rikka.shizuku.ShizukuRemoteProcess
            
            val output = process.inputStream.bufferedReader().use { it.readText().trim() }
            process.waitFor()
            output.substringAfter("package:").trim().takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku getAppPath 异常", e)
            null
        }
    }

    private fun shizukuExec(command: String): Int {
        return try {
            val newProcessMethod = rikka.shizuku.Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcessMethod.isAccessible = true
            val process = newProcessMethod.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) as rikka.shizuku.ShizukuRemoteProcess
            
            val result = process.waitFor()
            if (result != 0) {
                val error = process.errorStream.bufferedReader().use { it.readText().trim() }
                Log.e(TAG, "Shizuku command failed ($result): $command, error: $error")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku exec 异常: $command", e)
            -1
        }
    }
}
