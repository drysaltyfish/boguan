package com.boguan.app

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boguan.asr.AsrEngine
import com.boguan.audio.MicrophoneSource
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
 * 录音 + 实时转写的最小闭环（薄壳层，业务在 core 模块）。
 * 状态机：idle → recording ⇄ paused → finalizing → done
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = SessionRepository(BoguanDatabase(app))
    private val source = MicrophoneSource()
    private var engine: AsrEngine? = null
    private var session: Session? = null
    private var segmentStartMs = 0L
    private val sessionStartedElapsed = 0L

    var state by mutableStateOf(SessionState.IDLE)
        private set
    var sessionType by mutableStateOf(SessionType.LECTURE)
        private set
    var engineDescription by mutableStateOf("模型未加载")
        private set
    var partialText by mutableStateOf("")
        private set
    var finalizedText by mutableStateOf("")
        private set
    var statusMessage by mutableStateOf("")
        private set
    var canRecord by mutableStateOf(false)
        private set

    fun setType(type: SessionType) {
        if (state == SessionState.IDLE) sessionType = type
    }

    /** 预加载模型（进入界面时调用一次；加载耗时约 3–4 s）。 */
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
        if (!source.isRunning) {
            val ok = source.start { samples, count -> onFrame(engine, samples, count) }
            if (!ok) {
                statusMessage = "麦克风启动失败"
                return
            }
        }
        if (state == SessionState.IDLE) {
            val newSession = Session(type = sessionType)
            session = newSession
            repository.createSession(newSession)
            segmentStartMs = 0L
            finalizedText = ""
        }
        state = SessionState.RECORDING
        session?.let { repository.updateState(it.id, SessionState.RECORDING) }
        statusMessage = "正在记录"
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
        state = SessionState.FINALIZING
        statusMessage = "收尾中…"
        val current = session
        viewModelScope.launch(Dispatchers.IO) {
            if (current != null) {
                repository.finishSession(current.id, System.currentTimeMillis(), SessionState.DONE)
            }
            state = SessionState.DONE
            statusMessage = "已保存（终稿与摘要功能开发中）"
        }
    }

    private fun onFrame(engine: AsrEngine, samples: ShortArray, count: Int) {
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
                endMs = segmentStartMs + count * 1000L / 16_000,
                text = result.text.trim(),
                isFinal = false,
            )
            segmentStartMs = segment.endMs
            repository.insertSegment(segment)
            finalizedText = (finalizedText + " " + segment.text).trim()
            partialText = ""
        }
    }

    fun release() {
        source.stop()
        engine?.release()
        engine = null
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
}
