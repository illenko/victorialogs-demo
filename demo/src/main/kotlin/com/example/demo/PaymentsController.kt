package com.example.demo

import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*
import kotlin.random.Random

@RestController
@RequestMapping("/api/payments")
internal class PaymentsController(private val tracer: Tracer) {

    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/{id}")
    fun get(@PathVariable id: String): ResponseEntity<Map<String, Any>> {
        val lookupSpan = tracer.spanBuilder("payment.lookup").startSpan()
        try {
            lookupSpan.makeCurrent().use {
                lookupSpan.setAttribute("payment.id", id)
                randomDelay()
            }
        } finally {
            lookupSpan.end()
        }

        if (Random.nextInt(10) < 2) {
            log.warn("Payment not found: {}", id)
            return ResponseEntity.notFound().build()
        }

        val enrichSpan = tracer.spanBuilder("payment.enrich").startSpan()
        try {
            enrichSpan.makeCurrent().use {
                randomDelay()
                enrichSpan.setAttribute("payment.status", "SETTLED")
            }
        } finally {
            enrichSpan.end()
        }

        log.info("Fetched payment {}", id)
        return ResponseEntity.ok(mapOf("id" to id, "amount" to Random.nextDouble(1000.0), "status" to "SETTLED"))
    }

    @PostMapping
    fun create(@RequestBody(required = false) body: Map<String, Any>?): ResponseEntity<Map<String, String>> {
        val validateSpan = tracer.spanBuilder("payment.validate").startSpan()
        try {
            validateSpan.makeCurrent().use {
                randomDelay()
                validateSpan.setAttribute("payment.hasBody", body != null)
            }
        } finally {
            validateSpan.end()
        }

        val processSpan = tracer.spanBuilder("payment.process").startSpan()
        try {
            processSpan.makeCurrent().use {
                randomDelay()
                if (Random.nextInt(10) < 2) {
                    processSpan.setStatus(StatusCode.ERROR, "processor_error")
                    log.error("Payment processing failed")
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(mapOf("error" to "processor_error"))
                }
            }
        } finally {
            processSpan.end()
        }

        val id = UUID.randomUUID().toString()
        log.info("Processed payment {}", id)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("id" to id))
    }
}

private fun randomDelay() {
    Thread.sleep(Random.nextLong(5, 150))
}
