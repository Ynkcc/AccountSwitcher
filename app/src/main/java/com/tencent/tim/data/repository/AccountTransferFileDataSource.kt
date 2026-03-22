package com.tencent.tim.data.repository

import android.content.Context
import com.tencent.tim.data.local.AccountEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class AccountTransferPayload(
    val schemaVersion: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val accounts: List<AccountEntity> = emptyList()
)

data class AccountImportSummary(
    val insertedCount: Int,
    val updatedCount: Int,
    val skippedCount: Int,
    val invalidCount: Int
)

class AccountTransferFileDataSource(
    private val context: Context
) {
    companion object {
        private val jsonParser = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
        }
    }

    suspend fun exportToFile(
        filePath: String,
        payload: AccountTransferPayload
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(filePath)
            // 确保父目录存在
            file.parentFile?.mkdirs()
            
            val content = jsonParser.encodeToString(payload)
            file.writeText(content, Charsets.UTF_8)
        }
    }

    suspend fun importFromFile(filePath: String): Result<AccountTransferPayload> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(filePath)
            require(file.exists()) { "文件不存在: $filePath" }
            
            val content = file.readText(Charsets.UTF_8)
            parsePayload(content)
        }
    }

    private fun parsePayload(rawContent: String): AccountTransferPayload {
        val content = rawContent.removePrefix("\uFEFF").trim()
        require(content.isNotBlank()) { "导入文件为空" }

        return runCatching {
            jsonParser.decodeFromString<AccountTransferPayload>(content)
        }.getOrElse {
            val accounts = jsonParser.decodeFromString<List<AccountEntity>>(content)
            AccountTransferPayload(accounts = accounts)
        }
    }
}