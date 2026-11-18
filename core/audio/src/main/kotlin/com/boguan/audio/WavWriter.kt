package com.boguan.audio

import java.io.File
import java.io.RandomAccessFile

/**
 * 流式写 16-bit PCM WAV（ADR-0005：录制期保留 WAV，终稿 + 7 天后转 Opus）。
 *
 * 头部大小在 [close] 时回填 —— 录制过程中文件只追加数据区，
 * 即使进程被杀，最多丢失尾部未刷新的帧，已写入的部分仍可修复。
 */
class WavWriter(
    private val file: File,
    private val sampleRate: Int = SAMPLE_RATE,
    private val channels: Int = 1,
) : AutoCloseable {

    private val raf: RandomAccessFile
    private var dataBytes: Long = 0
    private var closed = false

    init {
        file.parentFile?.mkdirs()
        if (file.exists()) file.delete()
        raf = RandomAccessFile(file, "rw")
        raf.setLength(0)
        writePlaceholderHeader()
    }

    fun write(samples: ShortArray, count: Int) {
        if (closed || count <= 0) return
        val bytes = ByteArray(count * 2)
        for (i in 0 until count) {
            val v = samples[i].toInt()
            bytes[i * 2] = (v and 0xFF).toByte()
            bytes[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        raf.write(bytes)
        dataBytes += bytes.size
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching {
            raf.seek(0)
            raf.write(buildHeader(dataBytes))
            raf.setLength(HEADER_SIZE + dataBytes)
        }
        runCatching { raf.close() }
    }

    val bytesWritten: Long get() = dataBytes

    private fun writePlaceholderHeader() {
        raf.seek(0)
        raf.write(ByteArray(HEADER_SIZE))
    }

    private fun buildHeader(dataSize: Long): ByteArray {
        val byteRate = sampleRate * channels * 2
        val buf = java.nio.ByteBuffer.allocate(HEADER_SIZE).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray())
        buf.putInt((36 + dataSize).toInt())
        buf.put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray())
        buf.putInt(16)                       // fmt chunk size
        buf.putShort(1)                      // PCM
        buf.putShort(channels.toShort())
        buf.putInt(sampleRate)
        buf.putInt(byteRate)
        buf.putShort((channels * 2).toShort()) // block align
        buf.putShort(16)                     // bits per sample
        buf.put("data".toByteArray())
        buf.putInt(dataSize.toInt())
        return buf.array()
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val HEADER_SIZE = 44
    }
}
