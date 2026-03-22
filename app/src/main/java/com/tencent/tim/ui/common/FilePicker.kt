package com.tencent.tim.ui.common

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.io.File

enum class PickerMode {
    FILE, DIRECTORY
}

@Composable
fun FilePicker(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    mode: PickerMode = PickerMode.FILE,
    title: String = if (mode == PickerMode.FILE) "选择导入文件" else "选择导出目录",
    initialPath: String = Environment.getExternalStorageDirectory().absolutePath,
    suggestedFileName: String = ""
) {
    var currentPath by remember { mutableStateOf(initialPath) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var fileName by remember { mutableStateOf(suggestedFileName) }
    
    val files = remember(currentPath) {
        try {
            val root = File(currentPath)
            root.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val dialogHeight = if (isLandscape) 0.95f else 0.8f

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.9f else 0.95f)
                .fillMaxHeight(dialogHeight)
                .padding(if (isLandscape) 8.dp else 16.dp)
        ) {
            Column(modifier = Modifier.padding(if (isLandscape) 12.dp else 16.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(if (isLandscape) 4.dp else 8.dp))
                
                // 当前路径显示和返回上级
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            val parent = File(currentPath).parentFile
                            if (parent != null && parent.canRead()) {
                                currentPath = parent.absolutePath
                            }
                        },
                        enabled = currentPath != "/"
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回上级")
                    }
                    Text(
                        text = currentPath,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = if (isLandscape) 4.dp else 8.dp))

                // 文件列表
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(files) { file ->
                        FileItem(
                            file = file,
                            isSelected = if (mode == PickerMode.FILE) selectedFile == file else false,
                            onClick = {
                                if (file.isDirectory) {
                                    currentPath = file.absolutePath
                                    if (mode == PickerMode.DIRECTORY) {
                                        selectedFile = file
                                    }
                                } else if (mode == PickerMode.FILE) {
                                    selectedFile = file
                                }
                            }
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = if (isLandscape) 4.dp else 8.dp))

                // 导出模式下的文件名输入
                if (mode == PickerMode.DIRECTORY) {
                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { fileName = it },
                        label = { Text("文件名") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = if (isLandscape) 2.dp else 8.dp),
                        singleLine = true
                    )
                }

                // 底部按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (mode == PickerMode.FILE) {
                                selectedFile?.let { onConfirm(it.absolutePath) }
                            } else {
                                val finalPath = if (fileName.isEmpty()) {
                                    currentPath
                                } else {
                                    File(currentPath, fileName).absolutePath
                                }
                                onConfirm(finalPath)
                            }
                        },
                        enabled = if (mode == PickerMode.FILE) selectedFile != null else true
                    ) {
                        Text("确定")
                    }
                }
            }
        }
    }
}

@Composable
private fun FileItem(
    file: File,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = if (isSelected) 8.dp else 4.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
            contentDescription = null,
            tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else Color.Gray
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = file.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
