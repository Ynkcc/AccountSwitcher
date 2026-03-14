package com.tencent.tim.service

import android.app.*
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tencent.tim.R
import com.tencent.tim.manager.AccountManager
import com.tencent.open.agent.AgentActivity
import org.json.JSONObject

class FloatingService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var accountManager: AccountManager
    private var isExpanded = false

    override fun onCreate() {
        super.onCreate()
        accountManager = AccountManager(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        createNotificationChannel()
        startForeground(1, createNotification())
        
        initFloatingView()
    }

    private fun initFloatingView() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating, null)
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val btnIcon = Button(this).apply {
            text = "G"
            setBackgroundColor(Color.parseColor("#80000000"))
            setTextColor(Color.WHITE)
            setOnClickListener { toggleView() }
        }

        // Simplest UI for now: a toggleable layout
        windowManager.addView(btnIcon, params)
        
        btnIcon.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(btnIcon, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val diffX = event.rawX - initialTouchX
                        val diffY = event.rawY - initialTouchY
                        if (Math.abs(diffX) < 10 && Math.abs(diffY) < 10) {
                            v.performClick()
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun toggleView() {
        showMenuDialog()
    }

    private fun showMenuDialog() {
        val items = arrayOf("保存当前账号", "切换账号", "清除当前登录", "选择响应帐号")
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("GSwitcher")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> saveCurrent()
                    1 -> showAccountList()
                    2 -> clearCurrent()
                    3 -> showAccountListForResponse()
                }
            }
            .create().apply {
                window?.setType(
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                )
                show()
            }
    }

    private fun saveCurrent() {
        kotlin.concurrent.thread {
            val result = accountManager.saveCurrentAccount()
            floatingView.post {
                if (result.success) {
                    Toast.makeText(this@FloatingService, "保存成功: ${result.characName}", Toast.LENGTH_SHORT).show()
                } else {
                    Log.e("FloatingService", "Save failed: ${result.error}")
                    val msg = if (result.error != null) "保存失败: ${result.error}" else "保存失败，未识别到登录信息"
                    Toast.makeText(this@FloatingService, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showAccountList() {
        showAccountListInternal(false)
    }

    private fun showAccountListForResponse() {
        if (!AgentActivity.isActive()) {
            Toast.makeText(this, "当前无活跃的登录请求", Toast.LENGTH_SHORT).show()
            return
        }
        showAccountListInternal(true)
    }

    private fun showAccountListInternal(isForResponse: Boolean) {
        val accounts = accountManager.listAccounts()
        if (accounts.isEmpty()) {
            Toast.makeText(this, "没有保存的账号", Toast.LENGTH_SHORT).show()
            return
        }

        val names = accounts.map { it.second }.toTypedArray()
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(if (isForResponse) "选择响应账号" else "选择账号")
            .setItems(names) { _, which ->
                val openid = accounts[which].first
                val displayName = accounts[which].second
                
                if (isForResponse) {
                    handleResponse(openid)
                } else {
                    if (accountManager.switchAccount(openid)) {
                        Toast.makeText(this, "已切换到 $displayName，请重启游戏", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "切换失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNeutralButton("管理") { _, _ -> showManageAccounts() }
            .create().apply {
                window?.setType(
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                )
                show()
            }
    }

    private fun handleResponse(openid: String) {
        val accountJson = accountManager.getAccountJsonObject(openid)
        if (accountJson == null) {
            Toast.makeText(this, "无法读取账号数据", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val channelInfo = accountJson.getJSONObject("channel_info")
            
            val responseJson = JSONObject().apply {
                put("access_token", channelInfo.optString("access_token"))
                put("openid", accountJson.optString("openid", "default_openid"))
                put("pay_token", channelInfo.optString("pay_token", "default_pay_token"))
                put("expires_in", channelInfo.optString("expired", "7776000"))
                put("ret", 0) // 必须为 0，表示成功
                put("pf", accountJson.optString("pf"))
                put("page_type", "1")
            }
            
            val responseStr = responseJson.toString()
            Log.d("FloatingService", "Response JSON: $responseStr")
            AgentActivity.sendResponse(responseStr)
            Toast.makeText(this, "响应成功", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("FloatingService", "Build response failed", e)
            Toast.makeText(this, "构建响应数据失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showManageAccounts() {
        val accounts = accountManager.listAccounts()
        val names = accounts.map { it.second }.toTypedArray()
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("删除账号")
            .setItems(names) { _, which ->
                val openid = accounts[which].first
                val displayName = accounts[which].second
                accountManager.deleteAccount(openid)
                Toast.makeText(this, "已删除 $displayName", Toast.LENGTH_SHORT).show()
            }
            .create().apply {
                window?.setType(
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                )
                show()
            }
    }

    private fun clearCurrent() {
        if (accountManager.clearCurrentAccount()) {
            Toast.makeText(this, "已清除登录，请重启游戏", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "清除失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "gswitcher_service",
                "GSwitcher Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "gswitcher_service")
            .setContentTitle("GSwitcher 运行中")
            .setContentText("点击进入应用")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        // Should remove the btnIcon but keeping it simple for now as per rules
    }
}
