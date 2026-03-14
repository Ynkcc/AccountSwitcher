package com.tencent.tim.data.repository

import android.content.Context
import android.net.Uri
import com.tencent.tim.data.local.AccountEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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

    suspend fun exportToUri(
        uriString: String,
        payload: AccountTransferPayload
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val uri = parseUri(uriString)
            val content = jsonParser.encodeToString(payload)
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(content.toByteArray(Charsets.UTF_8))
                outputStream.flush()
            } ?: error("无法打开导出文件")
        }
    }

    suspend fun importFromUri(uriString: String): Result<AccountTransferPayload> = withContext(Dispatchers.IO) {
        runCatching {
            val uri = parseUri(uriString)
            val content = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                it.readText()
            } ?: error("无法打开导入文件")

            parsePayload(content)
        }
    }

    private fun parseUri(uriString: String): Uri {
        require(uriString.isNotBlank()) { "文件地址无效" }
        return Uri.parse(uriString)
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