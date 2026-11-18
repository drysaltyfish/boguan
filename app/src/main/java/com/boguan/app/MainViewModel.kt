package com.boguan.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.boguan.app.recording.RecordingController
import com.boguan.app.recording.RecordingService
import com.boguan.model.Segment
import com.boguan.model.SessionState
import com.boguan.model.SessionType
import java.io.File

/**
 * UI 门面：只做两件事 —— 把控制器的可观察状态暴露给 Compose、把用户动作转成服务/控制器调用。
 * 真正的录音逻辑在 [RecordingController]，由前台服务托管（ADR-0005）。
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val controller = RecordingController
    private val player = SegmentPlayer()

    init {
        controller.ensureInit(app)
    }

    val state: SessionState get() = controller.state
    val sessionType: SessionType get() = controller.sessionType
    val engineDescription: String get() = controller.engineDescription
    val statusMessage: String get() = controller.statusMessage
    val canRecord: Boolean get() = controller.canRecord
    val partialText: String get() = controller.partialText
    val recordedBytes: Long get() = controller.recordedBytes
    val segments: List<Segment> get() = controller.segments
    val playingSegmentId: Long? get() = player.playingSegmentId

    fun setType(type: SessionType) = controller.setType(type)

    fun preload() = controller.preload()

    fun start() {
        RecordingService.start(getApplication())
    }

    fun pause() = controller.pause()

    fun stop() {
        player.stop()
        RecordingService.stop(getApplication())
    }

    fun togglePlayback(segment: Segment) {
        val path = controller.currentAudioPath ?: return
        player.toggle(File(path), segment.id, segment.startMs, segment.endMs)
    }

    override fun onCleared() {
        super.onCleared()
        player.stop()
        // 注意：不在这里停录音 —— 录音由前台服务持有，Activity 销毁不影响
    }
}
