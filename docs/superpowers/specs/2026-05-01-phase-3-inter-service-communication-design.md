# Phase 3 — Inter-Service Communication Design

**Status:** Approved
**Date:** 2026-05-01
**Phase:** 3 of 12
**Depends on:** Phase 0 (foundation), Phase 1 (JWT auth), Phase 2 (Product Service)

---

## 1. Overview

Stand up `services/cart-service` (port 8083) that exercises **OpenFeign** (declarative HTTP client) and **Resilience4j** (Circuit Breaker + Retry + TimeLimiter) by calling Product Service whenever an item is added to the cart. Cart storage is in-memory in this phase via a `CartStore` abstraction; Phase 4 swaps it for Redis without changing the public API.

### 1.1 Goals

- New `cart-service` module on port 8083, registered with Eureka
- `ProductClient` Feign interface declares `GET /api/products/{id}`; Spring Cloud LoadBalancer resolves `lb://product-service` via Eureka
- Resilience4j wraps the Feign call: TimeLimiter (2s) → Retry (max 3, exponential backoff) → CircuitBreaker (50% failure threshold over a sliding window of 10)
- Fallback throws `ProductUnavailableException` → HTTP 503 in shared `ErrorResponse` shape
- `Cart` and `CartItem` are immutable records; mutations return new instances; `CartStore` interface allows Phase 4 to swap to Redis with no service-layer changes
- Snapshot product `name` + `priceAmount` at add-time so cart prices are stable across catalog updates (re-validation at Phase 5 checkout)
- Per-user cart keyed by `X-User-Id` (trust gateway's header, same model as Product Service)
- Integration test uses **WireMock** to simulate success, slow responses (timeout), and 5xx (circuit opens, fallback fires)
- Gateway routes `/api/cart/**` (Bearer required, no public bypass)

### 1.2 Non-goals (deferred)

- Persistent cart storage → Phase 4 (Redis)
- Stock reservation / inventory tracking → Phase 5 (Inventory Service)
- Distributed transactions / Saga → Phase 5
- Cart expiration / TTL → Phase 4 (Redis TTL)
- Bulkhead pattern (resource isolation) → discussed in learning notes only
- RateLimiter on outbound Feign calls → Phase 4 if needed
- Bulk add / promotions / coupon codes → out of scope
- Order conversion → Phase 5

---

## 2. Architecture

```
                       ┌──────────────────────┐
                       │   API Gateway 8080   │   POST /api/cart/items
                       │   JWT validate       │   Authorization: Bearer ...
                       │   X-User-* inject    │   {"productId":1,"quantity":2}
                       └──────────┬───────────┘
                                  │
                                  ▼ (X-User-Id, X-User-Role)
                       ┌──────────────────────┐
                       │   Cart Service 8083  │
                       │   ┌──────────────┐   │
                       │   │ HeaderAuthFil│   │
                       │   │  → Auth ctx  │   │
                       │   └──────┬───────┘   │
                       │          ▼           │
                       │   ┌──────────────┐   │
                       │   │ CartService  │   │
                       │   │  ├── store   │   │── ConcurrentHashMap (in-memory)
                       │   │  └── client  │── Resilience4j wrapped
                       │   └──────┬───────┘   │
                       └──────────┼───────────┘
                                  │ Feign + lb://
                                  ▼
                       ┌──────────────────────┐
                       │ Product Service 8082 │
                       │ GET /api/products/{} │
                       └──────────────────────┘
```

### 2.1 Trust model

Cart Service trusts gateway-supplied `X-User-Id` and `X-User-Role` (same pattern as Product Service from Phase 2). Each request mutates only the cart of `userId = X-User-Id`. Gateway strips inbound `X-User-*` headers from external requests, then re-injects validated values from the parsed JWT. Internal traffic on private network.

---

## 3. Repository layout

```
services/cart-service/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/backendguru/cartservice/
    │   │   ├── CartServiceApplication.java                 // @EnableFeignClients
    │   │   ├── auth/
    │   │   │   ├── HeaderAuthenticationFilter.java         // copied from product-service
    │   │   │   └── SecurityConfig.java                     // all /api/cart/** authenticated
    │   │   ├── cart/
    │   │   │   ├── Cart.java                               // immutable record
    │   │   │   ├── CartItem.java                           // immutable record
    │   │   │   ├── CartStore.java                          // interface
    │   │   │   ├── InMemoryCartStore.java                  // ConcurrentHashMap impl
    │   │   │   ├── CartService.java
    │   │   │   ├── CartController.java
    │   │   │   └── dto/
    │   │   │       ├── CartResponse.java
    │   │   │       ├── CartItemResponse.java
    │   │   │       ├── AddItemRequest.java
    │   │   │       └── UpdateItemRequest.java
    │   │   ├── product/
    │   │   │   ├── ProductClient.java                      // @FeignClient
    │   │   │   ├── ProductClientFallbackFactory.java
    │   │   │   └── dto/ProductSnapshot.java                // minimal, decoupled from product-service
    │   │   ├── exception/
    │   │   │   ├── GlobalExceptionHandler.java
    │   │   │   └── ProductUnavailableException.java
    │   │   └── config/OpenApiConfig.java
    │   └── resources/
    │       ├── application.yml
    │       └── logback-spring.xml
    └── test/java/com/backendguru/cartservice/
        ├── CartServiceApplicationTests.java                // smoke
        ├── cart/CartServiceTest.java                       // unit, mocked ProductClient
        └── CartFlowIntegrationTest.java                    // WireMock + MockMvc
```

---

## 4. Domain model

### 4.1 `CartItem` (record)

```java
public record CartItem(
    Long productId,
    String productName,         // snapshotted at add time
    BigDecimal priceAmount,     // snapshotted at add time
    String priceCurrency,
    int quantity) {

  public CartItem withQuantity(int newQuantity) { ... }
  public BigDecimal lineTotal() { return priceAmount.multiply(BigDecimal.valueOf(quantity)); }
}
```

### 4.2 `Cart` (record)

```java
public record Cart(Long userId, List<CartItem> items, Instant updatedAt) {
  public static Cart empty(Long userId) { ... }
  public Cart upsertItem(CartItem incoming) { /* merges by productId */ }
  public Cart updateQuantity(Long productId, int qty) { /* qty<=0 → removeItem */ }
  public Cart removeItem(Long productId) { ... }
  public BigDecimal totalAmount() { ... }
}
```

All mutations return a new `Cart` with `updatedAt = Instant.now()`. `items` is an unmodifiable list.

### 4.3 Why snapshot price/name?

Cart shows the price the user agreed to. If Product Service later changes the price (admin adjust, sale), existing cart items are stable. Phase 5 checkout re-validates against current Product Service prices and prompts the user if anything changed.

---

## 5. `CartStore` abstraction

```java
public interface CartStore {
  Cart get(Long userId);     // returns empty cart if absent
  Cart save(Cart cart);
  void clear(Long userId);
}
```

Phase 3 implementation:
```java
@Component
public class InMemoryCartStore implements CartStore {
  private final ConcurrentMap<Long, Cart> store = new ConcurrentHashMap<>();
  // ...
}
```

Phase 4 will add `RedisCartStore implements CartStore`. Service layer is unaware of the swap (open/closed principle).

---

## 6. ProductClient (Feign + Resilience4j)

```java
@FeignClient(name = "product-service", fallbackFactory = ProductClientFallbackFactory.class)
public interface ProductClient {
  @GetMapping("/api/products/{id}")
  ApiResponse<ProductSnapshot> getById(@PathVariable("id") Long id);
}
```

`ProductSnapshot` is a minimal record (id, name, priceAmount, priceCurrency, stockQuantity, enabled). It is **not** the Product Service's full `ProductResponse` — Cart Service does not couple to Product Service's domain model.

### 6.1 Fallback factory

Triggers when the Resilience4j chain decides the call has failed (circuit open / retries exhausted / timeout):

```java
@Component
public class ProductClientFallbackFactory implements FallbackFactory<ProductClient> {
  @Override
  public ProductClient create(Throwable cause) {
    log.warn("ProductClient fallback engaged: {}", cause.toString());
    return new ProductClient() {
      @Override
      public ApiResponse<ProductSnapshot> getById(Long id) {
        throw new ProductUnavailableException(
            "Product " + id + " is temporarily unavailable", cause);
      }
    };
  }
}
```

`ProductUnavailableException` extends `BusinessException` with `ErrorCode.SERVICE_UNAVAILABLE` (HTTP 503). Already added to `common` in the recovered codebase.

### 6.2 Resilience4j configuration

In Config Server's `cart-service.yml`:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      productClient:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10
        minimum-number-of-calls: 5
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 3
        automatic-transition-from-open-to-half-open-enabled: true
        register-health-indicator: true
  retry:
    instances:
      productClient:
        max-attempts: 3
        wait-duration: 200ms
        exponential-backoff-multiplier: 2
        retry-exceptions:
          - feign.RetryableException
          - java.io.IOException
  timelimiter:
    instances:
      productClient:
        timeout-duration: 2s
        cancel-running-future: true

spring:
  cloud:
    openfeign:
      circuitbreaker:
        enabled: true
```

### 6.3 Decorator order

Spring Cloud OpenFeign + CircuitBreaker integration wraps each Feign method as: **TimeLimiter → Retry → CircuitBreaker → real Feign call**. Reading inside-out:
- A slow call hits the 2s `TimeLimiter` → counts as failure
- `Retry` re-invokes (up to 3 attempts, 200ms × 2ⁿ backoff)
- If all retries fail, the failure increments `CircuitBreaker`'s window
- After 5 minimum calls, if ≥50% failed, circuit transitions **closed → open**
- 10s wait → **open → half-open**, allows 3 trial calls
- All trials succeed → **half-open → closed**; any fail → **half-open → open** again

---

## 7. CartService logic

### 7.1 Add item

```java
public Cart addItem(Long userId, Long productId, int quantity) {
  if (quantity <= 0) throw new ValidationException("quantity must be positive");
  ProductSnapshot snap = fetchProduct(productId);                          // Feign + Resilience4j
  if (!snap.enabled())                throw new ValidationException("Product not available");
  if (snap.stockQuantity() < quantity) throw new ValidationException("Insufficient stock");
  Cart updated = store.get(userId)
      .upsertItem(new CartItem(snap.id(), snap.name(), snap.priceAmount(),
                               snap.priceCurrency(), quantity));
  return store.save(updated);
}
```

### 7.2 Other operations

- `getCart(userId)` → `store.get(userId)` (returns empty cart if absent)
- `updateQuantity(userId, productId, qty)` → `ResourceNotFoundException` if item not in cart, else call `Cart.updateQuantity` (qty≤0 removes), save
- `removeItem(userId, productId)` → 404 if absent, else `removeItem` + save
- `clear(userId)` → `store.clear(userId)`

---

## 8. Endpoints

| Method | Path | Auth | Body / Params | Response |
|---|---|---|---|---|
| GET | `/api/cart` | Bearer | — | `CartResponse` (items, totals, updatedAt) |
| POST | `/api/cart/items` | Bearer | `{productId, quantity}` | `CartResponse` |
| PATCH | `/api/cart/items/{productId}` | Bearer | `{quantity}` (0 removes) | `CartResponse` |
| DELETE | `/api/cart/items/{productId}` | Bearer | — | `CartResponse` |
| DELETE | `/api/cart` | Bearer | — | `204 No Content` |

Authentication principal: `Long userId = (Long) Authentication.getPrincipal()` populated by `HeaderAuthenticationFilter` from `X-User-Id`. Anonymous request → 401 from `SecurityConfig`'s entry point.

`CartResponse` is the wire-shape:
```java
public record CartResponse(
    Long userId,
    List<CartItemResponse> items,
    int itemCount,
    BigDecimal totalAmount,
    Instant updatedAt) {}
```

All wrapped in `ApiResponse<T>` envelope.

---

## 9. Gateway integration

Add to Config Server `api-gateway.yml`:

```yaml
- id: cart-service
  uri: lb://cart-service
  predicates:
    - Path=/api/cart/**
```

`GatewayJwtAuthenticationFilter` already requires Bearer for any path not in PUBLIC_PREFIXES; `/api/cart/**` is therefore protected by default. No filter change needed.

---

## 10. Testing

| Test | Type | What it asserts |
|---|---|---|
| `CartServiceApplicationTests` | Smoke (`@SpringBootTest`, eureka/config disabled, ProductClient mocked) | Context loads |
| `CartServiceTest` | Unit (Mockito `ProductClient` + `CartStore` real `InMemoryCartStore`) | (a) addItem merges quantity for same productId, (b) addItem with qty≤0 throws ValidationException, (c) addItem when product disabled throws, (d) addItem when stockQuantity < qty throws, (e) ProductUnavailableException propagates from fallback, (f) different userId → independent cart, (g) updateQuantity with qty=0 removes, (h) removeItem on absent product throws ResourceNotFoundException |
| `CartFlowIntegrationTest` | `@SpringBootTest` + WireMock + MockMvc | (a) anonymous → 401, (b) authenticated user happy path: stub product → POST /api/cart/items → 200, (c) WireMock returns 500 → retry × 3 → CB increments → after threshold, fallback fires → 503 with `SERVICE_UNAVAILABLE`, (d) WireMock slow (3s) → TimeLimiter cancels at 2s → counts as failure, (e) two users get isolated carts |

WireMock setup uses `@RegisterExtension WireMockExtension`; Feign is pointed at WireMock by overriding `feign.client.config.product-service.url=http://localhost:${wm.port}` in the test property source, plus `eureka.client.enabled=false` so client doesn't try to discover.

---

## 11. Acceptance criteria

- [ ] `mvn clean verify` BUILD SUCCESS for whole reactor (7 modules)
- [ ] Cart Service starts on :8083, registers with Eureka, picks up Resilience4j config from Config Server
- [ ] `POST /api/cart/items` valid product → 200 + cart contains item with snapshotted price
- [ ] Same productId added twice → quantity merges (one row, summed quantity)
- [ ] Disabled product or insufficient stock → 400 with validation message
- [ ] Product Service stopped → after CB threshold, returns 503 `SERVICE_UNAVAILABLE`
- [ ] WireMock test asserts: success, retry on 5xx, circuit opens, timeout cancels, fallback executes
- [ ] Different `X-User-Id` → different carts (independent state)
- [ ] Swagger UI on :8083 shows endpoints with bearer scheme

---

## 12. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Feign + Spring Boot 3.4 + Spring Cloud 2024.0 compatibility | Already verified jjwt + WebFlux work in Phase 1; pin `spring-cloud-starter-openfeign` from BOM |
| Circuit breaker opens during cold start (no traffic baseline) | `minimum-number-of-calls: 5` prevents premature opening |
| In-memory cart unbounded for inactive users | Phase 4 Redis with TTL; for Phase 3 acceptable since dev-only |
| WireMock port collision in parallel test runs | Use random port (`@WireMockTest` or extension with `dynamicPort()`) |
| `ApiResponse<ProductSnapshot>` deserialization in Feign | Same Jackson `findAndRegisterModules()` pattern; `ApiResponse` is in `common` and present in cart-service classpath |

---

## 13. Interview topics unlocked

OpenFeign declarative client, RestTemplate vs WebClient vs Feign comparison, Resilience4j Circuit Breaker (closed/open/half-open states), failure rate window types (count vs time-based), Retry exponential backoff + jitter, idempotency requirement for Retry, TimeLimiter vs server-side timeout, Bulkhead pattern (semaphore vs threadpool), service mesh (Istio/Linkerd) as library-based resilience alternative, "failure as a feature" / graceful degradation, snapshot vs reference data in carts/orders, CAP / PACELC for cart consistency model, Strategy pattern via `CartStore` interface, WireMock for HTTP contract testing.
