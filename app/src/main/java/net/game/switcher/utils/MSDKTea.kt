package net.game.switcher.utils

import java.nio.ByteBuffer
import java.nio.ByteOrder

object MSDKTea {
    private const val ROUNDS = 16
    private const val DELTA = 0x9e3779b9.toInt()

    fun decrypt(data: ByteArray, key: ByteArray): ByteArray? {
        if (data.size < 16 || data.size % 8 != 0) return null

        val teaKey = ByteBuffer.wrap(key).order(ByteOrder.BIG_ENDIAN).let { buffer ->
            IntArray(4) { buffer.int }
        }

        val out = ByteArray(data.size)
        
        // Decrypt first block
        val d0 = decryptBlock(data.copyOfRange(0, 8), teaKey)
        System.arraycopy(d0, 0, out, 0, 8)

        var prevD = d0
        var prevC = data.copyOfRange(0, 8)

        for (i in 8 until data.size step 8) {
            val cipher = data.copyOfRange(i, i + 8)
            
            // P[i] = TEA_Dec(C[i] ^ prev_D) ^ prev_C
            val xored = ByteArray(8) { j -> (cipher[j].toInt() xor prevD[j].toInt()).toByte() }
            val dec = decryptBlock(xored, teaKey)
            val plain = ByteArray(8) { j -> (dec[j].toInt() xor prevC[j].toInt()).toByte() }
            
            System.arraycopy(plain, 0, out, i, 8)
            prevD = dec
            prevC = cipher
        }

        val pos = (out[0].toInt() and 7) + 3
        if (pos >= out.size - 7) return null
        
        return out.copyOfRange(pos, out.size - 7)
    }

    private fun decryptBlock(v: ByteArray, k: IntArray): ByteArray {
        val buffer = ByteBuffer.wrap(v).order(ByteOrder.BIG_ENDIAN)
        var v0 = buffer.int
        var v1 = buffer.int
        
        var sum = (DELTA * ROUNDS)
        
        for (i in 0 until ROUNDS) {
            v1 -= ((v0 shl 4) + k[2]) xor (v0 + sum) xor ((v0 ushr 5) + k[3])
            v0 -= ((v1 shl 4) + k[0]) xor (v1 + sum) xor ((v1 ushr 5) + k[1])
            sum -= DELTA
        }
        
        return ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            .putInt(v0).putInt(v1).array()
    }
}
