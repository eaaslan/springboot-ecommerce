# Phase 8 — Observability Implementation Plan

**Goal:** Wire metrics (Prometheus), traces (Zipkin via OTel), and unified Grafana dashboards across the 12-module reactor.

**Architecture:** App side adds `micrometer-registry-prometheus` + `micrometer-tracing-bridge-otel` + zipkin exporter; config-server defaults turn on `/actuator/prometheus`, percentile histograms, sampling, zipkin endpoint, traceId log pattern. Docker-compose adds prometheus/grafana/zipkin with provisioned datasources + dashboard. Custom counters/timers on hot business paths.

**Tech Stack:** Spring Boot 3.4 actuator + Micrometer 1.13, OpenTelemetry 1.x, Prometheus 2.55, Grafana 11, Zipkin 3.

---

## Tasks

### P8.T1 — Spec + plan + docker-compose
- Files: spec, plan, `docker-compose.yml`, `docker/prometheus/prometheus.yml`, `docker/grafana/provisioning/...`
- Add prometheus, grafana, zipkin services with healthchecks; mount prometheus config + grafana provisioning
- Commit: `chore(infra): add prometheus + grafana + zipkin to local stack + phase-8 spec/plan`

### P8.T2 — Dependencies on parent + per-service inheritance
- Modify root `pom.xml` `<dependencyManagement>` if needed (Spring Boot BOM already provides versions)
- Add to each service pom: `micrometer-registry-prometheus`, `micrometer-tracing-bridge-otel`, `opentelemetry-exporter-zipkin`
- Reactive (api-gateway) gets the same set
- Commit: `feat(observability): add prometheus + otel/zipkin tracing deps to every service`

### P8.T3 — Config Server defaults
- Modify `application.yml` (the universal default) — actuator exposure, percentile histograms, tracing sampling, zipkin endpoint, log pattern with traceId/spanId
- Service-specific overrides only if needed (none expected)
- Commit: `config(observability): expose prometheus + tracing defaults in application.yml`

### P8.T4 — Prometheus scrape + Grafana provisioning + dashboard JSON
- `docker/prometheus/prometheus.yml` (10 static targets via host.docker.internal)
- `docker/grafana/provisioning/datasources/datasources.yml` (Prometheus + Zipkin)
- `docker/grafana/provisioning/dashboards/dashboards.yml` (provider)
- `docker/grafana/dashboards/microservices-overview.json` (7 panels)
- Commit: `feat(observability): prometheus scrape + grafana datasources + microservices-overview dashboard`

### P8.T5 — Custom counters/timers on hot paths
- order-service: `MeterRegistry` injection + counters (orders.placed, orders.cancelled), `@Timed` on placeOrder; OutboxRelay outbox.published / outbox.failed counters
- notification-service: notifications.sent / notifications.duplicate counters
- Update unit tests minimally — counters can be `@MockBean MeterRegistry` or use `SimpleMeterRegistry` in tests
- Commit: `feat(observability): custom business counters on saga, outbox relay, notification consumer`

### P8.T6 — Spotless + verify + README + Turkish notes + tag
- `mvn spotless:apply && mvn clean verify`
- README: observability section with UI URLs (Prometheus 9090 / Grafana 3000 / Zipkin 9411), Phase 8 ✅
- `docs/learning/phase-8-notes.md` covering: three pillars, RED method, histograms vs summaries, pull vs push, sampling, cardinality, interview Q&A
- Tag `phase-8-complete`, push
- Commits: `chore: spotless apply`, `docs: README + phase-8 Turkish notes`

## Verification

1. `mvn clean verify` → 12 modules SUCCESS
2. Each service exposes `/actuator/prometheus`
3. (Manual smoke) docker compose up → prometheus targets all UP
4. Tag `phase-8-complete` pushed
