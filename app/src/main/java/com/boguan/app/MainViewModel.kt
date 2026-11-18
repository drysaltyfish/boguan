package com.boguan.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.boguan.app.recording.RecordingController
import com.boguan.app.recording.RecordingService
import com.boguan.model.Segment
import com.boguan.model.Session
import com.boguan.model.SessionState
import com.boguan.model.SessionType
import com.boguan.store.BoguanDatabase
import com.boguan.app.settings.AppSettings
import com.boguan.store.SessionRepository
import java.io.File

/**
 * UI 门面：
 * - 实时录音部分的状态在 [RecordingController]（由前台服务托管）；
 * - 历史浏览只读，直接查库。
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val controller = RecordingController
    private val player = SegmentPlayer()
    private val historyRepository = SessionRepository(BoguanDatabase(app))
    private val settings = AppSettings(app)

    init {
        controller.ensureInit(app)
        refreshHistory()
    }

    // ---- 实时录音状态（来自控制器） ----
    val state: SessionState get() = controller.state
    val sessionType: SessionType get() = controller.sessionType
    val engineDescription: String get() = controller.engineDescription
    val statusMessage: String get() = controller.statusMessage
    val canRecord: Boolean get() = controller.canRecord
    val partialText: String get() = controller.partialText
    val recordedBytes: Long get() = controller.recordedBytes
    val liveSegments: List<Segment> get() = controller.segments

    // ---- 历史 ----
    var history by mutableStateOf<List<Session>>(emptyList())
        private set

    var openedSession by mutableStateOf<Session?>(null)
        private set

    var openedSegments by mutableStateOf<List<Segment>>(emptyList())
        private set

    val playingSegmentId: Long? get() = player.playingSegmentId

    // ---- 设置 ----
    val baseUrl: String get() = settings.baseUrl
    val apiKey: String get() = settings.apiKey
    val model: String get() = settings.model

    fun saveSettings(baseUrl: String, apiKey: String, model: String) = settings.save(baseUrl, apiKey, model)

    // ---- 摘要状态 ----
    val summaryTitle: String get() = controller.summaryTitle
    val summaryBullets: List<String> get() = controller.summaryBullets
    val summaryStatus: String get() = controller.summaryStatus

    fun setType(type: SessionType) = controller.setType(type)
    fun preload() = controller.preload()

    fun start() = RecordingService.start(getApplication())
    fun pause() = controller.pause()

    fun stop() {
        player.stop()
        RecordingService.stop(getApplication())
        // 结束后刷新历史（当前场已入库）
        controller.state // 触发一次读取，确保状态最新
        refreshHistory()
    }

    fun refreshHistory() {
        history = runCatching { historyRepository.listSessions() }.getOrDefault(emptyList())
    }

    fun openSession(session: Session) {
        player.stop()
        openedSession = session
        openedSegments = runCatching { historyRepository.segmentsOf(session.id) }.getOrDefault(emptyList())
    }

    fun closeSession() {
        player.stop()
        openedSession = null
        openedSegments = emptyList()
    }

    fun togglePlayback(segment: Segment, audioPath: String?) {
        val path = audioPath ?: controller.currentAudioPath ?: return
        player.toggle(File(path), segment.id, segment.startMs, segment.endMs)
    }

    override fun onCleared() {
        super.onCleared()
        player.stop()
        // 录音由前台服务持有，Activity 销毁不影响
    }
}
