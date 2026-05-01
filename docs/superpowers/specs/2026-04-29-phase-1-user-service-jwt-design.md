# Phase 1 — User Service + JWT Authentication Design

**Status:** Implemented
**Phase:** 1 of 12
**Depends on:** Phase 0 (microservice foundation)

## 1. Overview

Add `services/user-service` (port 8081) that owns user identity, persists users in PostgreSQL via JPA, exposes registration/login/refresh endpoints, and issues JWT access + refresh tokens. The API Gateway gains a JWT validation filter so downstream services receive an already-authenticated request.

### Goals

- PostgreSQL via Flyway migrations on startup
- Users with audit columns and `@Version` for optimistic locking
- BCrypt strength 12 password hashing
- HS256-signed JWTs (access 15 min, refresh 7 days)
- Refresh tokens persisted (DB) with SHA-256 hash so a DB dump cannot replay
- Gateway validates JWT, propagates `X-User-Id` / `X-User-Role` to downstream
- `@EntityGraph` on `User → Address` to pre-empt N+1
- Swagger/OpenAPI per service (added per 2026-04-29 brief update)

### Non-goals

- OAuth2 / social login
- Multi-factor auth, password reset email flow
- RSA-signed (RS256) JWTs (HS256 is fine for single-issuer; can revisit)

## 2. Tech additions

| Concern | Choice | Why |
|---|---|---|
| Database | PostgreSQL 16 (alpine) | ACID, mature ecosystem |
| Migration | Flyway | Standard for Spring; SQL versioned |
| ORM | Spring Data JPA + Hibernate 6.6 | Bundled with Spring Boot 3.4 |
| JWT library | `io.jsonwebtoken:jjwt` 0.12.x | Mature, fluent builder |
| Password | `BCryptPasswordEncoder` strength 12 | Adaptive, salt included |
| Tests | Testcontainers PostgreSQL | Real engine in tests |
| Docs | `springdoc-openapi-starter-webmvc-ui` 2.6 | Swagger UI w/ bearer scheme |

## 3. Domain model (DDL)

```sql
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(80)  NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version       BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_users_email ON users(email);

CREATE TABLE addresses (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    line1 VARCHAR(200) NOT NULL,
    city VARCHAR(100) NOT NULL,
    country VARCHAR(100) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,   -- SHA-256 hex of the JWT
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ
);
```

## 4. JWT design

| Token | Lifetime | Storage | Claims |
|---|---|---|---|
| Access | 15 min | None (stateless) | `sub=userId`, `email`, `role`, `iat`, `exp` |
| Refresh | 7 days | DB (SHA-256 hashed) | `sub=userId`, `tokenId=UUID`, `iat`, `exp` |

### Issuance flow (login)

1. Client → `POST /auth/login` with email/password
2. `AuthService` loads user, BCrypt verifies
3. Generate access JWT, generate refresh JWT
4. Persist `sha256(refresh)` row → `refresh_tokens`
5. Return `TokenResponse`

### Refresh flow (rotation + replay detection)

1. Parse JWT (validates signature + expiry)
2. Look up `refresh_tokens` by SHA-256 hash
3. If found and not revoked → mark `revoked_at = now()`, issue new pair
4. If not found OR revoked → 401 (replay detected → user must re-login)

## 5. Endpoints

| Method | Path | Auth | Body | Response |
|---|---|---|---|---|
| POST | `/api/auth/register` | Public | `{email, password}` | `201` `{id}` |
| POST | `/api/auth/login` | Public | `{email, password}` | `TokenResponse` |
| POST | `/api/auth/refresh` | Public | `{refreshToken}` | `TokenResponse` |
| POST | `/api/auth/logout` | Bearer | — | `204` (revoke all refresh tokens) |
| GET | `/api/users/me` | Bearer | — | `UserResponse` |
| GET | `/api/users/me/with-addresses` | Bearer | — | `UserResponse` (uses `@EntityGraph`) |

All responses use `ApiResponse<T>` envelope from `common`.

## 6. Security configuration (Servlet stack)

`SecurityFilterChain`:
- CSRF disabled (stateless API)
- Session policy STATELESS
- `permitAll`: register, login, refresh, actuator, swagger
- `anyRequest`: authenticated (JWT)
- `JwtAuthenticationFilter` placed before `UsernamePasswordAuthenticationFilter`
- Custom `AuthenticationEntryPoint` returns JSON 401 in `ErrorResponse` shape

`JwtAuthenticationFilter` reads `Authorization: Bearer ...`, parses claims, builds `UsernamePasswordAuthenticationToken(userId, null, [ROLE_<role>])`, sets `SecurityContext`. Failures stay anonymous (let authorize rules reject).

`PasswordEncoder` = `BCryptPasswordEncoder(12)` — strength 12 ≈ 250ms on modern hardware, balances UX vs brute-force resistance.

## 7. Acceptance criteria

- [x] `mvn clean verify` succeeds with 5 modules
- [x] User Service starts on :8081, registers with Eureka, applies Flyway V1
- [x] `POST /api/auth/register` creates a hashed-password user
- [x] `POST /api/auth/login` returns `TokenResponse`; access token decoded shows `sub=<userId>`
- [x] `GET /api/users/me` with valid Bearer returns `UserResponse`
- [x] Without Bearer → 401 with `ErrorResponse` shape
- [x] Refresh token rotation works; replayed refresh → 401
- [x] `findWithAddressesById` issues a single SQL with JOIN (Testcontainer test verifies)
- [x] Swagger UI on :8081 shows endpoints with bearer auth
- [x] All Testcontainer tests pass

## 8. Interview topics unlocked (covered in learning notes)

N+1 + `@EntityGraph` vs `JOIN FETCH` vs `@BatchSize`, lazy/eager fetching, `LazyInitializationException`, `@Transactional` propagation/isolation, optimistic locking with `@Version`, BCrypt vs Argon2, JWT vs session, refresh-token rotation + replay defense, Spring Security filter chain order, CSRF in stateless APIs (why disabled with JWT), HS256 vs RS256, Flyway baseline / repeatable migrations, Hibernate L1/L2 cache, ACID + isolation levels, Testcontainers vs H2.
