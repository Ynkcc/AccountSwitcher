package com.tencent.tim.manager

import android.content.Context
import android.content.pm.PackageManager
import com.tencent.tim.data.system.RootManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

enum class OperationMode {
    NONE, ROOT, SHIZUKU
}

/**
 * 管理应用运行模式（Root 或 Shizuku）
 */
class ModeManager(
    private val context: Context,
    private val rootManager: RootManager
) {
    private val _currentMode = MutableStateFlow(OperationMode.NONE)
    val currentMode: StateFlow<OperationMode> = _currentMode.asStateFlow()

    /**
     * 检查可用模式，优先选择 Shizuku
     */
    fun checkAvailability() {
        when {
            isShizukuAvailable() -> _currentMode.value = OperationMode.SHIZUKU
            isRootAvailable() -> _currentMode.value = OperationMode.ROOT
            else -> _currentMode.value = OperationMode.NONE
        }
    }

    fun isRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val result = process.waitFor()
            result == 0
        } catch (e: Exception) {
            false
        }
    }

    fun isShizukuAvailable(): Boolean {
        return try {
            if (Shizuku.pingBinder()) {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun isShizukuAlive(): Boolean {
        return Shizuku.pingBinder()
    }

    /**
     * 请求 Shizuku 授权
     */
    fun requestShizukuPermission() {
        if (Shizuku.pingBinder()) {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(0)
            }
        }
    }
}
