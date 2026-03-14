package com.tencent.tim.ui.main

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tencent.tim.ui.common.AccountItem
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountListScreen(
    viewModel: MainViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is MainEffect.ShowToast -> Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GSwitcher") },
                actions = {
                    IconButton(onClick = { viewModel.handleIntent(MainIntent.SaveCurrentAccount) }) {
                        Icon(Icons.Default.Save, contentDescription = "保存当前账号")
                    }
                    IconButton(onClick = { viewModel.handleIntent(MainIntent.ClearCurrentAccount) }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "清除当前登录")
                    }
                    IconButton(onClick = { viewModel.handleIntent(MainIntent.RestartApp) }) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "重启游戏")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.accounts, key = { it.openid }) { account ->
                AccountItem(
                    account = account,
                    onSelect = { viewModel.handleIntent(MainIntent.SwitchAccount(account.openid)) },
                    onDelete = { viewModel.handleIntent(MainIntent.DeleteAccount(account.openid)) }
                )
            }
        }
    }
}
