package com.example.demo

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.*
import org.springframework.web.client.RestClient
import org.springframework.web.client.toEntity
import java.util.*
import kotlin.random.Random

@SpringBootApplication
@EnableScheduling
class DemoApplication {
    @Bean
    fun restClient(@Value("\${server.port:8080}") port: Int): RestClient =
        RestClient.create("http://localhost:$port")
}

@Component
internal class InstallOpenTelemetryAppender(private val openTelemetry: OpenTelemetry?) : InitializingBean {
    override fun afterPropertiesSet() {
        OpenTelemetryAppender.install(this.openTelemetry)
    }
}

fun main(args: Array<String>) {
    runApplication<DemoApplication>(*args)
}

@RestController
@RequestMapping("/api/payments")
internal class PaymentsController {

    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun list(): ResponseEntity<List<Map<String, Any>>> {
        randomDelay()
        if (randomFail()) return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        log.info("Listing payments")
        return ResponseEntity.ok(
            listOf(mapOf("id" to UUID.randomUUID().toString(), "amount" to 99.99, "status" to "SETTLED"))
        )
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: String): ResponseEntity<Map<String, Any>> {
        randomDelay()
        if (Random.nextInt(10) < 2) {
            log.warn("Payment not found: {}", id)
            return ResponseEntity.notFound().build()
        }
        log.info("Fetched payment {}", id)
        return ResponseEntity.ok(mapOf("id" to id, "amount" to Random.nextDouble(1000.0), "status" to "SETTLED"))
    }

    @PostMapping
    fun create(@RequestBody(required = false) body: Map<String, Any>?): ResponseEntity<Map<String, String>> {
        randomDelay()
        if (Random.nextInt(10) < 2) {
            log.error("Payment processing failed")
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf("error" to "processor_error"))
        }
        val id = UUID.randomUUID().toString()
        log.info("Processed payment {}", id)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("id" to id))
    }
}

private fun randomDelay() {
    Thread.sleep(Random.nextLong(5, 150))
}

private fun randomFail(): Boolean = Random.nextInt(10) < 1

@Component
class ScheduledLogger(private val restClient: RestClient) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val serviceLogger = LoggerFactory.getLogger("api-gateway")
    private val interactionsLogger = LoggerFactory.getLogger("interactions")

    private val tenants = listOf("tenant1", "tenant2", "tenant3")

    private data class EndpointAction(val method: String, val uri: String, val hasBody: Boolean = false)

    private val endpoints = listOf(
        EndpointAction("GET", "/api/payments"),
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

        val start = System.currentTimeMillis()
        var status = 0
        var responseBody = ""
        try {
            val spec = when (action.method) {
                "GET" -> restClient.get().uri(uri)
                    .header("X-Tenant-Id", tenantId)

                "POST" -> restClient.post().uri(uri)
                    .header("X-Tenant-Id", tenantId)
                    .header("Content-Type", "application/json")
                    .body(requestBody ?: emptyMap<String, String>())

                "PUT" -> restClient.put().uri(uri)
                    .header("X-Tenant-Id", tenantId)
                    .header("Content-Type", "application/json")
                    .body(requestBody ?: emptyMap<String, String>())

                else -> return
            }
            val response = spec.retrieve().toEntity<String>()
            status = response.statusCode.value()
            responseBody = response.body ?: ""
        } catch (e: Exception) {
            status = 500
            responseBody = """{"error":"${e.message}"}"""
            log.debug("Request failed: {} {} — {}", action.method, uri, e.message)
        }
        val took = System.currentTimeMillis() - start

        serviceLogger.info("[{}] {} -> {} {} ms", action.method, uri, status, took)
        interactionsLogger.atInfo()
            .addKeyValue("requestId", requestId)
            .addKeyValue("tenantId", tenantId)
            .addKeyValue("request.method", action.method)
            .addKeyValue("request.url", action.uri)
            .addKeyValue("request.uri", uri)
            .addKeyValue("request.body", requestBody?.toString() ?: "")
            .addKeyValue("response.body", responseBody)
            .addKeyValue("response.status", status)
            .addKeyValue("response.took", took)
            .log("[{}] {} -> {} {} ms", action.method, uri, status, took)
    }
}
