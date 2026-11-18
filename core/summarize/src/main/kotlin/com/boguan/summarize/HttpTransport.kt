package com.boguan.summarize

/**
 * 最小 HTTP 边界：便于单测时替换成假实现，不引入 mock 服务器。
 */
interface HttpTransport {
    /**
     * @return HTTP 状态码与响应体
     */
    suspend fun post(url: String, headers: Map<String, String>, jsonBody: String): HttpResult
}

data class HttpResult(val statusCode: Int, val body: String)
