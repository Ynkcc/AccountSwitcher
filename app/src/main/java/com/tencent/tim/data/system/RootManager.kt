package com.tencent.tim.data.system

import android.util.Log
import java.io.File
import java.io.IOException

class RootManager {
    companion object {
        private const val TAG = "RootManager"
        private const val PUBG_PACKAGE = "com.tencent.tmgp.pubgmhd"
    }

    private var cachedDataPath: String? = null

    private fun createRootProcess(command: String, useMountMaster: Boolean): Process {
        val args = if (useMountMaster) {
            arrayOf("su", "-mm", "-c", command)
        } else {
            arrayOf("su", "-c", command)
        }
        return Runtime.getRuntime().exec(args)
    }

    private fun logFailure(command: String, exitCode: Int, errorOutput: String) {
        Log.e(TAG, "Command failed with exit code $exitCode: $command")
        if (errorOutput.isNotEmpty()) {
            Log.e(TAG, "Error output: $errorOutput")
        }
    }

    private inline fun <T> runWithRoot(command: String, block: (Process) -> T): T? {
        return try {
            block(createRootProcess(command, useMountMaster = true))
        } catch (e: Exception) {
            try {
                block(createRootProcess(command, useMountMaster = false))
            } catch (e2: Exception) {
                Log.e(TAG, "Exception executing command: $command", e2)
                null
            }
        }
    }

    private fun execRoot(command: String): Boolean {
        return runWithRoot(command) { process ->
            val result = process.waitFor()
            if (result == 0) {
                true
            } else {
                val errorOutput = process.errorStream.bufferedReader().use { it.readText().trim() }
                logFailure(command, result, errorOutput)
                false
            }
        } ?: false
    }

    private fun execRootGetOutput(command: String): String? {
        return runWithRoot(command) { process ->
            val output = process.inputStream.bufferedReader().use { it.readText().trim() }
            val result = process.waitFor()
            if (result == 0) {
                output
            } else {
                val errorOutput = process.errorStream.bufferedReader().use { it.readText().trim() }
                logFailure(command, result, errorOutput)
                null
            }
        }
    }

    private fun execRootGetBytes(command: String): ByteArray? {
        return runWithRoot(command) { process ->
            val output = process.inputStream.readBytes()
            val result = process.waitFor()
            if (result == 0) {
                output
            } else {
                val errorOutput = process.errorStream.bufferedReader().use { it.readText().trim() }
                logFailure(command, result, errorOutput)
                null
            }
        }
    }

    fun getDataPath(): String {
        cachedDataPath?.let { return it }
        val paths = listOf("/data/user/0/$PUBG_PACKAGE", "/data/data/$PUBG_PACKAGE")
        for (path in paths) {
            val output = execRootGetOutput("ls -d $path")
            if (output != null && output.contains(PUBG_PACKAGE)) {
                cachedDataPath = path
                return path
            }
        }
        Log.w(TAG, "Defaulting to /data/user/0/$PUBG_PACKAGE as no data path was found")
        return "/data/user/0/$PUBG_PACKAGE"
    }

    val loginFilePath get() = "${getDataPath()}/files/itop_login.txt"

    fun readFile(path: String): ByteArray? {
        val quotedPath = path.replace("\"", "\\\"")
        return execRootGetBytes("cat \"$quotedPath\"")?.also {
            if (it.isEmpty()) {
                Log.w(TAG, "Read empty file or failed to read: $path")
            }
        }
    }

    fun writeFile(path: String, content: ByteArray): Boolean {
        val parentPath = File(path).parent
        if (!parentPath.isNullOrEmpty()) {
            val quotedParent = parentPath.replace("\"", "\\\"")
            if (!execRoot("mkdir -p \"$quotedParent\"")) {
                Log.e(TAG, "Failed to create parent directory: $parentPath")
                return false
            }
        }

        val quotedPath = path.replace("\"", "\\\"")
        val writeResult = runWithRoot("cat > \"$quotedPath\"") { process ->
            try {
                process.outputStream.use { it.write(content) }
                val result = process.waitFor()
                if (result == 0) {
                    true
                } else {
                    val errorOutput = process.errorStream.bufferedReader().use { it.readText().trim() }
                    logFailure("cat > \"$quotedPath\"", result, errorOutput)
                    false
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed while writing file stream: $path", e)
                false
            }
        } ?: false

        if (!writeResult) {
            return false
        }

        val dataDir = getDataPath().replace("\"", "\\\"")
        val chownSuccess = execRoot("chown \$(stat -c %u:%g \"$dataDir\") \"$quotedPath\"")
        if (!chownSuccess) {
            Log.e(TAG, "chown failed for $path")
        }

        val chmodSuccess = execRoot("chmod 600 \"$quotedPath\"")
        if (!chmodSuccess) {
            Log.e(TAG, "chmod failed for $path")
        }

        return true
    }

    fun deleteFile(path: String): Boolean {
        val quotedPath = path.replace("\"", "\\\"")
        val result = execRoot("rm -f \"$quotedPath\"")
        if (!result) {
            Log.e(TAG, "Failed to delete file: $path")
        }
        return result
    }

    fun restartApp(): Boolean {
        val result = execRoot(
            "am force-stop $PUBG_PACKAGE && monkey -p $PUBG_PACKAGE -c android.intent.category.LAUNCHER 1"
        )
        if (!result) {
            Log.e(TAG, "Failed to restart app: $PUBG_PACKAGE")
        }
        return result
    }

    fun deleteFileSync(path: String): Boolean {
        return deleteFile(path)
    }
}
