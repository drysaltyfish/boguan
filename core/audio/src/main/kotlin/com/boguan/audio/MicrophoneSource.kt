package com.boguan.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 麦克风音频源：16 kHz / 单声道 / 16-bit PCM。
 *
 * 设计要点（ADR-0002）：
 * - 不做激进降噪/去混响（安静教室收益低、伪影风险高）；
 * - 采集与识别解耦：本类只负责稳定吐帧，不关心上层怎么用。
 */
class MicrophoneSource(
    private val sampleRate: Int = SAMPLE_RATE,
    private val frameSamples: Int = FRAME_SAMPLES,
) {

    /** 帧回调。samples 只在前 [count] 个位置有效，回调返回后即被复用，勿持有。 */
    fun interface FrameListener {
        fun onFrame(samples: ShortArray, count: Int)
    }

    private val running = AtomicBoolean(false)
    private var record: AudioRecord? = null
    private var worker: Thread? = null

    val isRunning: Boolean get() = running.get()

    @SuppressLint("MissingPermission")
    fun start(listener: FrameListener): Boolean {
        if (running.get()) return true
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) return false
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer * 2, sampleRate),
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return false
        }
        this.record = record
        running.set(true)
        record.startRecording()

        worker = Thread({
            val buffer = ShortArray(frameSamples)
            while (running.get()) {
                val n = record.read(buffer, 0, buffer.size)
                if (n > 0) listener.onFrame(buffer, n)
            }
        }, "boguan-audio").apply { start() }
        return true
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        worker?.join(1_000)
        worker = null
        record?.let {
            runCatching { it.stop() }
            it.release()
        }
        record = null
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val FRAME_SAMPLES = 1_600 // 100 ms
    }
}
