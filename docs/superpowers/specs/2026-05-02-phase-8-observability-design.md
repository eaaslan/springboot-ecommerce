# Phase 8 — Observability Design (Prometheus + Grafana + Zipkin)

## 1. Goal

Make every microservice observable across the **three pillars** — metrics, traces, logs — with a local stack that mirrors production tooling. Emphasis on **RED method** (Rate, Errors, Duration) per service, plus a small set of business metrics (orders placed, outbox published, notifications sent).

## 2. Stack

| Component | Image | Port | Role |
|---|---|---:|---|
| Prometheus | `prom/prometheus:v2.55` | 9090 | Pull-based metrics scrape (15s) |
| Grafana | `grafana/grafana:11.3.0` | 3000 | Dashboards on Prometheus + Zipkin |
| Zipkin | `openzipkin/zipkin:3` | 9411 | Distributed traces (OTLP / Zipkin v2) |

All wired via docker-compose. Grafana provisioned with Prometheus + Zipkin datasources and one default dashboard.

## 3. Application Side

### Metrics
- `spring-boot-starter-actuator` (already in pom) → `/actuator/prometheus`
- `micrometer-registry-prometheus` (NEW) → exposes Prometheus format
- HTTP `http_server_requests_seconds_count|sum|max` auto-published per endpoint, status, method
- JVM: `jvm_memory_used_bytes`, `jvm_threads_live`, GC counts (auto)
- Custom counters/timers on hot paths (P8.T5)

### Tracing
- `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-zipkin` (NEW)
- Sampling: 1.0 in dev (every span exported), 0.1 in prod (TODO note in config)
- Auto-instruments: HTTP server, RestTemplate / Feign clients, JDBC, Kafka, RabbitMQ
- W3C `traceparent` header propagated through gateway → services

### Correlation
- Existing `CorrelationIdFilter` continues to inject MDC `traceId` for log search
- Once tracing is wired, Micrometer overrides the MDC value with the actual span trace id — that ID matches what Zipkin shows
- Logback pattern adds `[%X{traceId},%X{spanId}]` so log → trace navigation is one copy-paste

## 4. Default Configuration (config-server `application.yml`)

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,refresh,prometheus
  endpoint:
    health:
      show-details: when-authorized
    prometheus:
      enabled: true
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true   # required for histogram_quantile
      percentiles:
        http.server.requests: 0.5,0.95,0.99
    tags:
      application: ${spring.application.name}
  tracing:
    sampling:
      probability: 1.0
  zipkin:
    tracing:
      endpoint: ${ZIPKIN_ENDPOINT:http://localhost:9411/api/v2/spans}

logging:
  pattern:
    level: "%5p [${spring.application.name},%X{traceId:-},%X{spanId:-}]"
```

Reactive (api-gateway) gets the same pattern; the gateway already is the entry point so its trace IDs become the chain root.

## 5. Prometheus Scrape Config

```yaml
# docker/prometheus/prometheus.yml
global:
  scrape_interval: 15s
scrape_configs:
  - job_name: 'spring-services'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets:
          - 'host.docker.internal:8081'   # user-service
          - 'host.docker.internal:8082'   # product-service
          - 'host.docker.internal:8083'   # cart-service
          - 'host.docker.internal:8084'   # inventory-service
          - 'host.docker.internal:8085'   # payment-service
          - 'host.docker.internal:8086'   # order-service
          - 'host.docker.internal:8087'   # notification-service
          - 'host.docker.internal:8080'   # api-gateway
          - 'host.docker.internal:8888'   # config-server
          - 'host.docker.internal:8761'   # discovery-server
```

Production would use Eureka SD (Spring Cloud) or service-discovery via DNS / Consul. Static targets are fine for a local stack.

## 6. Grafana Provisioning

```
docker/grafana/
├── datasources.yml         # Prometheus + Zipkin
└── dashboards/
    ├── dashboards.yml      # provisioning provider
    └── microservices-overview.json   # the panels
```

Default dashboard panels:
1. **HTTP RPS by service** — `sum(rate(http_server_requests_seconds_count[1m])) by (application)`
2. **HTTP p95 latency by service** — `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, application))`
3. **Error rate by service** — `sum(rate(http_server_requests_seconds_count{status=~"5..|4.."}[1m])) by (application)`
4. **JVM heap usage** — `jvm_memory_used_bytes{area="heap"}` per app
5. **Business: orders placed** — `rate(orders_placed_total[1m])`
6. **Business: outbox published vs failed** — two timeseries side by side
7. **Business: notifications sent vs duplicate** — counters

## 7. Custom Metrics

| Metric | Type | Labels | Where |
|---|---|---|---|
| `orders.placed` | Counter | `currency` | OrderService.placeOrder happy path |
| `orders.cancelled` | Counter | `reason=reservation\|payment\|commit\|unexpected` | markCancelled |
| `orders.placeOrder.duration` | Timer | (none) | @Timed on placeOrder |
| `outbox.published` | Counter | `eventType` | OutboxRelay success branch |
| `outbox.failed` | Counter | `eventType` | OutboxRelay catch branch |
| `notifications.sent` | Counter | `channel` | NotificationService SENT path |
| `notifications.duplicate` | Counter | (none) | NotificationService dup path |

Naming follows Micrometer convention (snake_case in Prometheus output).

## 8. Out-of-Scope

| Item | Why deferred |
|---|---|
| Loki for logs | One-too-many for a learning project; structured JSON via `logstash-encoder` already exists; Phase 11 maybe |
| Alertmanager | Setup-heavy; document the rules but don't run |
| OTLP collector | Direct zipkin exporter is simpler; collector is production-grade |
| RED dashboards per endpoint | Service-level only |
| Kubernetes ServiceMonitor | Phase 12 (deployment) |

## 9. Interview Talking Points

1. **Three pillars** — metrics (aggregate), traces (causal), logs (granular). When to reach for which.
2. **RED vs USE method** — RED for request-driven (rate/errors/duration), USE for resources (utilization/saturation/errors).
3. **Pull (Prometheus) vs Push (StatsD)** — Prometheus pulls, easier service discovery, downside: short-lived jobs need pushgateway.
4. **Histograms vs summaries** — histograms aggregate across instances cleanly via `histogram_quantile`; summaries don't.
5. **Sampling strategy** — head sampling (decide upfront, simple), tail sampling (keep all errors, downsample success). Bizim: probability=1.0 dev, 0.1 prod.
6. **Trace context propagation** — W3C `traceparent`, B3, Jaeger. Spring Cloud uses W3C by default in 3.x.
7. **Cardinality bombs** — high-cardinality labels (user_id, request_id) explode time-series count. Avoid in metrics; use traces/logs for that detail.

## 10. Acceptance Criteria

1. `docker compose up -d` brings up prometheus, grafana, zipkin healthy.
2. `curl http://localhost:<service-port>/actuator/prometheus` returns metrics text.
3. Prometheus targets page (http://localhost:9090/targets) shows all running services UP.
4. Grafana (http://localhost:3000, admin/admin) loads with provisioned dashboard.
5. End-to-end trace visible in Zipkin (http://localhost:9411) when placing an order: gateway → order-service → cart/inventory/payment Feign + Kafka send.
6. Custom counters appear in Prometheus search (`orders_placed_total` etc).
7. `mvn clean verify` still green for all 12 modules.
8. Tag `phase-8-complete` pushed.
