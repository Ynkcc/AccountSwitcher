package net.game.switcher.utils

import android.util.Log

import java.io.DataOutputStream

object ShellUtils {
    fun execRoot(command: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-mm", "-c", command))
            val result = process.waitFor()
            if (result != 0) {
                val errorOutput = process.errorStream.bufferedReader().readText().trim()
                Log.e("ShellUtils", "Command failed with exit code $result: $command")
                if (errorOutput.isNotEmpty()) {
                    Log.e("ShellUtils", "Error output: $errorOutput")
                }
            } else {
                Log.d("ShellUtils", "Command success: $command")
            }
            result == 0
        } catch (e: Exception) {
            // Fallback to plain su -c if -mm is not supported
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
                val result = process.waitFor()
                result == 0
            } catch (e2: Exception) {
                Log.e("ShellUtils", "Exception executing command: $command", e2)
                false
            }
        }
    }

    fun execRootGetOutput(command: String): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-mm", "-c", command))
            val output = process.inputStream.bufferedReader().readText().trim()
            val result = process.waitFor()
            if (result == 0) {
                Log.d("ShellUtils", "Command success: $command, output: $output")
                output
            } else {
                val errorOutput = process.errorStream.bufferedReader().readText().trim()
                Log.e("ShellUtils", "Command failed with exit code $result: $command")
                if (errorOutput.isNotEmpty()) {
                    Log.e("ShellUtils", "Error output: $errorOutput")
                }
                null
            }
        } catch (e: Exception) {
             try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
                val output = process.inputStream.bufferedReader().readText().trim()
                val result = process.waitFor()
                if (result == 0) output else null
            } catch (e2: Exception) {
                Log.e("ShellUtils", "Exception executing command: $command", e2)
                null
            }
        }
    }

    fun execRootGetBytes(command: String): ByteArray? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-mm", "-c", command))
            val output = process.inputStream.readBytes()
            val result = process.waitFor()
            if (result == 0) {
                Log.d("ShellUtils", "Command success: $command, bytes=${output.size}")
                output
            } else {
                val errorOutput = process.errorStream.bufferedReader().readText().trim()
                Log.e("ShellUtils", "Command failed with exit code $result: $command")
                if (errorOutput.isNotEmpty()) {
                    Log.e("ShellUtils", "Error output: $errorOutput")
                }
                null
            }
        } catch (e: Exception) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
                val output = process.inputStream.readBytes()
                val result = process.waitFor()
                if (result == 0) output else null
            } catch (e2: Exception) {
                Log.e("ShellUtils", "Exception executing command: $command", e2)
                null
            }
        }
    }

    fun checkRoot(): Boolean {
        return execRoot("id")
    }
}
