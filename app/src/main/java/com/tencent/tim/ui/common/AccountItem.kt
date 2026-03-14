package com.tencent.tim.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tencent.tim.data.local.AccountEntity

@Composable
fun AccountItem(
    account: AccountEntity,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onSelect
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = account.roleName, style = MaterialTheme.typography.titleMedium)
                Text(text = "lv.${account.level} - ${account.rank}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Text(text = "最后更新: ${account.lastLogout}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun AccountListItemSimple(
    account: AccountEntity,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(account.roleName, style = MaterialTheme.typography.bodyMedium)
            Text("lv.${account.level} - ${account.rank}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}
