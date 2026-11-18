package com.boguan.app

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boguan.asr.AsrEngine
import com.boguan.audio.MicrophoneSource
import com.boguan.audio.SessionAudioStore
import com.boguan.audio.WavWriter
import com.boguan.model.Segment
import com.boguan.model.Session
import com.boguan.model.SessionState
import com.boguan.model.SessionType
import com.boguan.store.BoguanDatabase
import com.boguan.store.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * 录音 + 实时转写 + 音频落盘的最小闭环。
 * 状态机：idle → recording ⇄ paused → finalizing → done
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = SessionRepository(BoguanDatabase(app))
    private val audioStore = SessionAudioStore(File(app.filesDir, "sessions"))
    private val source = MicrophoneSource()
    private val player = SegmentPlayer()
    private var engine: AsrEngine? = null
    private var session: Session? = null
    private var writer: WavWriter? = null
    private var segmentStartMs = 0L

    var state by mutableStateOf(SessionState.IDLE)
        private set
    var sessionType by mutableStateOf(SessionType.LECTURE)
        private set
    var engineDescription by mutableStateOf("模型未加载")
        private set
    var partialText by mutableStateOf("")
        private set
    var statusMessage by mutableStateOf("")
        private set
    var canRecord by mutableStateOf(false)
        private set
    var recordedBytes by mutableStateOf(0L)
        private set

    val segments = mutableStateListOf<Segment>()

    val playingSegmentId: Long? get() = player.playingSegmentId

    fun setType(type: SessionType) {
        if (state == SessionState.IDLE) sessionType = type
    }

    fun preload() {
        if (engine != null) return
        viewModelScope.launch(Dispatchers.Default) {
            val modelsRoot = File(getApplication<Application>().getExternalFilesDir(null), "model")
            val loaded = AsrEngine.create(modelsRoot)
            if (loaded == null) {
                statusMessage = "未找到可用模型（" + modelsRoot.absolutePath + "）"
                canRecord = false
            } else {
                engine = loaded
                engineDescription = loaded.description
                canRecord = true
                statusMessage = "就绪"
            }
        }
    }

    fun start() {
        if (state != SessionState.IDLE && state != SessionState.PAUSED) return
        val engine = engine ?: return

        if (state == SessionState.IDLE) {
            val newSession = Session(type = sessionType)
            newSession.audioPath = audioStore.audioFile(newSession.id).absolutePath
            session = newSession
            repository.createSession(newSession)
            segments.clear()
            partialText = ""
            segmentStartMs = 0L
            writer = runCatching { audioStore.openWriter(newSession.id) }
                .onFailure { Log.e(TAG, "open wav writer failed", it) }
                .getOrNull()
        }

        if (!source.isRunning) {
            val ok = source.start { samples, count -> onFrame(engine, samples, count) }
            if (!ok) {
                statusMessage = "麦克风启动失败"
                return
            }
        }
        state = SessionState.RECORDING
        session?.let { repository.updateState(it.id, SessionState.RECORDING) }
        statusMessage = "正在记录（音频同时写入本地）"
    }

    fun pause() {
        if (state != SessionState.RECORDING) return
        state = SessionState.PAUSED
        session?.let { repository.updateState(it.id, SessionState.PAUSED) }
        statusMessage = "已暂停"
    }

    fun stop() {
        if (state != SessionState.RECORDING && state != SessionState.PAUSED) return
        source.stop()
        writer?.close()
        writer = null
        player.stop()
        state = SessionState.FINALIZING
        statusMessage = "收尾中…"
        val current = session
        viewModelScope.launch(Dispatchers.IO) {
            if (current != null) {
                repository.finishSession(current.id, System.currentTimeMillis(), SessionState.DONE)
            }
            state = SessionState.DONE
            statusMessage = "已保存 · 音频 " + (recordedBytes / 1024 / 1024) + " MB"
        }
    }

    fun togglePlayback(segment: Segment) {
        val path = session?.audioPath ?: return
        player.toggle(File(path), segment.id, segment.startMs, segment.endMs)
    }

    private fun onFrame(engine: AsrEngine, samples: ShortArray, count: Int) {
        writer?.write(samples, count)
        recordedBytes = writer?.bytesWritten ?: 0L

        val result = try {
            engine.accept(samples, count)
        } catch (t: Throwable) {
            Log.e(TAG, "asr failed", t)
            return
        }
        partialText = result.text
        if (result.isEndpoint && result.text.isNotBlank()) {
            val sessionId = session?.id ?: return
            val segment = Segment(
                sessionId = sessionId,
                startMs = segmentStartMs,
                endMs = segmentStartMs + count * 1000L / MicrophoneSource.SAMPLE_RATE,
                text = result.text.trim(),
            )
            segmentStartMs = segment.endMs
            repository.insertSegment(segment)
            // 回读以拿到自增 id（回听需要 id 作为播放句柄）
            val inserted = repository.segmentsOf(sessionId).lastOrNull()
            if (inserted != null) segments.add(inserted)
            partialText = ""
        }
    }

    fun release() {
        source.stop()
        writer?.close()
        writer = null
        player.stop()
        engine?.release()
        engine = null
    }

    override fun onCleared() {
        super.onCleared()
        release()
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
}
