package com.tencent.tim.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tencent.tim.ui.model.AccountUiModel

@Composable
fun AccountItem(
    account: AccountUiModel,
    onSelected: () -> Unit,
    onPlay: () -> Unit,
    onShowDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onShowDetails
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = account.isSelected,
                onClick = onSelected
            )
            
            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(text = account.roleName, style = MaterialTheme.typography.titleMedium)
                Text(text = "lv.${account.level} - ${account.rank}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Text(text = "下线时间: ${account.lastLogoutText}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            
            IconButton(onClick = onPlay) {
                Icon(
                    Icons.Default.PlayArrow, 
                    contentDescription = "切换并进入游戏", 
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
