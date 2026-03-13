package net.game.switcher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import net.game.switcher.manager.AccountManager
import net.game.switcher.manager.AccountInfo
import net.game.switcher.service.FloatingService
import net.game.switcher.utils.ShellUtils
import net.game.switcher.ui.theme.GSwitcherTheme

class MainActivity : ComponentActivity() {
    private lateinit var accountManager: AccountManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accountManager = AccountManager(this)
        
        if (!ShellUtils.checkRoot()) {
            Toast.makeText(this, "需要 Root 权限", Toast.LENGTH_LONG).show()
        }

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, 1001)
        } else {
            startService(Intent(this, FloatingService::class.java))
        }

        enableEdgeToEdge()
        setContent {
            GSwitcherTheme {
                AccountListScreen(accountManager)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001) {
            if (Settings.canDrawOverlays(this)) {
                startService(Intent(this, FloatingService::class.java))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountListScreen(accountManager: AccountManager) {
    val accounts = remember { accountManager.listAccounts() }
    var showDecrypted by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("GSwitcher 账号管理") })
        }
    ) { innerPadding ->
        if (accounts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("暂无保存账号")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                items(accounts) { (openid, displayName) ->
                    AccountItem(
                        openid = openid,
                        displayName = displayName,
                        info = accountManager.getAccountInfo(openid),
                        onViewDecrypted = { showDecrypted = openid }
                    )
                }
            }
        }
    }

    if (showDecrypted != null) {
        val data = accountManager.getDecryptedLoginData(showDecrypted!!) ?: "无法解析数据"
        Dialog(onDismissRequest = { showDecrypted = null }) {
            Surface(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("itop_login.txt 解密数据", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        Text(data, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = { showDecrypted = null },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("关闭")
                    }
                }
            }
        }
    }
}

@Composable
fun AccountItem(openid: String, displayName: String, info: AccountInfo?, onViewDecrypted: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp).clickable { expanded = !expanded },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = displayName, style = MaterialTheme.typography.titleLarge)
            Text(text = "OpenID: $openid", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            
            if (expanded && info != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))
                
                InfoRow("角色编号", info.roleId)
                InfoRow("账号等级", info.level)
                InfoRow("段位信息", info.rank)
                InfoRow("段位积分", info.rankPoints)
                InfoRow("当前状态", info.isOnline)
                InfoRow("封号状态", info.isBan)
                InfoRow("人脸验证", info.isFace)
                InfoRow("王牌印记", info.aceMark)
                InfoRow("热力值", info.heatValue)
                InfoRow("充值总额", info.totalRecharge)
                InfoRow("今日登录", "${info.todayLogin} 次")
                InfoRow("下线时间", info.lastLogout)
                
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onViewDecrypted,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("查看 itop_login.txt 解密数据")
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}