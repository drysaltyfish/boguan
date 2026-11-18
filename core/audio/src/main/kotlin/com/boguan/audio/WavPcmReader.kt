package com.boguan.audio

import java.io.File

/**
 * 从已落盘的 WAV 里读 PCM（16-bit / 单声道 / 16 kHz），并支持按时间片裁剪。
 * 终稿二遍转写需要"按实时段的起止时间"重新解码，所以切片能力是必需的。
 */
object WavPcmReader {

    const val SAMPLE_RATE = 16_000

    fun read(file: File): FloatArray? {
        if (!file.isFile || file.length() <= 44) return null
        val bytes = file.readBytes()
        var idx = 12
        var dataStart = -1
        var dataLen = 0
        while (idx + 8 <= bytes.size) {
            val id = String(bytes, idx, 4, Charsets.US_ASCII)
            val size = (bytes[idx + 4].toInt() and 0xff) or
                ((bytes[idx + 5].toInt() and 0xff) shl 8) or
                ((bytes[idx + 6].toInt() and 0xff) shl 16) or
                ((bytes[idx + 7].toInt() and 0xff) shl 24)
            if (id == "data") {
                dataStart = idx + 8
                dataLen = size
                break
            }
            if (size <= 0) break
            idx += 8 + size + (size and 1)
        }
        if (dataStart < 0) return null
        val available = minOf(dataLen, bytes.size - dataStart)
        val n = available / 2
        if (n <= 0) return null
        val out = FloatArray(n)
        for (k in 0 until n) {
            val lo = bytes[dataStart + 2 * k].toInt() and 0xff
            val hi = bytes[dataStart + 2 * k + 1].toInt() and 0xff
            out[k] = (((hi shl 8) or lo).toShort()) / 32768f
        }
        return out
    }

    /** 取 [startMs, endMs) 区间；越界部分自动裁剪。 */
    fun slice(samples: FloatArray, startMs: Long, endMs: Long): FloatArray {
        val start = (startMs * SAMPLE_RATE / 1000).toInt().coerceIn(0, samples.size)
        val end = (endMs * SAMPLE_RATE / 1000).toInt().coerceIn(start, samples.size)
        if (end - start < 1) return FloatArray(0)
        return samples.copyOfRange(start, end)
    }
}
