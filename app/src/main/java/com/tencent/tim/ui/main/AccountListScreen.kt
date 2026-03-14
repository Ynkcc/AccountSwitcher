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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tencent.tim.manager.OperationMode
import com.tencent.tim.ui.common.AccountItem
import com.tencent.tim.ui.model.AccountUiModel
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
    var showDetailsAccount by remember { mutableStateOf<AccountUiModel?>(null) }
    var showManualImportDialog by remember { mutableStateOf(false) }

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

    if (showManualImportDialog) {
        ManualImportDialog(
            onDismiss = { showManualImportDialog = false },
            onConfirm = { accessToken, openid, payToken ->
                viewModel.handleIntent(
                    MainIntent.ManualImportAccount(
                        accessToken = accessToken,
                        openid = openid,
                        payToken = payToken
                    )
                )
                showManualImportDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GSwitcher") },
                actions = {
                    val isShizukuMode = state.operationMode == OperationMode.SHIZUKU
                    
                    IconButton(
                        onClick = { viewModel.handleIntent(MainIntent.SaveCurrentAccount) },
                        enabled = !isShizukuMode
                    ) {
                        Icon(
                            Icons.Default.Save, 
                            contentDescription = "保存当前账号",
                            tint = if (isShizukuMode) Color.Gray else LocalContentColor.current
                        )
                    }
                    IconButton(
                        onClick = { viewModel.handleIntent(MainIntent.ClearCurrentAccount) },
                        enabled = !isShizukuMode
                    ) {
                        Icon(
                            Icons.Default.DeleteSweep, 
                            contentDescription = "清除当前登录",
                            tint = if (isShizukuMode) Color.Gray else LocalContentColor.current
                        )
                    }
                    IconButton(onClick = { viewModel.handleIntent(MainIntent.RefreshAccountsOnlineInfo) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "批量刷新在线信息")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 模式指示器区域
            ModeIndicatorSection(
                operationMode = state.operationMode,
                isRootAvailable = state.isRootAvailable,
                isShizukuAvailable = state.isShizukuAvailable,
                onRequestShizuku = { viewModel.handleIntent(MainIntent.RequestShizukuPermission) },
                onCheckModes = { viewModel.handleIntent(MainIntent.CheckModes) }
            )

            // 工具占位区域
            ToolPlaceholderSection(
                onHideQQ = { viewModel.handleIntent(MainIntent.HideQQ) },
                onRestoreQQ = { viewModel.handleIntent(MainIntent.RestoreQQ) },
                onManualImport = { showManualImportDialog = true }
            )

            if (state.isLoading) {
                Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            
            if (state.operationMode == OperationMode.SHIZUKU) {
                Surface(
                    color = MaterialTheme.colorScheme.infoContainer.copy(alpha = 0.3f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Shizuku 模式下仅支持 Intent 登录（勾选账号后直接打开游戏）",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
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
                            onPlay = { 
                                if (state.operationMode == OperationMode.SHIZUKU) {
                                    viewModel.handleIntent(MainIntent.RestartApp)
                                } else {
                                    viewModel.handleIntent(MainIntent.SwitchAndPlay(account.openid)) 
                                }
                            },
                            onShowDetails = { viewModel.handleIntent(MainIntent.ShowAccountDetails(account)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModeIndicatorSection(
    operationMode: OperationMode,
    isRootAvailable: Boolean,
    isShizukuAvailable: Boolean,
    onRequestShizuku: () -> Unit,
    onCheckModes: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ModeChip(
            label = "Root",
            isActive = operationMode == OperationMode.ROOT,
            isAvailable = isRootAvailable,
            onClick = onCheckModes
        )
        ModeChip(
            label = "Shizuku",
            isActive = operationMode == OperationMode.SHIZUKU,
            isAvailable = isShizukuAvailable,
            onClick = onRequestShizuku
        )
    }
}

@Composable
fun ModeChip(
    label: String,
    isActive: Boolean,
    isAvailable: Boolean,
    onClick: () -> Unit
) {
    val color = when {
        isActive -> MaterialTheme.colorScheme.primary
        isAvailable -> MaterialTheme.colorScheme.secondary
        else -> Color.Gray
    }
    
    Surface(
        onClick = onClick,
        color = color.copy(alpha = if (isActive) 1f else 0.1f),
        shape = MaterialTheme.shapes.extraLarge,
        border = if (isActive) null else androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, androidx.compose.foundation.shape.CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isActive) Color.White else color,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun ToolPlaceholderSection(
    onHideQQ: () -> Unit,
    onRestoreQQ: () -> Unit,
    onManualImport: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        onClick = onHideQQ,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.VisibilityOff, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("隐藏QQ")
                    }

                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        onClick = onRestoreQQ,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("恢复QQ")
                    }
                }

                OutlinedButton(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = onManualImport
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("手动导入")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "QQ 管理工具",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun ManualImportDialog(
    onDismiss: () -> Unit,
    onConfirm: (accessToken: String, openid: String, payToken: String) -> Unit
) {
    var accessToken by remember { mutableStateOf("") }
    var openid by remember { mutableStateOf("") }
    var payToken by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动导入 QQ 账号") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = accessToken,
                    onValueChange = { accessToken = it.trim() },
                    label = { Text("access_token（必填）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = openid,
                    onValueChange = { openid = it.trim() },
                    label = { Text("openid（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = payToken,
                    onValueChange = { payToken = it.trim() },
                    label = { Text("pay_token（可选，默认留空）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "仅填写 access_token 也可导入，系统会自动校验并获取对应 openid。",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = accessToken.isNotBlank(),
                onClick = { onConfirm(accessToken, openid, payToken) }
            ) {
                Text("导入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

// 补充 infoContainer 颜色定义 (如果主题中没有)
val ColorScheme.infoContainer: Color
    @Composable
    get() = Color(0xFFE1F5FE)

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
    account: AccountUiModel,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "账号详情: ${account.roleName}") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(scrollState)
            ) {
                Text(
                    text = "${account.roleName} | ${account.openid}",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = "以下为本地存储的紧凑原始字段（key=value）",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                SelectionContainer {
                    Text(
                        text = account.rawCompactData,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("确定")
            }
        }
    )
}
