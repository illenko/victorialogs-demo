package com.example.demo

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@SpringBootApplication
@EnableScheduling
class DemoApplication {
    @Bean
    fun restClient(@Value("\${server.port:8080}") port: Int): RestClient =
        RestClient.create("http://localhost:$port")

    @Bean
    fun tracer(openTelemetry: OpenTelemetry): Tracer =
        openTelemetry.getTracer("demo-app")
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
