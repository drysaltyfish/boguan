package com.boguan.summarize

import com.boguan.model.ActionItem
import com.boguan.model.Emphasis
import com.boguan.model.KeyPoint
import com.boguan.model.Question
import com.boguan.model.ReportEnvelope
import com.boguan.model.ReportPayload
import com.boguan.model.SessionType
import com.boguan.model.Topic
import com.boguan.model.UnverifiedItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 把 LLM 返回的类型化 JSON 映射为 [ReportPayload]（ADR-0004 的类型载荷 → 统一信封）。
 * 解析失败应抛异常让上层降级，不要静默产出半份报告。
 */
object ReportParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(raw: String, type: SessionType): ReportPayload {
        val root = json.parseToJsonElement(raw).jsonObject
        return when (type) {
            SessionType.LECTURE -> parseLecture(root)
            SessionType.MEETING -> parseMeeting(root)
        }
    }

    private fun parseLecture(root: JsonObject): ReportPayload {
        val keyPoints = array(root, "key_points").mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            KeyPoint(
                title = str(o, "title"),
                detail = str(o, "detail"),
                status = when (str(o, "status")) {
                    "NEEDS_REVIEW" -> KeyPoint.ReviewStatus.NEEDS_REVIEW
                    "UNSURE" -> KeyPoint.ReviewStatus.UNSURE
                    else -> KeyPoint.ReviewStatus.CLEAR
                },
            )
        }
        val assignments = array(root, "assignments").mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            ActionItem(text = str(o, "text"), deadline = optStr(o, "deadline"))
        }
        val emphasis = array(root, "emphasis").mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            Emphasis(text = str(o, "text"), kind = emphasisKind(str(o, "kind")))
        }
        val questions = array(root, "questions").mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            Question(text = str(o, "text"), answer = optStr(o, "answer"))
        }
        val topics = array(root, "topics").mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            Topic(title = str(o, "title"), startMs = 0, endMs = 0)
        }
        return ReportPayload(
            envelope = ReportEnvelope(
                oneLiner = str(root, "one_liner"),
                keyPoints = keyPoints,
                actionItems = assignments,
                emphasis = emphasis,
                questions = questions,
                topics = topics,
            ),
            unverified = parseUnverified(root),
        )
    }

    private fun parseMeeting(root: JsonObject): ReportPayload {
        val decisions = array(root, "decisions").mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val status = when (str(o, "status")) {
                "AGREED" -> "已达成"
                "PENDING" -> "待讨论"
                "DISPUTED" -> "有分歧"
                "SHELVED" -> "搁置"
                else -> str(o, "status")
            }
            Emphasis(text = str(o, "text") + "（" + status + "）", kind = Emphasis.Kind.DECISION)
        }
        val risks = array(root, "risks").mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            Emphasis(text = str(o, "text"), kind = Emphasis.Kind.RISK)
        }
        val actions = array(root, "action_items").mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            ActionItem(
                text = str(o, "text"),
                owner = optStr(o, "owner"),
                deadline = optStr(o, "deadline"),
            )
        }
        val questions = array(root, "open_questions").mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            Question(text = str(o, "text"))
        }
        val topics = array(root, "topics").mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            Topic(title = str(o, "title"), startMs = 0, endMs = 0)
        }
        return ReportPayload(
            envelope = ReportEnvelope(
                oneLiner = str(root, "one_liner"),
                emphasis = decisions + risks,
                actionItems = actions,
                questions = questions,
                topics = topics,
            ),
            unverified = parseUnverified(root),
        )
    }

    /** 待确认清单：LLM 报告的「可能听错」条目（ADR-0004）。 */
    private fun parseUnverified(root: JsonObject): List<UnverifiedItem> =
        array(root, "unverified").mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val text = str(o, "text")
            if (text.isBlank()) return@mapNotNull null
            UnverifiedItem(text = text, reason = str(o, "reason"))
        }

    private fun emphasisKind(raw: String): Emphasis.Kind = when (raw) {
        "EXAM" -> Emphasis.Kind.EXAM
        "PITFALL" -> Emphasis.Kind.PITFALL
        "DECISION" -> Emphasis.Kind.DECISION
        "RISK" -> Emphasis.Kind.RISK
        else -> Emphasis.Kind.KEY
    }

    private fun array(o: JsonObject, key: String): List<kotlinx.serialization.json.JsonElement> =
        (o[key] as? JsonArray)?.toList() ?: emptyList()

    private fun str(o: JsonObject, key: String): String =
        o[key]?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun optStr(o: JsonObject, key: String): String? {
        val v = o[key]?.jsonPrimitive?.contentOrNull ?: return null
        return if (v.isBlank() || v == "null") null else v
    }
}
