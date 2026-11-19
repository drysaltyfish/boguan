package com.boguan.summarize

import com.boguan.model.SessionType

/**
 * 10 分钟结构整理的一次调用（ADR-0003）。
 * 只负责组装请求与做最基本的形状校验；持久化与调度由上层负责。
 */
class ConsolidatedSummaryService(private val client: OpenAiCompatibleClient) {

    suspend fun consolidate(
        endpoint: LlmEndpoint,
        type: SessionType,
        previous: String,
        microSummaries: String,
    ): LlmResponse {
        val request = LlmRequest(
            purpose = LlmPurpose.CONSOLIDATION,
            systemPrompt = Prompts.consolidatedSystemPrompt(type),
            userContent = Prompts.consolidatedUserPrompt(previous, microSummaries),
            jsonSchema = Prompts.consolidatedSchema(type),
            maxOutputTokens = 1000,
            temperature = 0.2,
        )
        val response = client.complete(endpoint, request)
        if (!looksLikeJsonObject(response.content)) {
            throw LlmProtocolException("consolidation is not a JSON object: " + response.content.take(120))
        }
        return response
    }

    private fun looksLikeJsonObject(text: String): Boolean {
        val t = text.trim()
        return t.startsWith("{") && t.endsWith("}")
    }
}
