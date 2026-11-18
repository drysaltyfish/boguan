package com.boguan.summarize

import com.boguan.model.SessionType

/** 终稿报告抽取的一次调用（ADR-0004）。 */
class ReportService(private val client: OpenAiCompatibleClient) {

    suspend fun generate(
        endpoint: LlmEndpoint,
        type: SessionType,
        transcript: String,
        summaries: String,
    ): LlmResponse = client.complete(
        endpoint,
        LlmRequest(
            purpose = LlmPurpose.REPORT_EXTRACTION,
            systemPrompt = Prompts.reportSystemPrompt(type),
            userContent = Prompts.reportUserPrompt(transcript, summaries),
            jsonSchema = Prompts.reportSchema(type),
            maxOutputTokens = 2_500,
            temperature = 0.3,
        ),
    )
}
