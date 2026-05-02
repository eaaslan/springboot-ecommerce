# Phase 10 — Reactive Catalog Stream Implementation Plan

**Goal:** New `catalog-stream-service` (port 8089) demonstrating WebFlux + R2DBC against existing productdb.

**Architecture:** Reactive read facade. Annotated WebFlux controller, ReactiveCrudRepository, R2DBC pool. SSE endpoint demos streaming.

**Tech Stack:** Spring WebFlux, spring-data-r2dbc, r2dbc-postgresql, reactor-test.

---

## Tasks

### P10.T1 — Spec + plan + module skeleton
- Files: spec, plan, root pom (add module), service pom (skeleton), main class, application.yml
- Commit: `chore(infra)+docs(phase-10): catalog-stream-service skeleton + spec/plan`

### P10.T2 — Dependencies
- webflux + r2dbc starter + r2dbc-postgresql + observability + common
- Commit: `feat(catalog-stream-service): WebFlux + R2DBC dependencies`

### P10.T3 — ProductRow + ReactiveRepository
- `ProductRow.java` (record with `@Id`, `@Table("products")`, `@Column` mappings)
- `ProductReactiveRepository extends ReactiveCrudRepository<ProductRow, Long>` + `Flux<ProductRow> findByEnabledTrue(Pageable)` + `Flux<ProductRow> findByNameContainingIgnoreCaseAndEnabledTrue(String)`
- Commit: `feat(catalog-stream-service): ProductRow record + ReactiveCrudRepository`

### P10.T4 — Service + Controller + SSE
- `ReactiveCatalogService` — `Flux<ProductRow> page(int, int)`, `Mono<ProductRow> byId(Long)`, `Flux<ProductRow> search(String, int)`, `Flux<ProductRow> stream(int)` (interval emit)
- `ReactiveCatalogController` — endpoints from spec §4
- Commit: `feat(catalog-stream-service): reactive service + controller + SSE stream endpoint`

### P10.T5 — Exceptions + OpenApi
- `GlobalExceptionHandler` reactive (@RestControllerAdvice with Mono<ResponseEntity<ErrorResponse>>)
- `OpenApiConfig` with springdoc-webflux
- Commit: `feat(catalog-stream-service): reactive exception handler + OpenApi`

### P10.T6 — Tests
- `CatalogStreamServiceApplicationTests` smoke (R2DBC disabled or H2 R2DBC in test)
- `ReactiveCatalogServiceTest` with mocked repository returning Flux/Mono, asserted via StepVerifier
- Commit: `test(catalog-stream-service): smoke + reactive unit tests with StepVerifier`

### P10.T7 — Config Server + gateway route
- `catalog-stream-service.yml` (port 8089, R2DBC URL `r2dbc:postgresql://localhost:5432/productdb`, user product/pass)
- Gateway route `/api/catalog/**`; bypass JWT for GET
- Commit: `config(catalog-stream): externalize R2DBC + gateway route /api/catalog/**`

### P10.T8 — Spotless + verify + README + Turkish notes + tag
- spotless apply, mvn clean verify (14 modules)
- README row added, Phase 10 ✅, try-it includes SSE curl
- `phase-10-notes.md` covering imperative vs reactive, Mono/Flux, R2DBC, Loom comparison, when-to-use, interview Q&A
- Tag `phase-10-complete`

## Verification

1. `mvn clean verify` 14 modules
2. Tag pushed
