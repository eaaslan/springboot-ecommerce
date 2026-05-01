# Phase 1 — User Service + JWT Implementation Plan

Spec: `docs/superpowers/specs/2026-04-29-phase-1-user-service-jwt-design.md`

## Tasks (compact, all delivered)

1. **Postgres in docker-compose** with healthcheck, init.sql for productdb (used in Phase 2 too).
2. **Module skeleton** `services/user-service` with deps: web, data-jpa, security, validation, actuator, config-client, eureka-client, flyway-core, flyway-database-postgresql, postgresql, jjwt 0.12.6, mapstruct, lombok, logstash-logback-encoder, springdoc-openapi 2.6, testcontainers, h2, json-path.
3. **Application class** `UserServiceApplication` with `@EnableDiscoveryClient`, `@ConfigurationPropertiesScan`, `scanBasePackages` includes `common`. `application.yml` imports config server (optional). `logback-spring.xml` template (3 profile blocks).
4. **Config Server entries** `user-service.yml` (port 8081, datasource, JPA validate, Flyway, JWT properties), `user-service-dev.yml` (Hibernate SQL DEBUG).
5. **Flyway V1** — three tables: `users`, `addresses`, `refresh_tokens` with indexes.
6. **Entities** `User`, `Address` with audit fields, `@Version`, lazy `@OneToMany` / `@ManyToOne`. `Role` enum (USER, ADMIN). `@EqualsAndHashCode(of="id")`, `@ToString(of=...)` discipline. No `@Data` on entities. `addAddress()` helper sets bidirectional link.
7. **UserRepository** with `findByEmail`, `existsByEmail`, `findWithAddressesById` (`@EntityGraph(attributePaths="addresses")`).
8. **JwtProperties** record + **JwtService** issuing HS256 access (15min) + refresh (7d) JWTs, `parse()` for validation.
9. **RefreshToken** entity + repository (`findByTokenHash`, `deleteByUserId @Modifying @Query`).
10. **AuthService** — register (BCrypt), login (verify), refresh (rotate + replay detect), logout (delete all). SHA-256 hashes refresh JWTs before persisting.
11. **AuthController** — POST register/login/refresh/logout endpoints under `/api/auth`.
12. **GlobalExceptionHandler** maps `BusinessException` → `ErrorResponse`, `MethodArgumentNotValidException` → 400 with field details.
13. **SecurityConfig + JwtAuthenticationFilter** — stateless, BCrypt encoder, custom 401 entrypoint, public whitelist (auth + actuator + swagger).
14. **UserController + UserService + UserMapper (MapStruct)** — `/api/users/me` and `/api/users/me/with-addresses`.
15. **Smoke test** (`UserServiceApplicationTests`) with `Replace.ANY` H2 fallback.
16. **JwtServiceTest** (unit, no Spring) — claims, refresh token id, tampered token rejection.
17. **UserRepositoryTest** (Testcontainers) — persists, findByEmail, entity graph.
18. **AuthFlowIntegrationTest** (Testcontainers + MockMvc) — register→login→/me, no Bearer → 401, duplicate registration → 409, wrong password → 401, refresh rotation + replay.
19. **OpenApiConfig** with bearer security scheme.
20. **API Gateway updates** — public `/api/auth/**`, protected `/api/users/**`, `app.jwt.secret` shared with user-service. `GatewayJwtAuthenticationFilter` validates HS256, injects `X-User-*`.

## Acceptance

`mvn clean verify` (with postgres up) → BUILD SUCCESS, all tests pass.
