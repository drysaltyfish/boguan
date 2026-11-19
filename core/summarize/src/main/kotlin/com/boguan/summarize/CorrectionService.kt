package com.boguan.summarize

import com.boguan.model.SessionType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 一段被校正后的文本。 */
data class CorrectedSegment(val index: Int, val text: String)

/**
 * 终稿 LLM 校正（ADR-0011）：把「实时稿 + 离线稿」两份额外识别结果交给 LLM，
 * 结合上下文与术语表输出一份校正文本。
 *
 * 与 ASR 的区别：ASR 只能听音，纠错靠语言模型的世界知识 + 上下文，
 * 能修掉「女子是 → 余子式」这类同音错，但**不能凭空产生音频里没有的内容**。
 */
class CorrectionService(private val client: OpenAiCompatibleClient) {

    suspend fun correct(
        endpoint: LlmEndpoint,
        type: SessionType,
        glossary: String,
        contextBefore: String,
        items: String,
        contextAfter: String,
    ): LlmResponse {
        val request = LlmRequest(
            purpose = LlmPurpose.TRANSCRIPT_CORRECTION,
            systemPrompt = Prompts.correctionSystemPrompt(type, glossary),
            userContent = Prompts.correctionUserPrompt(contextBefore, items, contextAfter),
            jsonSchema = Prompts.correctionSchema(),
            maxOutputTokens = 2_000,
            temperature = 0.1,
        )
        val response = client.complete(endpoint, request)
        CorrectionParser.parse(response.content)
        return response
    }
}

/** 校正结果 JSON → 段落列表。解析失败抛异常，上层按「本批不校正」降级处理。 */
object CorrectionParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(raw: String): List<CorrectedSegment> {
        val root = json.parseToJsonElement(raw).jsonObject
        val segments = root["segments"] as? JsonArray
            ?: throw LlmProtocolException("correction payload has no segments array")
        return segments.mapNotNull { element -> toSegment(element) }
            .filter { it.text.isNotBlank() }
    }

    private fun toSegment(element: JsonElement): CorrectedSegment? {
        val obj = element as? JsonObject ?: return null
        val index = obj["i"]?.jsonPrimitive?.intOrNull ?: return null
        val text = obj["text"]?.jsonPrimitive?.contentOrNull?.trim() ?: return null
        return CorrectedSegment(index, text)
    }
}
