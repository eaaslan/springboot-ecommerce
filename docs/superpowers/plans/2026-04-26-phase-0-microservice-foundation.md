# Phase 0 — Microservice Foundation Implementation Plan

Spec: `docs/superpowers/specs/2026-04-26-phase-0-microservice-foundation-design.md`

## Tasks (all delivered)

### Foundation
1. **Multi-module Maven parent POM** — Java 21, Spring Boot 3.4, Spring Cloud 2024.0 (Moorgate), Lombok + MapStruct annotation processor binding, Spotless plugin (Google Java Format) on `verify`.
2. **`.editorconfig`** + `.gitignore` (target, .idea, .DS_Store, *.log).
3. **`docker-compose.yml`** with PostgreSQL placeholder (populated in Phase 1) + `docker/postgres/init.sql` idempotent productdb provisioning.

### `shared/common` library
4. **`pom.xml`** — library JAR (no Spring Boot repackage); `spring-boot-starter-web` + `spring-boot-starter-webflux` as `optional` so consumers pick their stack.
5. **`ErrorCode` enum** — RESOURCE_NOT_FOUND, DUPLICATE_RESOURCE, UNAUTHORIZED, FORBIDDEN, VALIDATION_FAILED, SERVICE_UNAVAILABLE, INTERNAL_ERROR with HTTP status mapping.
6. **`BusinessException` hierarchy** — base + 5 concrete subclasses (Resource/Duplicate/Unauthorized/Forbidden/Validation), `protected` constructors keep base abstract-ish.
7. **`ApiResponse<T>` envelope record** with `success(T)` and `failure(ErrorResponse)` factories. `@JsonInclude(NON_NULL)`.
8. **`ErrorResponse`** record (RFC 7807-flavoured) with `Builder` and `from(BusinessException, path, traceId)` factory.
9. **`LoggingConstants`** — header `X-Correlation-Id`, MDC keys `traceId` + `userId`.
10. **`CorrelationIdFilter`** (Servlet) — `OncePerRequestFilter` + `@ConditionalOnWebApplication(SERVLET)`; reads/generates correlation ID, puts into MDC, clears in `finally`.
11. **`CorrelationIdWebFilter`** (Reactive) — `WebFilter` + `@ConditionalOnWebApplication(REACTIVE)`; puts traceId into Reactor Context for cross-thread propagation.
12. **`logback-spring-template.xml`** — three profile blocks (dev plain, docker/prod JSON, default fallback).

### Tests
13. **ErrorCodeTest, BusinessExceptionTest, ApiResponseTest, ErrorResponseTest, CorrelationIdFilterTest, CorrelationIdWebFilterTest** — 26 unit tests, AssertJ.

### Infrastructure modules
14. **`infrastructure/config-server`** (8888) — `@EnableConfigServer`, native filesystem backend, all subsequent phase configs pre-staged in `configs/` (`application.yml`, `application-dev.yml`, `application-docker.yml`, `discovery-server.yml`, `api-gateway.yml`, `api-gateway-dev.yml`, `user-service.yml/-dev.yml`, `product-service.yml/-dev.yml`).
15. **`infrastructure/discovery-server`** (8761) — `@EnableEurekaServer`, self-preservation off in dev (`eviction-interval-timer-in-ms: 5000`), config import via Config Server.
16. **`infrastructure/api-gateway`** (8080, reactive) — Spring Cloud Gateway routes via Eureka discovery (`lb://`), `CorsConfig`, `GlobalErrorWebExceptionHandler` returning common `ErrorResponse` JSON, `GatewayJwtAuthenticationFilter` (jjwt 0.12.6) with HS256 verification + public prefixes + GET /api/products bypass + X-User-* header strip.

### Module-level smoke tests
17. **ConfigServerApplicationTests + ConfigServerNativeBackendTest** — context loads, serves api-gateway/dev and discovery-server/default endpoints.
18. **DiscoveryServerApplicationTests** — context loads (config + eureka client disabled in test).
19. **ApiGatewayApplicationTests + GlobalErrorWebExceptionHandlerTest** — context loads (config + eureka + discovery locator off, jwt secret stubbed); error handler emits ErrorResponse for `ResponseStatusException` and generic `RuntimeException`.

## Acceptance

`mvn clean verify` BUILD SUCCESS — 4 modules, 28 tests, Spotless clean.
