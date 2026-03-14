package com.tencent.tim

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tencent.tim.ui.main.AccountListScreen
import com.tencent.tim.ui.theme.AccountSwitcherTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        setContent {
            AccountSwitcherTheme {
                AccountListScreen()
            }
        }
    }
}