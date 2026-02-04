package com.example.demo

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.InitializingBean
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.*
import kotlin.random.Random


@SpringBootApplication
@EnableScheduling
class DemoApplication

@Component
internal class InstallOpenTelemetryAppender(private val openTelemetry: OpenTelemetry?) : InitializingBean {
    override fun afterPropertiesSet() {
        OpenTelemetryAppender.install(this.openTelemetry)
    }
}

fun main(args: Array<String>) {
    runApplication<DemoApplication>(*args)
}

@Component
class ScheduledLogger {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val httpMethods = listOf("GET", "POST", "PUT", "DELETE")
    private val uris = listOf("/api/users", "/api/products", "/api/orders", "/api/payments")
    private val responseStatuses = listOf(200, 201, 204, 400, 401, 404, 500)

    @Scheduled(fixedRate = 1000)
    fun imitateRequestLogging() {
        val paymentId = UUID.randomUUID().toString()
        val method = httpMethods[Random.nextInt(httpMethods.size)]
        val uri = uris[Random.nextInt(uris.size)]
        val requestBody = "{\"data\":\"value_${UUID.randomUUID().toString().substring(0, 4)}\"}"
        val responseStatus = responseStatuses[Random.nextInt(responseStatuses.size)]
        val responseBody = "{\"result\":\"success_${UUID.randomUUID().toString().substring(0, 4)}\"}"

        logger.atInfo()
            .addKeyValue("idempotencyId", paymentId)
            .addKeyValue("method", method)
            .addKeyValue("uri", uri)
            .addKeyValue("requestBody", requestBody)
            .addKeyValue("responseBody", responseBody)
            .addKeyValue("responseStatus", responseStatus).log("Processed request")

        MDC.clear()
    }
}
