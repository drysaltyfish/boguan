package com.boguan.model

/**
 * 报告 = 公共信封 + 类型载荷（ADR-0004）。
 * 报告是「投影」：只从结构化数据渲染，不做就地编辑。
 */

/** 公共信封：与类型无关。 */
data class ReportEnvelope(
    val oneLiner: String = "",
    val topics: List<Topic> = emptyList(),
    val actionItems: List<ActionItem> = emptyList(),
    val questions: List<Question> = emptyList(),
    val emphasis: List<Emphasis> = emptyList(),
    val keyPoints: List<KeyPoint> = emptyList(),
)

data class Topic(val title: String, val startMs: Long, val endMs: Long)

/** 时间锚点：指向原文时间段，用于回听。 */
data class Anchor(val startMs: Long, val endMs: Long)

data class ActionItem(
    val text: String,
    val owner: String? = null,
    val deadline: String? = null,
    val anchor: Anchor? = null,
)

data class Question(val text: String, val answer: String? = null, val anchor: Anchor? = null)

data class Emphasis(val text: String, val kind: Kind) {
    enum class Kind { EXAM, KEY, PITFALL, DECISION, RISK }
}

/** 课堂知识点 / 会议讨论要点。 */
data class KeyPoint(
    val title: String,
    val detail: String,
    val status: ReviewStatus = ReviewStatus.CLEAR,
    val anchor: Anchor? = null,
    val lowConfidence: Boolean = false,
) {
    enum class ReviewStatus { CLEAR, NEEDS_REVIEW, UNSURE }
}

/** 低置信度实体 -> 待确认清单。 */
data class UnverifiedItem(val text: String, val reason: String, val anchor: Anchor? = null)

data class ReportPayload(
    val envelope: ReportEnvelope = ReportEnvelope(),
    val unverified: List<UnverifiedItem> = emptyList(),
    val qaPairs: List<QaPair> = emptyList(),
)
