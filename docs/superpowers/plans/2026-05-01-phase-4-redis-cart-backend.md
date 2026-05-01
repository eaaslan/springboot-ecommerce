# Phase 4 — Redis Cart Backend Implementation Plan

Spec: `docs/superpowers/specs/2026-05-01-phase-4-redis-cart-backend-design.md`

**Goal:** Swap `InMemoryCartStore` for `RedisCartStore` via profile-based bean selection. Phase 3 service code untouched.

**Tech additions:** `spring-boot-starter-data-redis`, Testcontainers `GenericContainer<>("redis:7-alpine")`.

---

## Tasks (compact, autonomous execution)

1. **Add Redis to docker-compose** with healthcheck on port 6379. Manually start: `docker compose up -d redis`.
2. **Add `spring-boot-starter-data-redis` to cart-service `pom.xml`**.
3. **Create `RedisConfig`** with `cartRedisTemplate` bean using `Jackson2JsonRedisSerializer` + `JavaTimeModule`.
4. **Create `RedisCartStore`** implementing `CartStore` with key `cart:<userId>` and 30-day TTL.
5. **Add `@Profile("!test")` to `RedisCartStore`** and **`@Profile("test")` to existing `InMemoryCartStore`**.
6. **Add `@ActiveProfiles("test")` to existing tests**: `CartServiceApplicationTests`, `CartFlowIntegrationTest`. (`CartServiceTest` and `InMemoryCartStoreTest` are pure Mockito/instantiation, no profile needed.)
7. **Update Config Server `cart-service.yml`** with `spring.data.redis.*` properties.
8. **Add `cart-service-docker.yml`** to Config Server (Phase 12 readiness — `host: redis`).
9. **Write `RedisCartStoreTest`** with Testcontainers Redis: save/get round-trip, get-empty-on-miss, clear, multi-user isolation, TTL set.
10. **Run full reactor `mvn clean verify`** — all modules pass; Spotless clean. Apply formatting if needed.
11. **Update README** — Phase 4 status ✅, Redis row in module/run instructions.
12. **Write Turkish learning notes** `docs/learning/phase-4-notes.md` covering Strategy via Profile, Lettuce vs Jedis, Jackson record serialization, TTL strategy, Testcontainers `@DynamicPropertySource`, mock interview Q&A.
13. **Commit, tag `phase-4-complete`, push to main + tag**.

Each task ends with a focused commit + push.
