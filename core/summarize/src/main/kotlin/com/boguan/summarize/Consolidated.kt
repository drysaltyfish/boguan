package com.boguan.summarize

import com.boguan.model.ActionItem
import com.boguan.model.SessionType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 结构整理里的一条决定（会议类型）。 */
data class ConsolidationDecision(val text: String, val status: String)

/**
 * 10 分钟结构整理的产物（ADR-0003「结构整理」层）。
 *
 * 输入是若干条 2 分钟微摘要，输出是合并去重后的阶段小结；
 * 课堂类型用 [points] / [questions]，会议类型用 [decisions] / [actionItems]。
 */
data class ConsolidatedPayload(
    val title: String,
    val summary: String,
    val points: List<String> = emptyList(),
    val questions: List<String> = emptyList(),
    val decisions: List<ConsolidationDecision> = emptyList(),
    val actionItems: List<ActionItem> = emptyList(),
) {
    /** 展示用的要点行；会议类型把决定与待办也折成行，便于单列渲染。 */
    fun displayLines(): List<String> {
        val out = mutableListOf<String>()
        out += points
        decisions.forEach { out += "决定：" + it.text + "（" + decisionLabel(it.status) + "）" }
        actionItems.forEach { item ->
            val who = item.owner?.takeIf { it.isNotBlank() } ?: "负责人未定"
            val deadline = item.deadline?.takeIf { it.isNotBlank() } ?: "截止未定"
            out += "待办：" + item.text + "（" + who + " / " + deadline + "）"
        }
        questions.forEach { out += "疑问：" + it }
        return out
    }

    companion object {
        /** 决策状态的中文标签（与 GLOSSARY 的会议决策状态一致）。 */
        fun decisionLabel(status: String): String = when (status) {
            "AGREED" -> "已达成"
            "PENDING" -> "待讨论"
            "DISPUTED" -> "有分歧"
            "SHELVED" -> "搁置"
            else -> status
        }
    }
}

/**
 * 结构整理 JSON → [ConsolidatedPayload]。解析失败抛异常，由调用层降级
 * （ADR-0003 L4：该段标记不可用，不产出半份小结）。
 */
object ConsolidatedParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(raw: String, type: SessionType): ConsolidatedPayload {
        val root = json.parseToJsonElement(raw).jsonObject
        val title = str(root, "title")
        val summary = str(root, "summary")
        return when (type) {
            SessionType.LECTURE -> ConsolidatedPayload(
                title = title,
                summary = summary,
                points = stringList(root, "points"),
                questions = stringList(root, "questions"),
            )
            SessionType.MEETING -> ConsolidatedPayload(
                title = title,
                summary = summary,
                decisions = array(root, "decisions").mapNotNull { e ->
                    val o = e as? JsonObject ?: return@mapNotNull null
                    ConsolidationDecision(text = str(o, "text"), status = str(o, "status"))
                },
                actionItems = array(root, "action_items").mapNotNull { e ->
                    val o = e as? JsonObject ?: return@mapNotNull null
                    ActionItem(
                        text = str(o, "text"),
                        owner = optStr(o, "owner"),
                        deadline = optStr(o, "deadline"),
                    )
                },
            )
        }
    }

    private fun array(root: JsonObject, key: String): List<JsonElement> =
        (root[key] as? JsonArray)?.toList() ?: emptyList()

    private fun stringList(root: JsonObject, key: String): List<String> =
        array(root, key).mapNotNull { it.jsonPrimitive.contentOrNull }

    private fun str(o: JsonObject, key: String): String = optStr(o, key) ?: ""

    private fun optStr(o: JsonObject, key: String): String? {
        val e = o[key] ?: return null
        val value = e.jsonPrimitive.contentOrNull
        return if (value == "null") null else value
    }
}
