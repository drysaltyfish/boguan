package com.boguan.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boguan.app.recording.RecordingController
import com.boguan.app.recording.RecordingService
import com.boguan.model.MicroSummary
import com.boguan.model.Segment
import com.boguan.model.Session
import com.boguan.model.SessionState
import com.boguan.model.SessionType
import com.boguan.store.BoguanDatabase
import com.boguan.app.settings.AppSettings
import com.boguan.report.ReportRenderer
import com.boguan.store.SessionRepository
import com.boguan.summarize.ConsolidatedParser
import com.boguan.summarize.OpenAiCompatibleClient
import com.boguan.summarize.ReportParser
import com.boguan.summarize.ReportService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

    /** 历史场次里的阶段小结（已从库里的 JSON 解析出来，供直接渲染）。 */
    var openedConsolidations by mutableStateOf<List<ConsolidationItem>>(emptyList())
        private set

    val playingSegmentId: Long? get() = player.playingSegmentId

    // 注意：必须放在所有 mutableStateOf 属性之后 —— Kotlin 的 init 块按源码顺序执行，
    // 过早执行会读到尚未初始化的委托属性（曾导致启动 NPE）。
    init {
        controller.ensureInit(app)
        refreshHistory()
    }

    // ---- 设置 ----
    val baseUrl: String get() = settings.baseUrl
    val apiKey: String get() = settings.apiKey
    val model: String get() = settings.model
    val glossary: String get() = settings.glossary
    val correctionEnabled: Boolean get() = settings.correctionEnabled

    fun saveSettings(
        baseUrl: String,
        apiKey: String,
        model: String,
        glossary: String = this.glossary,
        correctionEnabled: Boolean = this.correctionEnabled,
    ) = settings.save(baseUrl, apiKey, model, glossary, correctionEnabled)

    // ---- 摘要状态 ----
    val summaryTitle: String get() = controller.summaryTitle
    val summaryBullets: List<String> get() = controller.summaryBullets
    val summaryStatus: String get() = controller.summaryStatus

    /** 终稿（离线二遍 + LLM 校正）进度，直播页与历史页共用。 */
    val finalPassStatus: String get() = controller.finalPassStatus

    /** 录音中滚动校正的进度。 */
    val correctedStatus: String get() = controller.correctedStatus

    // ---- 结构整理状态（每 10 分钟，ADR-0003） ----
    val consolidatedTitle: String get() = controller.consolidatedTitle
    val consolidatedLines: List<String> get() = controller.consolidatedLines
    val consolidatedStatus: String get() = controller.consolidatedStatus

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

    /** 重跑某场历史记录的终稿（不动实时稿）。 */
    fun reprocessSession(session: Session) {
        controller.reprocessSession(session.id)
    }

    /** 重新读取当前打开场次的段落（终稿跑完后刷新展示）。 */
    fun refreshOpenedSession() {
        val session = openedSession ?: return
        openedSegments = runCatching { historyRepository.segmentsOf(session.id) }.getOrDefault(emptyList())
        reportText = runCatching { historyRepository.latestReport(session.id) }.getOrNull().orEmpty()
    }

    fun refreshHistory() {
        history = runCatching { historyRepository.listSessions() }.getOrDefault(emptyList())
    }

    fun openSession(session: Session) {
        player.stop()
        openedSession = session
        openedSegments = runCatching { historyRepository.segmentsOf(session.id) }.getOrDefault(emptyList())
        openedConsolidations = runCatching {
            historyRepository.microSummariesOf(session.id)
                .filter { it.kind == MicroSummary.Kind.CONSOLIDATED }
                .mapNotNull { summary ->
                    val payload = runCatching { ConsolidatedParser.parse(summary.payloadJson, session.type) }
                        .getOrNull() ?: return@mapNotNull null
                    ConsolidationItem(
                        title = payload.title,
                        lines = payload.displayLines(),
                        startMs = summary.startMs,
                        endMs = summary.endMs,
                    )
                }
        }.getOrDefault(emptyList())
    }

    fun closeSession() {
        player.stop()
        openedSession = null
        openedSegments = emptyList()
        openedConsolidations = emptyList()
    }

    // ---- 报告 ----
    var reportText by mutableStateOf("")
        private set
    var reportStatus by mutableStateOf("")
        private set

    fun openSessionWithReport(session: Session) {
        openSession(session)
        reportText = runCatching { historyRepository.latestReport(session.id) }.getOrNull().orEmpty()
        reportStatus = if (reportText.isBlank()) "" else "已加载上次生成的报告"
    }

    fun generateReport(session: Session) {
        val endpoint = settings.endpointOrNull()
        if (endpoint == null) {
            reportStatus = "未配置 API key，无法生成报告"
            return
        }
        if (reportStatus.startsWith("生成中")) return
        reportStatus = "生成中（LLM 抽取）…"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val segments = historyRepository.segmentsOf(session.id)
                // 报告用展示文本：有 LLM 校正稿时用校正稿（ADR-0011）
                val transcript = segments.joinToString("\n") { it.displayText }
                if (transcript.isBlank()) {
                    reportStatus = "本场没有转写内容"
                    return@launch
                }
                val summaries = historyRepository.microSummariesOf(session.id)
                    .joinToString("\n") { it.payloadJson }
                val response = ReportService(OpenAiCompatibleClient())
                    .generate(endpoint, session.type, transcript, summaries)
                val payload = ReportParser.parse(response.content, session.type)
                val markdown = ReportRenderer.render(session, payload, transcript)
                historyRepository.insertReport(session.id, session.type, response.content, markdown)
                reportText = markdown
                reportStatus = "报告已生成（" + response.inputTokens + "→" + response.outputTokens + " tokens）"
            } catch (t: Throwable) {
                reportStatus = "生成失败：" + (t.message ?: t.javaClass.simpleName)
            }
        }
    }

    fun togglePlayback(segment: Segment, audioPath: String?) {
        val path = audioPath ?: controller.currentAudioPath ?: return
        player.toggle(File(path), segment.id, segment.startMs, segment.endMs)
    }

    /** 历史详情里的阶段小结视图。 */
    data class ConsolidationItem(
        val title: String,
        val lines: List<String>,
        val startMs: Long,
        val endMs: Long,
    )

    override fun onCleared() {
        super.onCleared()
        player.stop()
        // 录音由前台服务持有，Activity 销毁不影响
    }
}
