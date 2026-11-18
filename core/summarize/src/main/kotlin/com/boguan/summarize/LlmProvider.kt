package com.boguan.summarize

/**
 * LLM 供应边界（ADR-0001 / ADR-0003）。
 * 协议层只认 OpenAI 兼容接口；具体 provider 由 base_url 决定，模型名不硬编码。
 */
data class LlmEndpoint(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
)

/** 微摘要 / 结构整理 / 终稿抽取 / 会中问答 四类调用。 */
enum class LlmPurpose { MICRO_SUMMARY, CONSOLIDATION, REPORT_EXTRACTION, QA }

data class LlmRequest(
    val purpose: LlmPurpose,
    val systemPrompt: String,
    val userContent: String,
    val jsonSchema: String? = null,
    val maxOutputTokens: Int = 1_200,
    val temperature: Double = 0.2,
)

data class LlmResponse(
    val content: String,
    val requestId: String? = null,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
)

interface LlmProvider {
    suspend fun complete(endpoint: LlmEndpoint, request: LlmRequest): LlmResponse
}
