package com.boguan.audio

import java.io.File
import java.io.RandomAccessFile

/**
 * 流式写 16-bit PCM WAV。
 *
 * 关键设计：**头部按固定间隔回填**（默认 10 秒）。
 * 只在 close() 时回填的话，进程被系统强杀会留下全零头 → 不可播放的裸 PCM；
 * 每 10 秒回填一次，最坏只丢最后一段的头，前面的数据仍然可用。
 */
class WavWriter(
    private val file: File,
    private val sampleRate: Int = SAMPLE_RATE,
    private val channels: Int = 1,
    private val patchIntervalBytes: Long = SAMPLE_RATE * 2L * PATCH_INTERVAL_SECONDS,
) : AutoCloseable {

    private val raf: RandomAccessFile
    private var dataBytes: Long = 0
    private var patchedAtBytes: Long = 0
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
        if (dataBytes - patchedAtBytes >= patchIntervalBytes) {
            patchHeader()
        }
    }

    /** 立刻回填头（可在暂停、切后台等时机主动调用）。 */
    fun flushHeader() {
        if (!closed) patchHeader()
    }

    override fun close() {
        if (closed) return
        patchHeader()
        closed = true
        runCatching { raf.setLength(HEADER_SIZE + dataBytes) }
        runCatching { raf.close() }
    }

    val bytesWritten: Long get() = dataBytes

    private fun patchHeader() {
        if (closed) return
        patchedAtBytes = dataBytes
        runCatching {
            val pos = raf.filePointer
            raf.seek(0)
            raf.write(buildHeader(dataBytes))
            raf.seek(pos)
        }
    }

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
        buf.putInt(16)
        buf.putShort(1)
        buf.putShort(channels.toShort())
        buf.putInt(sampleRate)
        buf.putInt(byteRate)
        buf.putShort((channels * 2).toShort())
        buf.putShort(16)
        buf.put("data".toByteArray())
        buf.putInt(dataSize.toInt())
        return buf.array()
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val HEADER_SIZE = 44
        private const val PATCH_INTERVAL_SECONDS = 10L
    }
}
