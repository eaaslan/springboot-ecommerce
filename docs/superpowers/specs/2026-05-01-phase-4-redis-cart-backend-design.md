# Phase 4 — Redis Cart Backend Design

**Status:** Approved (autonomous)
**Date:** 2026-05-01
**Phase:** 4 of 12
**Depends on:** Phase 3 (cart-service with `CartStore` interface)

---

## 1. Overview

Swap `InMemoryCartStore` → `RedisCartStore` in `services/cart-service` without touching service-layer code. Strategy pattern (Phase 3) was set up for exactly this. Cart entries persist across restarts and expire after 30 days of inactivity.

### 1.1 Goals

- `RedisCartStore` implements `CartStore` using `RedisTemplate<String, Cart>`
- Profile-based bean selection: `@Profile("test")` keeps `InMemoryCartStore`; everything else (`dev`/`docker`/`prod`) uses Redis
- Existing Phase 3 tests gain `@ActiveProfiles("test")` so they continue without Redis
- Add `redis:7-alpine` to `docker-compose.yml` (port 6379) with healthcheck
- Cart key format: `cart:<userId>`; TTL 30 days, refreshed on every `save`
- Jackson serializer with `JavaTimeModule` to handle `Instant`; `BigDecimal` natively serialized
- New `RedisCartStoreTest` with Testcontainers `redis:7-alpine` validating persistence, get/save/clear, TTL behavior, multi-user isolation
- `CartService` and `CartController` unchanged
- Whole reactor green (`mvn clean verify`) with Redis down and with Redis up

### 1.2 Non-goals

- Cart sharing / merging guest cart with logged-in cart (Phase 5 if desired)
- Pub/Sub for cart events (Phase 7)
- Redis cluster / Sentinel (single-node sufficient for this learning project)
- Cart history / audit trail
- Server-side rendering of cart on Redis cache miss

---

## 2. Architecture

```
┌──────────────────────┐
│   Cart Service 8083  │
│   ┌──────────────┐   │
│   │ CartService  │   │── unchanged from Phase 3
│   │  └─ store    │── @Autowired CartStore
│   └──────────────┘   │
│          │           │
│   ┌──────┴───────┐   │
│   │ RedisCart    │── @Profile("!test")  (default: dev/docker/prod)
│   │   Store      │   │
│   └──────────────┘   │
│   ┌──────────────┐   │
│   │ InMemoryCart │── @Profile("test")
│   │   Store      │   │
│   └──────────────┘   │
└──────────┬───────────┘
           │ RedisTemplate
           ▼
   ┌──────────────────┐
   │ Redis 7-alpine   │
   │ key: cart:<uid>  │
   │ value: Cart JSON │
   │ TTL: 30 days     │
   └──────────────────┘
```

### 2.1 Bean selection mechanics

```java
@Profile("test")
@Component
public class InMemoryCartStore implements CartStore { ... }

@Profile("!test")
@Component
public class RedisCartStore implements CartStore { ... }
```

Spring activates exactly one. Phase 3 tests get `@ActiveProfiles("test")` annotation. Production has no `test` profile → Redis active.

---

## 3. RedisCartStore implementation

```java
@Component
@Profile("!test")
@RequiredArgsConstructor
public class RedisCartStore implements CartStore {

  private static final String KEY_PREFIX = "cart:";
  private static final Duration TTL = Duration.ofDays(30);

  private final RedisTemplate<String, Cart> redisTemplate;

  @Override
  public Cart get(Long userId) {
    Cart cart = redisTemplate.opsForValue().get(key(userId));
    return cart != null ? cart : Cart.empty(userId);
  }

  @Override
  public Cart save(Cart cart) {
    redisTemplate.opsForValue().set(key(cart.userId()), cart, TTL);
    return cart;
  }

  @Override
  public void clear(Long userId) {
    redisTemplate.delete(key(userId));
  }

  private String key(Long userId) {
    return KEY_PREFIX + userId;
  }
}
```

### 3.1 RedisConfig — serializer setup

```java
@Configuration
public class RedisConfig {

  @Bean
  public RedisTemplate<String, Cart> cartRedisTemplate(RedisConnectionFactory cf) {
    RedisTemplate<String, Cart> tpl = new RedisTemplate<>();
    tpl.setConnectionFactory(cf);
    tpl.setKeySerializer(new StringRedisSerializer());

    ObjectMapper om = new ObjectMapper();
    om.registerModule(new JavaTimeModule());
    om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    Jackson2JsonRedisSerializer<Cart> ser = new Jackson2JsonRedisSerializer<>(om, Cart.class);
    tpl.setValueSerializer(ser);

    tpl.afterPropertiesSet();
    return tpl;
  }
}
```

`JavaTimeModule` handles `Instant`. `BigDecimal` and primitives serialize natively as JSON. Records work seamlessly with Jackson 2.16+.

### 3.2 Why Lettuce, not Jedis?

Lettuce is Spring Boot's default Redis client (since 2.0). Netty-based, thread-safe, supports both sync and reactive. Cart Service is Servlet, but Lettuce's sync API fits naturally. Jedis would also work but is older and has thread-safety caveats requiring connection pool management.

---

## 4. Configuration

### 4.1 Config Server `cart-service.yml`

Append to existing config:

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 2s
      lettuce:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 0
```

### 4.2 docker-compose.yml — add Redis

```yaml
  redis:
    image: redis:7-alpine
    container_name: ecommerce-redis
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      retries: 10
```

### 4.3 cart-service-docker.yml (new — for Phase 12 readiness)

Future phase will need `host: redis`. Now add the override file so it's pre-staged.

```yaml
spring:
  data:
    redis:
      host: redis
```

---

## 5. Test strategy

| Test | Profile | Dependencies | Asserts |
|---|---|---|---|
| `CartTest` | n/a (no Spring) | none | Record behavior — unchanged from Phase 3 |
| `InMemoryCartStoreTest` | n/a (direct instantiation) | none | InMemory store — unchanged |
| `CartServiceTest` | n/a (Mockito) | mocked ProductClient + new InMemoryCartStore | Service logic — unchanged |
| `CartServiceApplicationTests` | `@ActiveProfiles("test")` | mocked ProductClient | Context loads with InMemory bean |
| `CartFlowIntegrationTest` | `@ActiveProfiles("test")` | WireMock + InMemory | E2E with WireMock — unchanged |
| **`RedisCartStoreTest`** (new) | default | Testcontainers `redis:7-alpine` | save/get round-trip, get returns empty on miss, clear deletes, TTL set, multi-user isolated |

The new `RedisCartStoreTest`:

```java
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
  "spring.cloud.config.enabled=false",
  "spring.config.import=",
  "eureka.client.enabled=false",
  "spring.cloud.discovery.enabled=false"
})
class RedisCartStoreTest {

  @Container
  static GenericContainer<?> redis =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void redisProps(DynamicPropertyRegistry r) {
    r.add("spring.data.redis.host", redis::getHost);
    r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
  }

  @MockBean ProductClient productClient;
  @Autowired CartStore store;

  // tests for save/get/clear/multi-user
}
```

Spring picks `RedisCartStore` here (no `test` profile active). Mocked ProductClient avoids Eureka/LoadBalancer setup.

---

## 6. Acceptance criteria

- [ ] `mvn clean verify` BUILD SUCCESS for whole reactor (7 modules)
- [ ] All Phase 3 tests still green (with `@ActiveProfiles("test")` added)
- [ ] `RedisCartStoreTest` 5+ tests green using Testcontainers Redis
- [ ] `cart-service` starts with Redis up: `INFO` log shows `Lettuce ... connected to localhost:6379`
- [ ] Cart persists across restart: add item → restart cart-service → cart retains item with correct snapshot price
- [ ] Cart key in Redis is `cart:<userId>`, value is JSON containing items array; `TTL` query returns ~30 days
- [ ] Different users have independent cart entries in Redis
- [ ] Existing Phase 3 endpoints unchanged in behavior

---

## 7. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Jackson can't deserialize `Instant` from epoch-second JSON | Configure `JavaTimeModule` + `disable(WRITE_DATES_AS_TIMESTAMPS)` so it serializes as ISO-8601 string |
| `Cart` record with `List<CartItem>` doesn't deserialize | Records work with Jackson 2.16+ (Spring Boot 3.4 ships 2.18). Default constructor not needed for records. |
| Redis down at startup → cart-service fails to start | Lettuce default is lazy connect; `spring.data.redis.timeout: 2s` means first cart request times out, returns 503 only if CB wired (not done here). For dev OK; production add health-indicator + circuit-breaker (deferred to Phase 8 observability) |
| Testcontainers slow on first run (image pull) | Image cached after first pull; subsequent runs ~2s startup |
| `RedisTemplate<String, Cart>` bean conflict with Spring Boot's auto-configured generic `RedisTemplate<Object, Object>` | Name our bean `cartRedisTemplate` and `@Autowire` by type with explicit cast or qualifier |

---

## 8. Interview topics unlocked

Strategy pattern realized via profile-based bean selection, Redis as cache vs primary store (cart is primary), TTL-based expiration vs LRU eviction, Lettuce vs Jedis, Jackson record serialization with `JavaTimeModule`, key naming conventions (`<entity>:<id>`), Redis data types (used String here; alternatives Hash for partial updates), eventual consistency in cache scenarios (cart is consistent because we own the key), connection pool sizing, cache penetration vs cache stampede, `@DynamicPropertySource` for Testcontainers integration.
