package com.tencent.tim.ui.floating

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tencent.tim.ui.common.AccountListItemSimple
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun FloatingWindowUI(
    viewModel: FloatingViewModel = koinViewModel(),
    onMove: (Float, Float) -> Unit = { _, _ -> }
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is FloatingEffect.ShowToast -> Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(
        modifier = Modifier
            .padding(8.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onMove(dragAmount.x, dragAmount.y)
                }
            }
    ) {
        if (!state.isExpanded) {
            // Collapsed: Floating Bubble
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                    .clickable { viewModel.handleIntent(FloatingIntent.ToggleExpand) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.SwitchAccount, contentDescription = "Expand", tint = Color.White)
            }
        } else {
            // Expanded: List view
            Card(
                modifier = Modifier
                    .width(280.dp)
                    .heightIn(max = 400.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (state.isResponseMode) "选择响应账号" else "快速切换",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row {
                            IconButton(onClick = { viewModel.handleIntent(FloatingIntent.SaveCurrentAccount) }) {
                                Icon(Icons.Default.Save, contentDescription = "Save")
                            }
                            IconButton(onClick = { viewModel.handleIntent(FloatingIntent.ToggleExpand) }) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }
                    }

                    HorizontalDivider()

                    // Account List
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                    ) {
                        items(state.accounts, key = { it.openid }) { account ->
                            AccountListItemSimple(
                                account = account,
                                onSelect = {
                                    if (state.isResponseMode) {
                                        viewModel.handleIntent(FloatingIntent.RespondWithAccount(account.openid))
                                    } else {
                                        viewModel.handleIntent(FloatingIntent.SwitchAccount(account.openid))
                                    }
                                }
                            )
                        }
                    }

                    HorizontalDivider()

                    // Footer
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Restart App button is important for quick switching
                        TextButton(onClick = { viewModel.handleIntent(FloatingIntent.ClearCurrentAccount) }) {
                            Text("清除现状", fontSize = 12.sp)
                        }
                        TextButton(onClick = { viewModel.handleIntent(FloatingIntent.RestartApp) }) {
                            Text("重启应用", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
