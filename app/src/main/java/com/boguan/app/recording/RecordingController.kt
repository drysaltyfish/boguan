package com.boguan.app.recording

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import java.io.File
import java.util.concurrent.Executors

/**
 * 录音会话的单一状态持有者（ADR-0005 状态机）。
 *
 * 放在进程级单例而不是 ViewModel 里，是为了让**前台服务**与 UI 共享同一份状态：
 * Activity 被系统回收、屏幕熄灭都不影响录音继续。所有状态变更都在主线程执行。
 */
object RecordingController {

    private const val TAG = "RecordingController"

    private val main = Handler(Looper.getMainLooper())
    private val engineLoader = Executors.newSingleThreadExecutor { r -> Thread(r, "boguan-engine") }

    private var appContext: Context? = null
    private var repository: SessionRepository? = null
    private var audioStore: SessionAudioStore? = null

    private var engine: AsrEngine? = null
    private var source: MicrophoneSource? = null
    private var writer: WavWriter? = null
    private var session: Session? = null
    private var segmentStartMs = 0L

    // ---- UI 可观察状态 ----
    var state by mutableStateOf(SessionState.IDLE)
        private set
    var sessionType by mutableStateOf(SessionType.LECTURE)
        private set
    var engineDescription by mutableStateOf("模型未加载")
        private set
    var statusMessage by mutableStateOf("")
        private set
    var canRecord by mutableStateOf(false)
        private set
    var partialText by mutableStateOf("")
        private set
    var recordedBytes by mutableStateOf(0L)
        private set
    var startedAtWallMs by mutableStateOf(0L)
        private set

    val segments = mutableStateListOf<Segment>()

    /** 当前会话的音频文件路径；供回听使用。 */
    val currentAudioPath: String? get() = session?.audioPath

    fun ensureInit(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        repository = SessionRepository(BoguanDatabase(app))
        audioStore = SessionAudioStore(File(app.filesDir, "sessions"))
    }

    fun setType(type: SessionType) {
        if (state == SessionState.IDLE) sessionType = type
    }

    fun preload() {
        if (engine != null || engineLoader.isShutdown) return
        val ctx = appContext ?: return
        canRecord = false
        statusMessage = "模型加载中…"
        engineLoader.execute {
            val modelsRoot = File(ctx.getExternalFilesDir(null), "model")
            val loaded = AsrEngine.create(modelsRoot)
            main.post {
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
    }

    fun start() {
        if (state != SessionState.IDLE && state != SessionState.PAUSED) return
        val engine = engine ?: run { statusMessage = "模型未就绪"; return }
        val repository = repository ?: return
        val audioStore = audioStore ?: return

        if (state == SessionState.IDLE) {
            val newSession = Session(type = sessionType)
            newSession.audioPath = audioStore.audioFile(newSession.id).absolutePath
            session = newSession
            repository.createSession(newSession)
            segments.clear()
            partialText = ""
            segmentStartMs = 0L
            recordedBytes = 0L
            startedAtWallMs = System.currentTimeMillis()
            writer = runCatching { audioStore.openWriter(newSession.id) }
                .onFailure { Log.e(TAG, "open wav writer failed", it) }
                .getOrNull()
        }

        if (source?.isRunning != true) {
            val created = MicrophoneSource()
            val ok = created.start { samples, count -> onFrame(engine, samples, count) }
            if (!ok) {
                statusMessage = "麦克风启动失败"
                return
            }
            source = created
        }
        state = SessionState.RECORDING
        session?.let { repository.updateState(it.id, SessionState.RECORDING) }
        statusMessage = "正在记录"
    }

    fun pause() {
        if (state != SessionState.RECORDING) return
        state = SessionState.PAUSED
        session?.let { repository?.updateState(it.id, SessionState.PAUSED) }
        statusMessage = "已暂停"
    }

    /** 结束记录：停采集、封 WAV、写终态。可在服务销毁时安全调用。 */
    fun stop() {
        if (state != SessionState.RECORDING && state != SessionState.PAUSED) return
        val repository = repository
        val current = session
        state = SessionState.FINALIZING
        statusMessage = "收尾中…"
        main.postDelayed({
            source?.stop()
            source = null
            writer?.close()
            writer = null
            if (current != null && repository != null) {
                repository.finishSession(current.id, System.currentTimeMillis(), SessionState.DONE)
            }
            state = SessionState.DONE
            statusMessage = "已保存 · 音频 " + (recordedBytes / 1024) + " KB"
        }, 0)
    }

    fun release() {
        source?.stop()
        source = null
        writer?.close()
        writer = null
        engine?.release()
        engine = null
    }

    private fun onFrame(engine: AsrEngine, samples: ShortArray, count: Int) {
        writer?.write(samples, count)
        val result = try {
            engine.accept(samples, count)
        } catch (t: Throwable) {
            Log.e(TAG, "asr failed", t)
            return
        }
        main.post {
            recordedBytes = writer?.bytesWritten ?: 0L
            partialText = result.text
            if (result.isEndpoint && result.text.isNotBlank()) {
                persistSegment(result.text.trim(), count)
                partialText = ""
            }
        }
    }

    private fun persistSegment(text: String, sampleCount: Int) {
        val repository = repository ?: return
        val sessionId = session?.id ?: return
        val segment = Segment(
            sessionId = sessionId,
            startMs = segmentStartMs,
            endMs = segmentStartMs + sampleCount * 1000L / MicrophoneSource.SAMPLE_RATE,
            text = text,
        )
        segmentStartMs = segment.endMs
        repository.insertSegment(segment)
        repository.segmentsOf(sessionId).lastOrNull()?.let { segments.add(it) }
    }
}
