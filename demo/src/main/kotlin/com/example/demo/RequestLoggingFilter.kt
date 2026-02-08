package com.example.demo

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.web.util.ContentCachingResponseWrapper
import java.util.*

@Component
class RequestLoggingFilter : OncePerRequestFilter() {

    private val interactionsLogger = LoggerFactory.getLogger("interactions")

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.requestURI.startsWith("/actuator")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val cachedRequest = ContentCachingRequestWrapper(request, 10_000)
        val cachedResponse = ContentCachingResponseWrapper(response)
        val requestId = request.getHeader("X-Request-Id") ?: UUID.randomUUID().toString()
        val tenantId = request.getHeader("X-Tenant-Id") ?: ""

        val start = System.currentTimeMillis()
        try {
            filterChain.doFilter(cachedRequest, cachedResponse)
        } finally {
            val took = System.currentTimeMillis() - start
            val requestBody = String(cachedRequest.contentAsByteArray, Charsets.UTF_8)
            val responseBody = String(cachedResponse.contentAsByteArray, Charsets.UTF_8)

            interactionsLogger.atInfo()
                .addKeyValue("requestId", requestId)
                .addKeyValue("tenantId", tenantId)
                .addKeyValue("request.method", request.method)
                .addKeyValue("request.uri", request.requestURI)
                .addKeyValue("request.body", requestBody)
                .addKeyValue("response.status", cachedResponse.status)
                .addKeyValue("response.body", responseBody)
                .addKeyValue("response.took", took)
                .log("[{}] {} -> {} {} ms", request.method, request.requestURI, cachedResponse.status, took)

            cachedResponse.copyBodyToResponse()
        }
    }
}
