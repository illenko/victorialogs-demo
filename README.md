# VictoriaMetrics Observability Demo

A demo project showcasing how to use the full [VictoriaMetrics](https://victoriametrics.com/) stack — **VictoriaMetrics**, **VictoriaLogs**, and **VictoriaTraces** — with a Spring Boot application, leveraging their OpenTelemetry-compatible receivers for metrics, log, and trace ingestion.

## Overview

The project consists of:

- **Spring Boot application** (Kotlin) that simulates a payments API with synthetic traffic generation
- **VictoriaMetrics** — receives application metrics via the OTLP HTTP endpoint
- **VictoriaLogs** — receives structured application logs via the OTLP HTTP endpoint
- **VictoriaTraces** — receives distributed traces via the OTLP HTTP endpoint
- **Grafana** — pre-configured dashboard and datasources for visualizing metrics, logs, and traces

```
┌──────────────────────┐
│   Spring Boot App    │
│  (Payments API +     │
│   synthetic traffic) │
└──┬──────┬────────┬───┘
   │      │        │
   │ OTLP │ OTLP   │ OTLP
   │      │        │
   ▼      ▼        ▼
┌──────┐ ┌──────┐ ┌──────────┐
│  VM  │ │  VL  │ │   VT     │
│Metrics│ │ Logs │ │ Traces   │
│(:8428)│ │(:9428)│ │(:10428)  │
└──┬───┘ └──┬───┘ └────┬─────┘
   │        │          │
   └────────┼──────────┘
            ▼
      ┌──────────┐
      │ Grafana  │
      │ (:3000)  │
      └──────────┘
```

## Prerequisites

- Java 21+
- Docker & Docker Compose

## Getting Started

### 1. Start the infrastructure

```bash
cd env
docker compose up -d
```

This starts VictoriaMetrics, VictoriaLogs, VictoriaTraces, and Grafana with pre-provisioned datasources and dashboards.

### 2. Run the Spring Boot application

```bash
cd demo
./gradlew bootRun
```

The application starts on a random port and immediately begins generating synthetic API traffic (~5 requests/second).

### 3. Open Grafana

Navigate to [http://localhost:3000](http://localhost:3000) (login: `admin` / `admin`).

The pre-configured **Spring Boot Observability** dashboard includes:

| Row | Panels | Datasource | Description |
|---|---|---|---|
| Overview | Request Rate, Error Rate %, p95 Latency, Total Requests (1h) | VictoriaMetrics | Key SRE stats at a glance |
| RED Metrics | Request Rate, Error Rate, Latency (p50/p95/p99) | VictoriaMetrics | Rate, Errors, Duration timeseries per endpoint |
| Traces | Recent Traces | VictoriaTraces | Distributed traces for the `demo` service |
| Logs | Request Log | VictoriaLogs | Structured table of all API requests |

## How It Works

### Metrics pipeline

Spring Boot auto-instruments HTTP server requests via Micrometer and exports them using the OTLP protocol directly to VictoriaMetrics:

```
App → OTLP HTTP → VictoriaMetrics (http://localhost:8428/opentelemetry/v1/metrics)
```

Configured in `application.yaml`:

```yaml
management:
  otlp:
    metrics:
      export:
        url: http://localhost:8428/opentelemetry/v1/metrics
        step: 1s
```

Histogram percentiles (p50, p90, p95, p99) are enabled for `http.server.requests`.

### Logs pipeline

The application uses a Logback OpenTelemetry appender to export structured logs directly to VictoriaLogs:

```
App (Logback) → OpenTelemetryAppender → OTLP HTTP → VictoriaLogs (http://localhost:9428/insert/opentelemetry/v1/logs)
```

Configured in `application.yaml`:

```yaml
management:
  opentelemetry:
    logging:
      export:
        otlp:
          endpoint: http://localhost:9428/insert/opentelemetry/v1/logs
```

Logs are structured as key-value pairs (requestId, tenantId, method, URI, status, latency, request/response bodies) making them queryable in VictoriaLogs.

### Traces pipeline

Spring Boot auto-instruments HTTP server and client requests with OpenTelemetry and exports traces directly to VictoriaTraces:

```
App → OTLP HTTP → VictoriaTraces (http://localhost:10428/insert/opentelemetry/v1/traces)
```

Configured in `application.yaml`:

```yaml
management:
  opentelemetry:
    tracing:
      export:
        otlp:
          endpoint: http://localhost:10428/insert/opentelemetry/v1/traces
```

The scheduled traffic generator makes real HTTP calls via `RestClient`, which automatically creates client→server trace spans.

### No collector needed

VictoriaMetrics, VictoriaLogs, and VictoriaTraces all natively support OpenTelemetry OTLP HTTP receivers, so the application exports telemetry **directly** — no OpenTelemetry Collector is required.

## The Demo Application

The Spring Boot app exposes a simple payments API:

| Endpoint | Description |
|---|---|
| `GET /api/payments` | List payments (10% simulated failure rate) |
| `GET /api/payments/{id}` | Get payment by ID (20% not-found rate) |
| `POST /api/payments` | Create payment (20% failure rate) |

A scheduled task calls these endpoints every 200ms with random tenant IDs and request parameters, simulating realistic multi-tenant traffic with varying response codes and latencies.

## Project Structure

```
├── demo/                          # Spring Boot Kotlin application
│   ├── build.gradle.kts
│   └── src/main/
│       ├── kotlin/.../DemoApplication.kt
│       └── resources/
│           ├── application.yaml
│           └── logback-spring.xml
└── env/                           # Docker environment
    ├── docker-compose.yaml
    └── grafana/
        ├── dashboards/demo.json
        └── provisioning/
            ├── dashboards/dashboards.yml
            └── datasources/
                ├── victorialogs.yml
                ├── victoriametrics.yml
                └── victoriatraces.yml
```

## Tech Stack

- **Spring Boot 4.0** with `spring-boot-starter-opentelemetry`
- **Kotlin** / Java 21
- **VictoriaMetrics v1.134.0** — time-series database (Prometheus-compatible)
- **VictoriaLogs v1.44.0** — log management solution
- **VictoriaTraces** — distributed tracing backend (Jaeger-compatible)
- **Grafana 11.5.2** with `victoriametrics-logs-datasource` and `victoriametrics-metrics-datasource` plugins
- **OpenTelemetry** — OTLP protocol for metrics, logs, and traces export
