# Phase 11 — Performance + Production-Readiness Implementation Plan

**Goal:** Idempotency-Key (order-service), Caffeine cache (product-service), gateway rate limit, graceful shutdown, liveness/readiness probes.

**Architecture:** All changes are additive; no module rewrites. New tables: `processed_orders` (V3 in order-service). Config-server `application.yml` gets graceful shutdown + probe defaults.

---

## Tasks

### P11.T1 — Spec + plan
- Files: spec, plan
- Commit: `docs(phase-11): performance + production-readiness spec/plan`

### P11.T2 — Idempotency-Key on POST /api/orders
- Files:
  - `services/order-service/src/main/resources/db/migration/V3__processed_orders.sql`
  - `services/order-service/src/main/java/com/backendguru/orderservice/idempotency/ProcessedOrder.java`
  - `ProcessedOrderRepository.java`
  - `IdempotencyService.java` (capture + replay)
  - Modify `OrderController.place(...)` to accept `Idempotency-Key` header
- Wire ObjectMapper for response cache
- Commit: `feat(order-service): Idempotency-Key middleware on POST /api/orders (24h dedup, response replay)`

### P11.T3 — Caffeine cache on product-service
- Add `spring-boot-starter-cache` + `caffeine` deps
- `@EnableCaching` on app or config
- `application.yml` cache config
- `@Cacheable("productById")` on `getProduct(Long)`, `@Cacheable("productPage")` on paged listing
- `@CacheEvict(allEntries=true)` on create/update/delete
- Commit: `feat(product-service): Caffeine local cache on read paths (productById + productPage), TTL 5m, max 10k`

### P11.T4 — Gateway rate limiting
- Add `spring-cloud-starter-gateway` Redis rate limiter (already on classpath via gateway). Add `spring-boot-starter-data-redis-reactive` to api-gateway pom (small addition).
- `KeyResolver` bean (IP-based)
- `default-filters` in `api-gateway.yml`
- Commit: `feat(api-gateway): Redis-backed token-bucket rate limiter (50/s replenish, 100 burst, IP key)`

### P11.T5 — Graceful shutdown + probes (default for all services)
- Modify config-server's universal `application.yml`:
  - `server.shutdown: graceful`
  - `spring.lifecycle.timeout-per-shutdown-phase: 30s`
  - `management.endpoint.health.probes.enabled: true`
- Commit: `config(observability): graceful shutdown + liveness/readiness probes default for all services`

### P11.T6 — Spotless + verify + README + Turkish notes + tag
- spotless apply, mvn clean verify (14 modules)
- README: try-it shows Idempotency-Key replay + 429 from gateway
- `phase-11-notes.md` (idempotency design, cache strategies, rate limit algorithms, K8s probes, graceful shutdown, interview Q&A)
- Tag `phase-11-complete`

## Verification

1. `mvn clean verify` 14 modules
2. Tag pushed
