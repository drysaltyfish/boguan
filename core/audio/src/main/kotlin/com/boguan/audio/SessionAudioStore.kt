package com.boguan.audio

import java.io.File

/**
 * 会话音频文件布局：`<root>/<sessionId>/audio.wav`
 *
 * 用独立目录而不是单文件命名，方便将来同一场次扩展（例如终稿 Opus、分片）。
 */
class SessionAudioStore(private val root: File) {

    fun sessionDir(sessionId: String): File = File(root, sessionId)

    fun audioFile(sessionId: String): File = File(sessionDir(sessionId), "audio.wav")

    fun hasAudio(sessionId: String): Boolean {
        val f = audioFile(sessionId)
        return f.isFile && f.length() > 44
    }

    fun openWriter(sessionId: String): WavWriter = WavWriter(audioFile(sessionId))

    /** 该场音频的毫秒时长（按 WAV 文件大小推算）。 */
    fun durationMs(sessionId: String): Long {
        val f = audioFile(sessionId)
        if (!f.isFile) return 0
        val dataBytes = (f.length() - 44).coerceAtLeast(0)
        return dataBytes / 2 * 1000 / WavWriter.SAMPLE_RATE
    }
}
