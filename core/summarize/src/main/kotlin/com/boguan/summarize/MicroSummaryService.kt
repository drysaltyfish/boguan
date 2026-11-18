package com.boguan.summarize

import com.boguan.model.SessionType

/**
 * 2 分钟微摘要的一次调用（ADR-0003）。
 * 只负责组装请求与做最基本的形状校验；持久化与调度由上层负责。
 */
class MicroSummaryService(private val client: OpenAiCompatibleClient) {

    suspend fun summarize(
        endpoint: LlmEndpoint,
        type: SessionType,
        previousSummary: String,
        windowText: String,
    ): LlmResponse {
        val request = LlmRequest(
            purpose = LlmPurpose.MICRO_SUMMARY,
            systemPrompt = Prompts.microSummarySystemPrompt(type),
            userContent = Prompts.microSummaryUserPrompt(previousSummary, windowText),
            jsonSchema = Prompts.microSummarySchema(type),
            maxOutputTokens = 800,
            temperature = 0.2,
        )
        val response = client.complete(endpoint, request)
        if (!looksLikeJsonObject(response.content)) {
            throw LlmProtocolException("micro summary is not a JSON object: " + response.content.take(120))
        }
        return response
    }

    private fun looksLikeJsonObject(text: String): Boolean {
        val t = text.trim()
        return t.startsWith("{") && t.endsWith("}")
    }
}
