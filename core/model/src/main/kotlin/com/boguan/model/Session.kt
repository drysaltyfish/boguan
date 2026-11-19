package com.boguan.model

import java.util.UUID

/** 一场记录的类型。决定抽取目标而不只是渲染方式（ADR-0004）。 */
enum class SessionType { LECTURE, MEETING }

/** 状态机（ADR-0005）：idle → recording ⇄ paused → finalizing → done */
enum class SessionState {
    IDLE, RECORDING, PAUSED, FINALIZING, DONE, FAILED;

    /** 允许的转移；非法转移由调用方处理为编程错误。 */
    fun canGoTo(next: SessionState): Boolean = when (this) {
        IDLE -> next == RECORDING
        RECORDING -> next == PAUSED || next == FINALIZING
        PAUSED -> next == RECORDING || next == FINALIZING
        FINALIZING -> next == DONE || next == FAILED
        DONE, FAILED -> false
    }
}

/** 一场记录的元信息（公共信封的 meta 部分）。 */
data class Session(
    val id: String = UUID.randomUUID().toString(),
    val type: SessionType = SessionType.LECTURE,
    var title: String = "",
    val startedAtMs: Long = System.currentTimeMillis(),
    var endedAtMs: Long? = null,
    var state: SessionState = SessionState.IDLE,
    val audioSource: String = "microphone",
    /** 本地音频文件路径（WAV）；为空表示尚未落盘。 */
    var audioPath: String? = null,
)

/**
 * 转写段。
 *
 * [text] 是实时稿（v0.1 的展示文本），[offlineText] 是会后二遍的离线稿。
 * 两份都保留：二遍稿不一定更好，分歧本身是「待确认」的信号（ADR-0010）。
 */
data class Segment(
    val id: Long = 0,
    val sessionId: String,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val confidence: Float = 0f,
    val isFinal: Boolean = false,
    val offlineText: String = "",
    /** 终稿 LLM 校正后的文本（ADR-0011）；为空表示没跑或没改动。 */
    val correctedText: String = "",
) {
    /** 展示用文本，由 [TranscriptMerge.displayText] 单点决定。 */
    val displayText: String get() = TranscriptMerge.displayText(text, offlineText, correctedText)

    /** 实时稿与二遍稿的相似度（0..1，越低越可疑）；没有二遍稿时为 1。 */
    val offlineSimilarity: Double
        get() = if (offlineText.isBlank()) 1.0 else TranscriptMerge.similarity(text, offlineText)

    /** 终稿（校正稿优先，其次离线稿）与实时稿分歧较大，建议回听确认。 */
    val finalGuess: String get() = correctedText.ifBlank { offlineText }

    /** 实时稿与终稿分歧较大，建议回听确认。 */
    val needsReview: Boolean get() = TranscriptMerge.needsReview(text, finalGuess)
}

/** 每 2 分钟的微摘要 / 每 10 分钟的结构整理。 */
data class MicroSummary(
    val id: Long = 0,
    val sessionId: String,
    val kind: Kind,
    val startMs: Long,
    val endMs: Long,
    val payloadJson: String,
    val degraded: Boolean = false,
) {
    enum class Kind { MICRO, CONSOLIDATED }
}

/** 会中问答记录，最终进入报告附录。 */
data class QaPair(
    val id: Long = 0,
    val sessionId: String,
    val askedAtMs: Long,
    val question: String,
    val answer: String,
    val anchorStartMs: Long? = null,
    val anchorEndMs: Long? = null,
)
