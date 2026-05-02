# Phase 10 — Reactive Catalog Stream Design (WebFlux + R2DBC)

## 1. Goal

Add a **reactive read facade** over the existing `productdb` to demonstrate Project Reactor / Spring WebFlux / R2DBC end-to-end. Side-by-side with the imperative `product-service` (Servlet + JPA), so learners and interviewers can directly compare paradigms.

## 2. Design Choice — New Module vs. Migrate Existing

| Option | Pros | Cons |
|---|---|---|
| Migrate `product-service` to WebFlux | Single source of truth | Breaking change; tests, security, gateway routes all churn; mixed paradigm risk |
| **NEW** `catalog-stream-service` (port 8089) — reads only | Clean comparison; both paradigms live; SSE demo natural | One more module |

✅ **NEW module.** Reasoning: comparison value > duplication cost in a learning project. Same DB read access; write path stays imperative.

## 3. Module Layout

```
services/catalog-stream-service/
├── pom.xml                              # webflux + r2dbc-postgresql
└── src/main/java/com/backendguru/catalogstreamservice/
    ├── CatalogStreamServiceApplication.java
    ├── catalog/
    │   ├── ProductRow.java               # @Table record matching products schema
    │   ├── ProductReactiveRepository.java # ReactiveCrudRepository<ProductRow, Long>
    │   ├── ReactiveCatalogService.java   # business ops (Mono/Flux)
    │   └── ReactiveCatalogController.java # @RestController WebFlux
    ├── exception/GlobalExceptionHandler.java
    └── config/OpenApiConfig.java
└── src/main/resources/application.yml
```

No DB migrations — reads tables `products` (and `categories` lazily by id) created by product-service Flyway. R2DBC connects with read-only convention; we don't INSERT.

## 4. Endpoints

| Method | Path | Returns | Notes |
|---|---|---|---|
| GET | `/api/catalog/products?page=0&size=20` | `Flux<ProductRow>` | Paginated reactive query |
| GET | `/api/catalog/products/{id}` | `Mono<ProductRow>` | 404 via Mono.error |
| GET | `/api/catalog/products/search?q=` | `Flux<ProductRow>` | Reactive ILIKE filter |
| GET | `/api/catalog/products/stream` | `Flux<ProductRow>` (SSE) | Emits one product every 2s — `text/event-stream` |

## 5. Reactive Stack Choices

| Concern | Choice | Why |
|---|---|---|
| Web layer | Spring WebFlux annotated controller | Familiar `@RestController` ergonomics |
| DB driver | R2DBC postgresql | Non-blocking; matches reactive theme |
| Repository | `ReactiveCrudRepository` from spring-data-r2dbc | Auto-derived queries, type-safe |
| Backpressure | Default reactor buffer | In-memory MVP; documented |
| Logging traceId | Spring Cloud Sleuth/OTel propagates context for reactor | works via `Hooks.enableAutomaticContextPropagation()` if needed |
| Correlation id | Reuse `CorrelationIdWebFilter` from `shared/common` | already exists |

## 6. Key Reactive Concepts Demonstrated

1. **Mono / Flux** — single value vs. stream
2. **Backpressure** — consumer-paced production via Reactive Streams
3. **Lazy execution** — pipeline doesn't run until subscribe
4. **Server-Sent Events** — `Flux<T>` + `produces=text/event-stream` ⇒ chunked HTTP push
5. **R2DBC vs JDBC** — non-blocking driver; threads not parked on I/O
6. **WebTestClient + StepVerifier** — reactive test ergonomics

## 7. Trade-offs & When NOT to Use WebFlux

| Use WebFlux when | Stick with Servlet/MVC when |
|---|---|
| Long-lived connections (SSE, WS) | CRUD apps |
| High concurrent I/O bound load (10k+ idle conns) | CPU-bound work |
| Streaming data from upstream | Simple request/response |
| Backpressure matters | Team unfamiliar with reactive — debugging cost is real |

> **Mainstream verdict (2024+):** Virtual threads (Java 21 Loom) reduce WebFlux's necessity for the "blocking I/O scaling" use case. WebFlux remains valuable for **streaming**, **functional composition**, and **library APIs** that already return reactive types.

## 8. Trade-offs & Out-of-Scope

| Item | Why deferred |
|---|---|
| Reactive write path | Order saga is naturally transactional; mixing reactive + JPA in same service is messy |
| Reactive Kafka consumer | Phase 11 perf chapter |
| Functional routing (RouterFunction) | Annotated controller is more familiar; can be added later |
| Reactive JWT auth | Read endpoints public; gateway already filters |

## 9. Interview Talking Points

1. **WebFlux vs MVC** — event loop vs thread-per-request. Netty default vs Tomcat.
2. **Mono vs Flux vs Java's CompletableFuture** — Reactive Streams spec vs callback chain; backpressure.
3. **R2DBC vs JDBC** — non-blocking driver, requires DB driver support (Postgres/MySQL/MSSQL OK; Oracle limited).
4. **Loom vs Reactive** — virtual threads ease blocking-I/O scaling; reactive still wins streaming/composition.
5. **`subscribeOn` vs `publishOn`** — where work runs; `subscribeOn` upstream, `publishOn` downstream.
6. **Backpressure strategies** — BUFFER, DROP, LATEST, ERROR.
7. **Testing reactive** — StepVerifier expects emissions; VirtualTimeScheduler for time-based.
8. **Common pitfalls** — `block()` in production, leaking threads from blocking calls, lost MDC context (without auto context propagation hook).

## 10. Acceptance Criteria

1. `mvn clean verify` succeeds — 14 modules.
2. `curl http://localhost:8089/api/catalog/products?page=0&size=5` returns JSON array.
3. `curl -N http://localhost:8089/api/catalog/products/stream` keeps connection open and emits events.
4. Through gateway: `curl http://localhost:8080/api/catalog/products`.
5. Tag `phase-10-complete` pushed.
