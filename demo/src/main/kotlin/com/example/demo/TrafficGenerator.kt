package com.example.demo

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.toEntity
import java.util.*

@Component
class TrafficGenerator(private val restClient: RestClient) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val tenants = listOf("tenant1", "tenant2", "tenant3")

    private data class EndpointAction(val method: String, val uri: String, val hasBody: Boolean = false)

    private val endpoints = listOf(
        EndpointAction("GET", "/api/payments/{id}"),
        EndpointAction("POST", "/api/payments", true),
    )

    @Scheduled(fixedRate = 200)
    fun executeRandomRequest() {
        val action = endpoints.random()
        val requestId = UUID.randomUUID().toString()
        val uri = action.uri.replace("{id}", requestId)
        val tenantId = tenants.random()
        val requestBody = if (action.hasBody) mapOf("data" to "value_${requestId.substring(0, 4)}") else null

        try {
            when (action.method) {
                "GET" -> restClient.get().uri(uri)
                    .header("X-Tenant-Id", tenantId)
                    .header("X-Request-Id", requestId)

                "POST" -> restClient.post().uri(uri)
                    .header("X-Tenant-Id", tenantId)
                    .header("X-Request-Id", requestId)
                    .header("Content-Type", "application/json")
                    .body(requestBody ?: emptyMap<String, String>())

                else -> return
            }.retrieve().toEntity<String>()
        } catch (e: Exception) {
            log.debug("Request failed: {} {} — {}", action.method, uri, e.message)
        }
    }
}
