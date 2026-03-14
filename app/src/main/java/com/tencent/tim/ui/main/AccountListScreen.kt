package com.tencent.tim.ui.main

import android.widget.Toast
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tencent.tim.data.local.AccountEntity
import com.tencent.tim.ui.common.AccountItem
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

enum class DragValue { Start, End }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountListScreen(
    viewModel: MainViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showDetailsAccount by remember { mutableStateOf<AccountEntity?>(null) }

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is MainEffect.ShowToast -> Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                is MainEffect.ShowAccountDetails -> showDetailsAccount = effect.account
            }
        }
    }

    if (showDetailsAccount != null) {
        AccountDetailsDialog(
            account = showDetailsAccount!!,
            onDismiss = { showDetailsAccount = null }
        )
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
                    IconButton(onClick = { viewModel.handleIntent(MainIntent.RefreshAccountsOnlineInfo) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "批量刷新在线信息")
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
                SwipeToRevealDelete(
                    onDelete = { viewModel.handleIntent(MainIntent.DeleteAccount(account.openid)) }
                ) {
                    AccountItem(
                        account = account,
                        onSelected = { viewModel.handleIntent(MainIntent.SetSelectedAccount(account.openid)) },
                        onPlay = { viewModel.handleIntent(MainIntent.SwitchAndPlay(account.openid)) },
                        onShowDetails = { viewModel.handleIntent(MainIntent.ShowAccountDetails(account)) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeToRevealDelete(
    onDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val deleteBtnWidth = 80.dp
    val deleteBtnWidthPx = with(density) { deleteBtnWidth.toPx() }

    val state = remember {
        AnchoredDraggableState(
            initialValue = DragValue.Start,
            anchors = DraggableAnchors {
                DragValue.Start at 0f
                DragValue.End at -deleteBtnWidthPx
            },
            positionalThreshold = { distance: Float -> distance * 0.5f },
            velocityThreshold = { with(density) { 100.dp.toPx() } },
            snapAnimationSpec = tween(),
            decayAnimationSpec = exponentialDecay(),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // 后方的删除按钮
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(deleteBtnWidth)
                .background(MaterialTheme.colorScheme.error)
                .clickable { onDelete() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "删除",
                tint = Color.White
            )
        }

        // 前层的内容
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset {
                    IntOffset(
                        x = state.offset.roundToInt(),
                        y = 0
                    )
                }
                .anchoredDraggable(state, Orientation.Horizontal)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            content()
        }
    }
}

@Composable
fun AccountDetailsDialog(
    account: AccountEntity,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "账号详情: ${account.roleName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DetailRow("Role ID", account.roleId)
                DetailRow("Level", account.level)
                DetailRow("Rank", account.rank)
                DetailRow("OpenID", account.openid)
                DetailRow("Online", account.isOnline)
                DetailRow("Ban Status", account.isBan)
                DetailRow("Heat Value", account.heatValue)
                DetailRow("Last Update", account.lastLogout)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("确定")
            }
        }
    )
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(text = "$label: ", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(2f))
    }
}
