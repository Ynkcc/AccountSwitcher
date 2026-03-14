package com.tencent.open.agent

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.tencent.tim.domain.AccountInteractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Mocking QQ SDK's AgentActivity to handle login requests.
 */
class AgentActivity : Activity() {

    private val interactor: AccountInteractor by inject()
    private val scope = MainScope()

    companion object {
        private const val TAG = "AgentActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "AgentActivity started with intent")

        // 自动响应选中账号的登录请求
        scope.launch {
            val selectedAccount = interactor.getSelectedAccount()
            if (selectedAccount != null) {
                interactor.buildAgentResponse(selectedAccount.openid)
                    .onSuccess { jsonResponse ->
                        val intent = Intent()
                        intent.putExtra("key_response", jsonResponse)
                        setResult(RESULT_OK, intent)
                        finish()
                    }
                    .onFailure { error ->
                        Toast.makeText(this@AgentActivity, "构建登录响应失败: ${error.message}", Toast.LENGTH_SHORT).show()
                        finish()
                    }
            } else {
                Toast.makeText(this@AgentActivity, "未选择登录账号，请在主页面先勾选一个", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
