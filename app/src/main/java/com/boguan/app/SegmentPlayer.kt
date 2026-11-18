package com.boguan.app

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * 锚点回听（ADR-0004）：从某个转写段的起始时间播放到结束时间。
 * 只支持单段播放，不做连续播放队列。
 */
class SegmentPlayer {

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private val stopRunnable = Runnable { stop() }

    var playingSegmentId by mutableStateOf<Long?>(null)
        private set

    fun toggle(file: File, segmentId: Long, startMs: Long, endMs: Long) {
        if (playingSegmentId == segmentId) {
            stop()
            return
        }
        stop()
        if (!file.isFile) return
        val mp = MediaPlayer()
        player = mp
        runCatching {
            mp.setDataSource(file.absolutePath)
            mp.prepare()
            mp.seekTo(startMs.toInt())
            mp.start()
            playingSegmentId = segmentId
            val duration = (endMs - startMs).coerceAtLeast(300)
            handler.postDelayed(stopRunnable, duration)
            mp.setOnCompletionListener { stop() }
        }.onFailure {
            stop()
        }
    }

    fun stop() {
        handler.removeCallbacks(stopRunnable)
        playingSegmentId = null
        player?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        player = null
    }
}
