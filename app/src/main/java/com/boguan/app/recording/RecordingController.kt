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
import com.boguan.asr.OfflineAsrEngine
import com.boguan.audio.MicrophoneSource
import com.boguan.audio.SessionAudioStore
import com.boguan.audio.WavPcmReader
import com.boguan.audio.WavWriter
import com.boguan.model.Segment
import com.boguan.model.Session
import com.boguan.model.SessionState
import com.boguan.model.SessionType
import com.boguan.store.BoguanDatabase
import com.boguan.store.SessionRepository
import com.boguan.app.settings.AppSettings
import com.boguan.summarize.ConsolidatedParser
import com.boguan.summarize.ConsolidatedSummaryService
import com.boguan.summarize.LlmEndpoint
import com.boguan.summarize.MicroSummaryService
import com.boguan.summarize.OpenAiCompatibleClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    private const val MIN_SUMMARY_CHARS = 60
    /** 结构整理至少累积这么多条微摘要才有合并价值。 */
    private const val MIN_CONSOLIDATION_MICROS = 2

    private val main = Handler(Looper.getMainLooper())
    private val engineLoader = Executors.newSingleThreadExecutor { r -> Thread(r, "boguan-engine") }
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val microSummarizer = MicroSummaryService(OpenAiCompatibleClient())
    private val consolidator = ConsolidatedSummaryService(OpenAiCompatibleClient())

    private var appContext: Context? = null
    private var repository: SessionRepository? = null
    private var audioStore: SessionAudioStore? = null

    private var engine: AsrEngine? = null
    private var source: MicrophoneSource? = null
    private var writer: WavWriter? = null
    private var session: Session? = null
    private var segmentStartMs = 0L
    private var samplesSinceSegmentStart = 0L
    private var appSettings: AppSettings? = null
    private var summaryJob: Job? = null
    private var pendingSummaryText = StringBuilder()
    private var previousSummary = ""
    private var summaryWindowStartMs = 0L
    private var consolidationJob: Job? = null
    private var pendingConsolidationText = StringBuilder()
    private var pendingConsolidationCount = 0
    private var previousConsolidated = ""
    private var consolidationWindowStartMs = 0L
    private var consolidationCount = 0
    private var totalSamplesWritten = 0L

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
    var summaryTitle by mutableStateOf("")
        private set
    var summaryBullets by mutableStateOf<List<String>>(emptyList())
        private set
    var summaryStatus by mutableStateOf("")
        private set
    var consolidatedTitle by mutableStateOf("")
        private set
    var consolidatedLines by mutableStateOf<List<String>>(emptyList())
        private set
    var consolidatedStatus by mutableStateOf("")
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
        appSettings = AppSettings(app)
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
        if (state == SessionState.RECORDING || state == SessionState.FINALIZING) return
        val engine = engine ?: run { statusMessage = "模型未就绪"; return }
        val repository = repository ?: return
        val audioStore = audioStore ?: return

        if (state == SessionState.IDLE || state == SessionState.DONE) {
            val newSession = Session(type = sessionType)
            newSession.audioPath = audioStore.audioFile(newSession.id).absolutePath
            session = newSession
            repository.createSession(newSession)
            segments.clear()
            partialText = ""
            segmentStartMs = 0L
            samplesSinceSegmentStart = 0L
            totalSamplesWritten = 0L
            recordedBytes = 0L
            startedAtWallMs = System.currentTimeMillis()
            pendingSummaryText = StringBuilder()
            previousSummary = ""
            summaryWindowStartMs = 0L
            summaryTitle = ""
            summaryBullets = emptyList()
            summaryStatus = if (appSettings?.endpointOrNull() == null) "未配置 API key：摘要不会生成" else ""
            pendingConsolidationText = StringBuilder()
            pendingConsolidationCount = 0
            previousConsolidated = ""
            consolidationWindowStartMs = 0L
            consolidationCount = 0
            consolidatedTitle = ""
            consolidatedLines = emptyList()
            consolidatedStatus = ""
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
        startSummaryLoop()
        startConsolidationLoop()
    }

    fun pause() {
        if (state != SessionState.RECORDING) return
        flushSummary("暂停")
        state = SessionState.PAUSED
        session?.let { repository?.updateState(it.id, SessionState.PAUSED) }
        statusMessage = "已暂停"
    }

    /** 结束记录：停采集、封 WAV、写终态。可在服务销毁时安全调用。 */
    fun stop() {
        if (state != SessionState.RECORDING && state != SessionState.PAUSED) return
        summaryJob?.cancel()
        summaryJob = null
        consolidationJob?.cancel()
        consolidationJob = null
        flushSummary("结束")
        flushConsolidation("结束")
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
            // 单场结束：控制器回到 IDLE，允许立即开始下一场（DONE 是单场终态，不是应用终态）
            state = SessionState.IDLE
            statusMessage = "已保存 · 音频 " + (recordedBytes / 1024) + " KB"
            val audioPath = current?.audioPath
            if (current != null && audioPath != null) {
                runFinalPass(current.id, audioPath)
            }
        }, 0)
    }

    // ---------------- 终稿二遍（ADR-0002） ----------------

    /** 用离线模型按实时段的起止时间重跑一遍，覆盖段文本；失败不影响已保存的实时稿。 */
    private fun runFinalPass(sessionId: String, wavPath: String) {
        ioScope.launch {
            val modelsRoot = appContext?.let { File(it.getExternalFilesDir(null), "model") } ?: return@launch
            main.post { statusMessage = "终稿处理中（离线模型）…" }
            val engine = OfflineAsrEngine.create(modelsRoot)
            if (engine == null) {
                main.post { statusMessage = "终稿模型未安装，保留实时稿" }
                return@launch
            }
            try {
                val samples = WavPcmReader.read(File(wavPath))
                if (samples == null) {
                    main.post { statusMessage = "终稿失败：音频不可读" }
                    return@launch
                }
                val segs = repository?.segmentsOf(sessionId).orEmpty()
                var updated = 0
                var review = 0
                for (s in segs) {
                    val text = decodeSegment(engine, samples, s.startMs, s.endMs)
                    if (text.isBlank()) continue
                    // 只写二遍稿，不覆盖实时稿（ADR-0010）
                    repository?.updateSegmentOfflineText(s.id, text)
                    val flagged = com.boguan.model.TranscriptMerge.needsReview(s.text, text)
                    if (flagged) review++
                    updated++
                    main.post {
                        val idx = segments.indexOfFirst { it.id == s.id }
                        if (idx >= 0) {
                            segments[idx] = s.copy(offlineText = text, isFinal = true)
                        }
                    }
                }
                main.post {
                    statusMessage = if (review > 0) {
                        "终稿二遍完成（" + updated + "/" + segs.size + " 段，" + review + " 段待确认）"
                    } else {
                        "终稿二遍完成（" + updated + "/" + segs.size + " 段）"
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "final pass failed", t)
                main.post { statusMessage = "终稿失败：" + (t.message ?: t.javaClass.simpleName) }
            } finally {
                engine.release()
            }
        }
    }

    private fun decodeSegment(engine: OfflineAsrEngine, all: FloatArray, startMs: Long, endMs: Long): String {
        val slice = WavPcmReader.slice(all, startMs, endMs)
        if (slice.size < WavPcmReader.SAMPLE_RATE / 2) return ""
        val maxWindow = OfflineAsrEngine.MAX_WINDOW_SECONDS * WavPcmReader.SAMPLE_RATE
        if (slice.size <= maxWindow) return engine.transcribe(slice)
        val sb = StringBuilder()
        var offset = 0
        while (offset < slice.size) {
            val end = minOf(offset + maxWindow, slice.size)
            sb.append(engine.transcribe(slice.copyOfRange(offset, end))).append(' ')
            offset = end
        }
        return sb.toString().trim()
    }

    // ---------------- 摘要调度（ADR-0003） ----------------

    private fun startSummaryLoop() {
        summaryJob?.cancel()
        val interval = appSettings?.summaryIntervalMs ?: AppSettings.DEFAULT_INTERVAL_MS
        summaryJob = ioScope.launch {
            while (isActive) {
                delay(interval)
                main.post { flushSummary("定时") }
            }
        }
    }

    /** 把窗口内累积的转写送给 LLM；失败时把文本退回缓冲，下一轮重试。 */
    private fun flushSummary(reason: String) {
        val sessionId = session?.id ?: return
        if (state == SessionState.FINALIZING && reason == "结束") {
            // 结束时的最后一次 flush 允许
        } else if (state != SessionState.RECORDING && state != SessionState.PAUSED && reason != "结束") {
            return
        }
        val text = pendingSummaryText.toString().trim()
        if (text.length < MIN_SUMMARY_CHARS) return

        val endpoint = appSettings?.endpointOrNull()
        if (endpoint == null) {
            summaryStatus = "未配置 API key：摘要已跳过"
            return
        }
        pendingSummaryText = StringBuilder()
        val startMs = summaryWindowStartMs
        val endMs = segmentStartMs
        summaryStatus = "摘要生成中（" + reason + "）…"

        ioScope.launch {
            try {
                val response = microSummarizer.summarize(
                    endpoint = endpoint,
                    type = sessionType,
                    previousSummary = previousSummary,
                    windowText = text,
                )
                val bullets = parseStringList(response.content, "bullets")
                val title = parseString(response.content, "title")
                main.post {
                    previousSummary = bullets.joinToString("；").ifBlank { previousSummary }
                    summaryTitle = title
                    summaryBullets = bullets
                    summaryStatus = "摘要已更新至 " + fmtMs(endMs)
                    summaryWindowStartMs = endMs
                    // 微摘要同时喂给 10 分钟结构整理的输入缓冲
                    if (title.isNotBlank() || bullets.isNotEmpty()) {
                        pendingConsolidationText
                            .append(title.ifBlank { "（无标题）" })
                            .append("：")
                            .append(bullets.joinToString("；"))
                            .append('\n')
                        pendingConsolidationCount++
                    }
                    repository?.insertMicroSummary(
                        com.boguan.model.MicroSummary(
                            sessionId = sessionId,
                            kind = com.boguan.model.MicroSummary.Kind.MICRO,
                            startMs = startMs,
                            endMs = endMs,
                            payloadJson = response.content,
                        ),
                    )
                }
            } catch (t: Throwable) {
                Log.w(TAG, "summary failed", t)
                main.post {
                    summaryStatus = "摘要失败：" + (t.message ?: t.javaClass.simpleName)
                    // 退回缓冲，下一轮重试
                    pendingSummaryText.insert(0, text + "\n")
                }
            }
        }
    }

    // ---------------- 结构整理调度（每 10 分钟，ADR-0003） ----------------

    private fun startConsolidationLoop() {
        consolidationJob?.cancel()
        val interval = appSettings?.consolidationIntervalMs ?: AppSettings.DEFAULT_CONSOLIDATION_INTERVAL_MS
        consolidationJob = ioScope.launch {
            while (isActive) {
                delay(interval)
                main.post { flushConsolidation("定时") }
            }
        }
    }

    /**
     * 把本阶段的微摘要合并成阶段小结。输入不足两条微摘要时跳过
     * （单条微摘要已经是可用粒度的摘要，再压一次只是浪费 token）。
     */
    private fun flushConsolidation(reason: String) {
        val sessionId = session?.id ?: return
        if (reason != "结束" && state != SessionState.RECORDING && state != SessionState.PAUSED) return
        if (pendingConsolidationCount < MIN_CONSOLIDATION_MICROS) return
        val micros = pendingConsolidationText.toString().trim()
        if (micros.isBlank()) return
        val endpoint = appSettings?.endpointOrNull() ?: return

        pendingConsolidationText = StringBuilder()
        pendingConsolidationCount = 0
        val startMs = consolidationWindowStartMs
        val endMs = segmentStartMs
        val stage = consolidationCount + 1
        consolidatedStatus = "阶段小结生成中（" + reason + "）…"

        ioScope.launch {
            try {
                val response = consolidator.consolidate(
                    endpoint = endpoint,
                    type = sessionType,
                    previous = previousConsolidated,
                    microSummaries = micros,
                )
                val payload = ConsolidatedParser.parse(response.content, sessionType)
                val lines = payload.displayLines()
                main.post {
                    consolidationCount = stage
                    consolidationWindowStartMs = endMs
                    consolidatedTitle = payload.title
                    consolidatedLines = lines
                    previousConsolidated = payload.title + "：" + payload.summary
                    consolidatedStatus = "阶段小结已更新至 " + fmtMs(endMs) + "（第 " + stage + " 段）"
                    repository?.insertMicroSummary(
                        com.boguan.model.MicroSummary(
                            sessionId = sessionId,
                            kind = com.boguan.model.MicroSummary.Kind.CONSOLIDATED,
                            startMs = startMs,
                            endMs = endMs,
                            payloadJson = response.content,
                        ),
                    )
                }
            } catch (t: Throwable) {
                Log.w(TAG, "consolidation failed", t)
                main.post {
                    consolidatedStatus = "阶段小结失败：" + (t.message ?: t.javaClass.simpleName)
                    // 退回缓冲，下一轮重试
                    pendingConsolidationText.insert(0, micros + "\n")
                    pendingConsolidationCount += MIN_CONSOLIDATION_MICROS
                }
            }
        }
    }

    private fun parseStringList(jsonText: String, key: String): List<String> = runCatching {
        val obj = json.parseToJsonElement(jsonText).jsonObject
        (obj[key] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
    }.getOrDefault(emptyList())

    private fun parseString(jsonText: String, key: String): String = runCatching {
        json.parseToJsonElement(jsonText).jsonObject[key]?.jsonPrimitive?.contentOrNull.orEmpty()
    }.getOrDefault("")

    private fun fmtMs(ms: Long): String {
        val s = ms / 1000
        return String.format(java.util.Locale.US, "%02d:%02d", s / 60, s % 60)
    }

    fun release() {
        summaryJob?.cancel()
        summaryJob = null
        consolidationJob?.cancel()
        consolidationJob = null
        source?.stop()
        source = null
        writer?.close()
        writer = null
        engine?.release()
        engine = null
    }

    private fun onFrame(engine: AsrEngine, samples: ShortArray, count: Int) {
        writer?.write(samples, count)
        samplesSinceSegmentStart += count
        totalSamplesWritten += count
        val result = try {
            engine.accept(samples, count)
        } catch (t: Throwable) {
            Log.e(TAG, "asr failed", t)
            return
        }
        main.post {
            recordedBytes = totalSamplesWritten * 2
            partialText = result.text
            if (result.isEndpoint && result.text.isNotBlank()) {
                persistSegment(result.text.trim())
                partialText = ""
            }
        }
    }

    private fun persistSegment(text: String) {
        val repository = repository ?: return
        val sessionId = session?.id ?: return
        val startMs = segmentStartMs
        val endMs = startMs + samplesSinceSegmentStart * 1000L / MicrophoneSource.SAMPLE_RATE
        val segment = Segment(
            sessionId = sessionId,
            startMs = startMs,
            endMs = endMs,
            text = text,
        )
        segmentStartMs = endMs
        samplesSinceSegmentStart = 0L
        pendingSummaryText.append(text).append('\n')
        repository.insertSegment(segment)
        repository.segmentsOf(sessionId).lastOrNull()?.let { segments.add(it) }
    }
}
