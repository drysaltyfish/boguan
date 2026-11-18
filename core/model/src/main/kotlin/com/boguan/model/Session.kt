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
)

/** 转写段。isFinal=false 为实时稿，true 为终稿。 */
data class Segment(
    val id: Long = 0,
    val sessionId: String,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val confidence: Float = 0f,
    val isFinal: Boolean = false,
)

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
