# Phase 11 — Performance + Production-Readiness Design

## 1. Goal

Take the working microservices system and add the production-grade hardening that distinguishes a "demo project" from "ready for an SRE review." Five concrete additions, each with an obvious interview-talkback moment.

| Addition | Where | Production value |
|---|---|---|
| Idempotency-Key middleware | order-service POST /api/orders | Network retries don't double-charge |
| Caffeine local cache | product-service read paths | DB pressure ↓, latency ↓ |
| Gateway rate limiting (Redis token bucket) | api-gateway | Abuse / runaway-script protection |
| Graceful shutdown | every service | No request dropped on rollout |
| Liveness vs readiness probes | every service | Kubernetes-grade orchestration |

## 2. Idempotency-Key — The Order Path

### Problem
Network glitch between client and gateway → client retries POST /api/orders → server already created the order → **double charge.**

### Solution
Client sends `Idempotency-Key: <UUID>` header. Server stores `(idempotency_key → order_id, response)` in a dedup table. Same key, same response. Different key, new order.

### Schema (V3 in order-service)
```sql
CREATE TABLE processed_orders (
    idempotency_key  VARCHAR(80)  PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    order_id         BIGINT       NOT NULL,
    response_body    TEXT         NOT NULL,
    response_status  INT          NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_processed_orders_user ON processed_orders(user_id, created_at);
```

A retention job (out-of-scope here) prunes rows older than 24h.

### Flow
```java
String key = request.getHeader("Idempotency-Key");

if (key != null) {
  Optional<ProcessedOrder> existing = repo.findByIdempotencyKeyAndUserId(key, userId);
  if (existing.isPresent()) {
    return rebuildResponse(existing.get());      // 200/201 + cached body
  }
}
OrderResponse resp = service.placeOrder(userId, body);
if (key != null) {
  repo.save(new ProcessedOrder(key, userId, resp.id(), serialize(resp), 201));
}
return resp;
```

### Edge cases
- **Different user, same key** — treat as different (`(idempotency_key, user_id)` composite check). Prevents key-leakage cross-tenant.
- **Concurrent same-key** — DB unique constraint; loser catches `DataIntegrityViolationException`, re-reads existing row.
- **Saga still in flight** — if first request hasn't committed `processed_orders` row yet, second request would create duplicate. Mitigation: write the dedup row in the **same transaction** as the order creation.

> **Mülakat sorusu:** "Stripe'ın idempotency-key tasarımı ile karşılaştır" — Stripe 24h retention, response body cached, status code preserved. Yaklaşımımız aynı pattern.

## 3. Caffeine Cache — product-service Read Paths

### Where
- `GET /api/products/{id}` → `productById` cache
- `GET /api/products` (paged listing) → `productPage` cache (key = page+size+filter hash)

### Library
`com.github.ben-manes.caffeine:caffeine` — high-performance in-process cache. Spring Boot integrates via `spring-boot-starter-cache` + `@EnableCaching`.

### Config
```yaml
spring:
  cache:
    cache-names: productById, productPage
    caffeine:
      spec: maximumSize=10000,expireAfterWrite=5m,recordStats
```

`recordStats` enables `cache.gets`, `cache.puts`, `cache.evictions` Micrometer metrics — visible in Grafana.

### Eviction
Admin endpoints (`POST /api/products`, `PUT /api/products/{id}`) annotated `@CacheEvict(cacheNames={"productById","productPage"}, allEntries=true)`. Crude but correct for MVP — fine-grained eviction is a Phase 12 concern.

### Why local-only (not Redis)?
- ~10k products * ~1KB = ~10MB heap per instance — trivial.
- No network hop = sub-microsecond hit latency.
- Already have Redis (cart) — but cart needs cross-instance consistency, product reads are stale-tolerant.
- Hybrid (Caffeine L1 + Redis L2) is Phase 12+ optimization.

## 4. API Gateway Rate Limiting

### Library
Spring Cloud Gateway includes `RequestRateLimiter` filter using Redis Lua script (token bucket per key).

### Config (api-gateway.yml)
```yaml
spring:
  cloud:
    gateway:
      default-filters:
        - name: RequestRateLimiter
          args:
            redis-rate-limiter.replenishRate: 50      # tokens/sec
            redis-rate-limiter.burstCapacity: 100     # bucket size
            key-resolver: "#{@ipKeyResolver}"
```

### Key resolver
```java
@Bean KeyResolver ipKeyResolver() {
  return exchange -> Mono.just(
      exchange.getRequest().getRemoteAddress() != null
          ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
          : "anonymous");
}
```

Rate limit triggers → `429 Too Many Requests` with retry headers.

### Production tuning (documented, not implemented)
- Per-user rate limit (require auth in resolver)
- Burst-friendly endpoints (catalog) vs strict (auth, order)
- Global vs per-route filter
- Redis Cluster for multi-AZ

> **Mülakat sorusu:** "Token bucket vs leaky bucket?" — Token bucket allows bursts (drain bucket fast then refill), leaky bucket smooths to constant rate. Stripe/AWS prefer token bucket for API ergonomics.

## 5. Graceful Shutdown

### Setting (`application.yml` common)
```yaml
server:
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

### Behavior
On `SIGTERM`:
1. Stop accepting new requests (server stops listening)
2. Let in-flight requests finish (up to 30s)
3. Close DB pools, MQ connections, scheduled tasks
4. Exit with 0

### Why this matters in K8s
Without graceful shutdown, pod kill mid-request → 502 to client. With it + `terminationGracePeriodSeconds: 60` → zero downtime rolling deploys.

## 6. Liveness vs Readiness Probes

### Distinction
- **Liveness** — "Am I alive?" If false → kill + restart. Should NOT depend on downstream services.
- **Readiness** — "Can I serve traffic right now?" If false → remove from LB. CAN depend on critical downstreams (DB, Redis).

### Config
```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
      group:
        liveness:
          include: livenessState
        readiness:
          include: readinessState,db,redis
```

### Endpoints
- `/actuator/health/liveness` → 200 if app process is up
- `/actuator/health/readiness` → 200 if dependencies healthy

K8s manifest example (documented):
```yaml
livenessProbe:
  httpGet: { path: /actuator/health/liveness, port: 8086 }
  periodSeconds: 10
readinessProbe:
  httpGet: { path: /actuator/health/readiness, port: 8086 }
  periodSeconds: 5
```

## 7. Out-of-Scope

| Item | Why deferred |
|---|---|
| Hybrid L1/L2 cache (Caffeine + Redis) | Marginal value for learning project |
| HPA (Horizontal Pod Autoscaler) | Phase 12 K8s deployment |
| GC tuning (G1 → ZGC) | Workload-specific; profile first |
| JFR / async-profiler integration | Phase 12 |
| Bulkhead pattern | Resilience4j already does CB; bulkhead overlap |
| Circuit breaker for DB | DB outage = restart anyway |

## 8. Interview Talking Points

1. **Idempotency-Key vs replay** — at-least-once delivery is a fact of life; idempotency makes it safe.
2. **Cache invalidation** — "There are only two hard things in CS: cache invalidation, naming things, and off-by-one errors." Time-based eviction (5m TTL) is the lazy correct answer; event-based invalidation needs Phase 7's Kafka.
3. **Cache stampede** — many concurrent misses on same key → DB hammered. Caffeine's `LoadingCache` + single-flight loader prevents this (built-in).
4. **Rate limit fairness** — IP key isn't fair behind NAT; user key requires auth. Trade-off.
5. **Graceful shutdown without K8s** — `kill -TERM <pid>`. Test locally with `mvn spring-boot:run` + `Ctrl+C`.
6. **Liveness fail = restart loop** — the most common K8s mistake is liveness depending on DB. DB outage → all pods restart → app never ready.
7. **JVM warm-up** — first requests slow due to JIT. CDS/AOT compilation tradeoffs. Spring AOT (`native-image`) is Phase 12.

## 9. Acceptance Criteria

1. `mvn clean verify` green for all 14 modules.
2. `Idempotency-Key` smoke: same header + same body → second response 200 (not 201) with same orderId.
3. `/actuator/caches` shows `productById` and `productPage` after a request.
4. Gateway rate limiter: rapid-fire 200 reqs → some 429 responses.
5. `/actuator/health/liveness` and `/health/readiness` distinct.
6. `Ctrl+C` mid-request returns response, then exits clean.
7. Tag `phase-11-complete` pushed.
