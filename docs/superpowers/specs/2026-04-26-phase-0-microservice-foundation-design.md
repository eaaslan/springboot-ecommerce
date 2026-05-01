# Phase 0 — Microservice Foundation Design

**Status:** Implemented
**Phase:** 0 of 12

## 1. Overview

Bootstrap a Spring Cloud microservice monorepo. Establish three platform services (Config Server, Eureka, API Gateway) and a `shared/common` library with cross-cutting concerns (response envelopes, exception model, correlation filters). No business domain in this phase — infrastructure-only.

### Goals
- Multi-module Maven monorepo, Java 21, Spring Boot 3.4, Spring Cloud 2024.0 (Moorgate)
- Config Server (8888) with **native filesystem** backend (Git-backed deferred to prod phase)
- Eureka Server (8761), self-preservation off in dev for fast eviction
- API Gateway (8080), reactive (Spring Cloud Gateway/WebFlux): routing, CORS, JWT validation, correlation IDs, RFC 7807-flavoured error responses
- `shared/common`: `ApiResponse<T>` envelope, `ErrorResponse` (RFC 7807-flavoured), `ErrorCode` enum (incl. `SERVICE_UNAVAILABLE`), `BusinessException` hierarchy, correlation filters for both Servlet and Reactive stacks
- Spotless + Google Java Format enforced on `verify`
- Profile strategy: `dev` (plain text logs, localhost), `docker` (JSON logs, service-name DNS), `prod`

### Non-goals
- Database / persistence (Phase 1+)
- Authentication / authorization business logic (Phase 1)
- Domain entities (Phase 1+)
- Production deployment (Phase 12)

## 2. Repository structure

```
springboot-ecommerce/
├── pom.xml                                # multi-module parent
├── docker-compose.yml                     # placeholder (postgres added in Phase 1)
├── docker/postgres/init.sql               # idempotent productdb provisioning
├── README.md, .gitignore, .editorconfig
├── shared/common/                         # library JAR
│   ├── pom.xml
│   └── src/main/java/com/backendguru/common/
│       ├── dto/{ApiResponse, ErrorResponse}.java
│       ├── error/{ErrorCode, BusinessException, ResourceNotFound, Duplicate, Unauthorized, Forbidden, Validation}Exception.java
│       └── logging/{LoggingConstants, CorrelationIdFilter, CorrelationIdWebFilter}.java
├── infrastructure/
│   ├── config-server/                     # 8888 — native fs backend
│   ├── discovery-server/                  # 8761 — Eureka
│   └── api-gateway/                       # 8080 — reactive WebFilter chain
└── docs/superpowers/{specs,plans} + docs/learning/   # interview prep documentation
```

## 3. Cross-cutting design choices

### `ApiResponse<T>` envelope
`{ success, data, error, timestamp }`. Wraps every JSON response. Frontend always parses the same shape; `error` is `null` on success, `data` is `null` on failure.

### `ErrorResponse` (RFC 7807-flavoured)
`{ code, message, status, path, traceId, timestamp, details? }`. `from(BusinessException)` factory builds it from any thrown exception in services.

### `ErrorCode` enum
Maps each business-meaningful failure to an HTTP status:
- `RESOURCE_NOT_FOUND` (404)
- `DUPLICATE_RESOURCE` (409)
- `UNAUTHORIZED` (401)
- `FORBIDDEN` (403)
- `VALIDATION_FAILED` (400)
- `SERVICE_UNAVAILABLE` (503) — added for Phase 3 Resilience4j fallback
- `INTERNAL_ERROR` (500)

### Correlation IDs
Every request gets `X-Correlation-Id` (incoming or generated UUID). Propagated to log MDC (`traceId` key) for cross-service tracing.

- **Servlet stack** → `CorrelationIdFilter` (`OncePerRequestFilter`)
- **Reactive stack** → `CorrelationIdWebFilter` (`WebFilter`) puts traceId into Reactor Context
- `@ConditionalOnWebApplication(SERVLET|REACTIVE)` keeps each filter only in the matching stack

### API Gateway JWT filter
`GatewayJwtAuthenticationFilter` (reactive `WebFilter`):
- Public prefixes: `/api/auth/`, `/actuator/`, `/v3/api-docs`, `/swagger-ui`
- `GET /api/products/**` → public (catalog browse, no JWT required)
- Strips inbound `X-User-*` headers (forgery defense), injects validated values from JWT for authenticated paths
- HS256 with shared secret via Config Server (`app.jwt.secret`)

## 4. Profile strategy

| Profile | Activation | Logging | Service URLs | Notes |
|---|---|---|---|---|
| `dev` | default in IDE | plain text | `localhost:*` | self-preservation off, hibernate SQL DEBUG |
| `docker` | `SPRING_PROFILES_ACTIVE=docker` | JSON | service-name DNS (`http://discovery-server:8761/eureka/`) | for `docker compose up` |
| `prod` | env on Oracle Cloud (Phase 12) | JSON | production DNS | Git-backed Config Server |

## 5. Acceptance criteria

- [x] `mvn clean verify` BUILD SUCCESS
- [x] Spotless + Google Java Format enforced on every module
- [x] All four modules start clean (`contextLoads` smoke tests pass)
- [x] Common module 26 tests passing (errors, DTOs, filters)
- [x] Gateway error handler emits `ErrorResponse` JSON for any reactive exception
- [x] Correlation ID propagation works across both stacks (asserted in tests)
- [x] Config Server serves placeholder configs from native backend
- [x] Eureka dashboard accessible at `:8761`

## 6. Interview topics unlocked

Microservice foundation patterns, Service Discovery (CAP — Eureka AP), API Gateway responsibilities, centralized configuration, Spring Cloud release trains (Moorgate=2024.0), reactive vs Servlet stacks, `@ConditionalOnWebApplication`, RFC 7807 Problem Details, correlation IDs (MDC + Reactor Context), JWT validation at edge vs everywhere, profile strategy (dev/docker/prod), Spotless code style enforcement.
