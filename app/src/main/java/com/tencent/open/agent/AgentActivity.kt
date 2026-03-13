package com.tencent.open.agent

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import org.json.JSONObject

/**
 * Mocking QQ SDK's AgentActivity to handle login requests.
 */
class AgentActivity : Activity() {

    companion object {
        private const val TAG = "AgentActivity"
        private var instance: AgentActivity? = null

        fun sendResponse(jsonResponse: String) {
            instance?.let { activity ->
                val intent = Intent()
                intent.putExtra("key_response", jsonResponse)
                activity.setResult(RESULT_OK, intent)
                activity.finish()
            }
        }
        
        fun isActive(): Boolean = instance != null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        
        // 打印请求参数
        Log.d(TAG, "AgentActivity started with intent: ${intent?.extras?.let { extras -> 
            extras.keySet().joinToString("; ") { key -> "$key=${extras.get(key)}" }
        } ?: "No extras"}")

        // Note: The UI for choosing the account will be handled by the FloatingService menu.
        // This activity just stays in the background (or foreground with no UI) until 
        // the user picks an account from the floating menu.
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }
}
