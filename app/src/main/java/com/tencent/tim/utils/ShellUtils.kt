package com.tencent.tim.utils

import android.util.Log

object ShellUtils {
    private const val TAG = "ShellUtils"

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

    fun execRoot(command: String): Boolean {
        return runWithRoot(command) { process ->
            val result = process.waitFor()
            if (result == 0) {
                Log.d(TAG, "Command success: $command")
                true
            } else {
                val errorOutput = process.errorStream.bufferedReader().readText().trim()
                logFailure(command, result, errorOutput)
                false
            }
        } ?: false
    }

    fun execRootGetOutput(command: String): String? {
        return runWithRoot(command) { process ->
            val output = process.inputStream.bufferedReader().readText().trim()
            val result = process.waitFor()
            if (result == 0) {
                Log.d(TAG, "Command success: $command, output: $output")
                output
            } else {
                val errorOutput = process.errorStream.bufferedReader().readText().trim()
                logFailure(command, result, errorOutput)
                null
            }
        }
    }

    fun execRootGetBytes(command: String): ByteArray? {
        return runWithRoot(command) { process ->
            val output = process.inputStream.readBytes()
            val result = process.waitFor()
            if (result == 0) {
                Log.d(TAG, "Command success: $command, bytes=${output.size}")
                output
            } else {
                val errorOutput = process.errorStream.bufferedReader().readText().trim()
                logFailure(command, result, errorOutput)
                null
            }
        }
    }

    fun checkRoot(): Boolean {
        return execRoot("id")
    }
}
