# Phase 3 — Inter-Service Communication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `services/cart-service` (port 8083) with an in-memory cart store and a Feign client to `product-service` wrapped in Resilience4j (CircuitBreaker + Retry + TimeLimiter).

**Architecture:** Cart Service trusts gateway-supplied `X-User-Id` / `X-User-Role` headers (same model as Product Service). `CartStore` interface with `InMemoryCartStore` implementation lets Phase 4 swap to Redis without service-layer changes. `ProductClient` (Feign) is wrapped in Resilience4j; failures return a fallback that throws `ProductUnavailableException` → HTTP 503 in shared `ErrorResponse` shape.

**Tech Stack:** Spring Boot 3.4 · Spring Cloud 2024.0 · OpenFeign · Resilience4j 2.x · jjwt 0.12.6 (gateway only) · WireMock 3.6 (tests) · Testcontainers (not needed — in-memory store, mocked Feign in tests)

**Spec:** `docs/superpowers/specs/2026-05-01-phase-3-inter-service-communication-design.md`

---

## File Structure

Files created/modified by this plan:

```
springboot-ecommerce/
├── pom.xml                                                  # MODIFY: add services/cart-service module
├── README.md                                                # MODIFY: roadmap status, add cart curl examples
├── docs/learning/phase-3-notes.md                           # CREATE: Turkish learning notes
├── infrastructure/
│   ├── api-gateway/src/main/java/com/backendguru/apigateway/jwt/
│   │   └── GatewayJwtAuthenticationFilter.java              # READ ONLY (no change — /api/cart already covered)
│   └── config-server/src/main/resources/configs/
│       ├── cart-service.yml                                 # CREATE: port + Resilience4j config
│       ├── cart-service-dev.yml                             # CREATE: log levels
│       └── api-gateway.yml                                  # MODIFY: add cart-service route
└── services/cart-service/                                   # CREATE
    ├── pom.xml
    └── src/
        ├── main/
        │   ├── java/com/backendguru/cartservice/
        │   │   ├── CartServiceApplication.java
        │   │   ├── auth/
        │   │   │   ├── HeaderAuthenticationFilter.java
        │   │   │   └── SecurityConfig.java
        │   │   ├── cart/
        │   │   │   ├── Cart.java                            # record
        │   │   │   ├── CartItem.java                        # record
        │   │   │   ├── CartStore.java                       # interface
        │   │   │   ├── InMemoryCartStore.java               # ConcurrentHashMap impl
        │   │   │   ├── CartService.java
        │   │   │   ├── CartController.java
        │   │   │   └── dto/
        │   │   │       ├── CartResponse.java
        │   │   │       ├── CartItemResponse.java
        │   │   │       ├── AddItemRequest.java
        │   │   │       └── UpdateItemRequest.java
        │   │   ├── product/
        │   │   │   ├── ProductClient.java                   # @FeignClient
        │   │   │   ├── ProductClientFallbackFactory.java
        │   │   │   └── dto/ProductSnapshot.java
        │   │   ├── exception/
        │   │   │   ├── GlobalExceptionHandler.java
        │   │   │   └── ProductUnavailableException.java
        │   │   └── config/OpenApiConfig.java
        │   └── resources/
        │       ├── application.yml
        │       └── logback-spring.xml
        └── test/java/com/backendguru/cartservice/
            ├── CartServiceApplicationTests.java
            ├── cart/CartTest.java                           # record behavior unit test
            ├── cart/InMemoryCartStoreTest.java              # store unit test
            ├── cart/CartServiceTest.java                    # service unit test (mocked Feign)
            └── CartFlowIntegrationTest.java                 # WireMock + MockMvc end-to-end
```

**Module dependency graph:**
- `cart-service` → `common` (ApiResponse, ErrorResponse, BusinessException, SERVICE_UNAVAILABLE)
- `cart-service` → `product-service` (only via Feign + Eureka discovery; no direct Maven dependency)

---

## Task 1: cart-service module skeleton (pom.xml)

**Files:**
- Create: `services/cart-service/pom.xml`
- Modify: `pom.xml` (root) — add `<module>services/cart-service</module>`

- [ ] **Step 1: Create directory tree**

Run:
```bash
mkdir -p services/cart-service/src/main/java/com/backendguru/cartservice/{auth,cart/dto,product/dto,exception,config} services/cart-service/src/main/resources services/cart-service/src/test/java/com/backendguru/cartservice/cart
```

- [ ] **Step 2: Write `services/cart-service/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.backendguru</groupId>
        <artifactId>springboot-ecommerce-parent</artifactId>
        <version>0.0.1-SNAPSHOT</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>cart-service</artifactId>
    <packaging>jar</packaging>
    <name>cart-service</name>

    <dependencies>
        <dependency><groupId>com.backendguru</groupId><artifactId>common</artifactId></dependency>

        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>

        <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-config</artifactId></dependency>
        <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-netflix-eureka-client</artifactId></dependency>
        <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-openfeign</artifactId></dependency>
        <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-loadbalancer</artifactId></dependency>
        <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId></dependency>
        <dependency><groupId>io.github.resilience4j</groupId><artifactId>resilience4j-spring-boot3</artifactId></dependency>

        <dependency><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId><scope>provided</scope></dependency>
        <dependency><groupId>net.logstash.logback</groupId><artifactId>logstash-logback-encoder</artifactId></dependency>

        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>2.6.0</version>
        </dependency>

        <!-- Test -->
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
        <dependency><groupId>org.springframework.security</groupId><artifactId>spring-security-test</artifactId><scope>test</scope></dependency>
        <dependency>
            <groupId>org.wiremock.integrations</groupId>
            <artifactId>wiremock-spring-boot</artifactId>
            <version>3.6.0</version>
            <scope>test</scope>
        </dependency>
        <dependency><groupId>com.jayway.jsonpath</groupId><artifactId>json-path</artifactId><scope>test</scope></dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 3: Add module to root `pom.xml`**

Open `pom.xml` and find the `<modules>` block. Append `<module>services/cart-service</module>` after `<module>services/product-service</module>`:

```xml
<modules>
    <module>shared/common</module>
    <module>infrastructure/config-server</module>
    <module>infrastructure/discovery-server</module>
    <module>infrastructure/api-gateway</module>
    <module>services/user-service</module>
    <module>services/product-service</module>
    <module>services/cart-service</module>
</modules>
```

- [ ] **Step 4: Verify compile**

Run: `mvn -pl services/cart-service -am compile`
Expected: `BUILD SUCCESS`. Module is empty but resolves all dependencies.

- [ ] **Step 5: Commit**

```bash
git add pom.xml services/cart-service/pom.xml
git commit -m "feat(cart-service): add module skeleton (Feign, Resilience4j, WireMock)"
```

---

## Task 2: Application class + base config + logback

**Files:**
- Create: `services/cart-service/src/main/java/com/backendguru/cartservice/CartServiceApplication.java`
- Create: `services/cart-service/src/main/resources/application.yml`
- Create: `services/cart-service/src/main/resources/logback-spring.xml`

- [ ] **Step 1: Write `CartServiceApplication.java`**

```java
package com.backendguru.cartservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableDiscoveryClient
@EnableFeignClients
@SpringBootApplication(
    scanBasePackages = {"com.backendguru.cartservice", "com.backendguru.common"})
public class CartServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(CartServiceApplication.class, args);
  }
}
```

- [ ] **Step 2: Write `application.yml`**

```yaml
spring:
  application:
    name: cart-service
  profiles:
    active: dev
  config:
    import: optional:configserver:http://localhost:8888
  cloud:
    config:
      fail-fast: false
      retry:
        max-attempts: 3
        initial-interval: 1000
```

- [ ] **Step 3: Write `logback-spring.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <springProperty scope="context" name="serviceName" source="spring.application.name" defaultValue="unknown"/>

    <springProfile name="dev">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} [%X{traceId:-}] - %msg%n</pattern>
            </encoder>
        </appender>
    </springProfile>

    <springProfile name="docker,prod">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LogstashEncoder">
                <customFields>{"service":"${serviceName}"}</customFields>
                <includeMdcKeyName>traceId</includeMdcKeyName>
                <includeMdcKeyName>userId</includeMdcKeyName>
            </encoder>
        </appender>
    </springProfile>

    <springProfile name="!dev &amp; !docker &amp; !prod">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} [%X{traceId:-}] - %msg%n</pattern>
            </encoder>
        </appender>
    </springProfile>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

- [ ] **Step 4: Verify compile**

Run: `mvn -pl services/cart-service compile`
Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add services/cart-service/src/main/
git commit -m "feat(cart-service): add application class with @EnableFeignClients and base config"
```

---

## Task 3: Config Server entries with Resilience4j config

**Files:**
- Create: `infrastructure/config-server/src/main/resources/configs/cart-service.yml`
- Create: `infrastructure/config-server/src/main/resources/configs/cart-service-dev.yml`

- [ ] **Step 1: Write `cart-service.yml`**

```yaml
server:
  port: 8083

spring:
  cloud:
    openfeign:
      circuitbreaker:
        enabled: true

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

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,refresh,circuitbreakers,retries
  health:
    circuitbreakers:
      enabled: true
```

- [ ] **Step 2: Write `cart-service-dev.yml`**

```yaml
logging:
  level:
    io.github.resilience4j: DEBUG
    feign: INFO
```

- [ ] **Step 3: Commit**

```bash
git add infrastructure/config-server/src/main/resources/configs/cart-service*.yml
git commit -m "feat(config-server): add cart-service Resilience4j config (CB + Retry + TimeLimiter)"
```

---

## Task 4: `CartItem` record (TDD)

**Files:**
- Create: `services/cart-service/src/main/java/com/backendguru/cartservice/cart/CartItem.java`
- Test: `services/cart-service/src/test/java/com/backendguru/cartservice/cart/CartTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.backendguru.cartservice.cart;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CartTest {

  @Test
  void cartItemWithQuantityReturnsNewItem() {
    var item = new CartItem(1L, "Widget", new BigDecimal("10.00"), "TRY", 2);
    var updated = item.withQuantity(5);

    assertThat(updated.quantity()).isEqualTo(5);
    assertThat(updated.productId()).isEqualTo(1L);
    assertThat(updated.productName()).isEqualTo("Widget");
    assertThat(item.quantity()).isEqualTo(2);
  }

  @Test
  void cartItemLineTotalMultipliesPriceByQuantity() {
    var item = new CartItem(1L, "Widget", new BigDecimal("10.00"), "TRY", 3);
    assertThat(item.lineTotal()).isEqualByComparingTo(new BigDecimal("30.00"));
  }
}
```

- [ ] **Step 2: Run test — must fail**

Run: `mvn -pl services/cart-service test -Dtest=CartTest`
Expected: FAIL — `CartItem` class not found.

- [ ] **Step 3: Implement `CartItem`**

```java
package com.backendguru.cartservice.cart;

import java.math.BigDecimal;

public record CartItem(
    Long productId,
    String productName,
    BigDecimal priceAmount,
    String priceCurrency,
    int quantity) {

  public CartItem withQuantity(int newQuantity) {
    return new CartItem(productId, productName, priceAmount, priceCurrency, newQuantity);
  }

  public BigDecimal lineTotal() {
    return priceAmount.multiply(BigDecimal.valueOf(quantity));
  }
}
```

- [ ] **Step 4: Run test — must pass**

Run: `mvn -pl services/cart-service test -Dtest=CartTest`
Expected: PASS — 2 tests green.

- [ ] **Step 5: Commit**

```bash
git add services/cart-service/src/main/java/com/backendguru/cartservice/cart/CartItem.java services/cart-service/src/test/java/com/backendguru/cartservice/cart/CartTest.java
git commit -m "feat(cart-service): add immutable CartItem record"
```

---

## Task 5: `Cart` record with mutation helpers (TDD)

**Files:**
- Create: `services/cart-service/src/main/java/com/backendguru/cartservice/cart/Cart.java`
- Modify: `services/cart-service/src/test/java/com/backendguru/cartservice/cart/CartTest.java`

- [ ] **Step 1: Append failing tests to `CartTest`**

Append (inside class `CartTest`):

```java
  @Test
  void emptyCartHasNoItemsAndZeroTotal() {
    var cart = Cart.empty(42L);
    assertThat(cart.userId()).isEqualTo(42L);
    assertThat(cart.items()).isEmpty();
    assertThat(cart.totalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void upsertItemAddsNewProduct() {
    var cart = Cart.empty(1L);
    var item = new CartItem(10L, "Widget", new BigDecimal("5.00"), "TRY", 2);

    var updated = cart.upsertItem(item);

    assertThat(updated.items()).hasSize(1);
    assertThat(updated.items().get(0).quantity()).isEqualTo(2);
  }

  @Test
  void upsertItemMergesQuantityForExistingProduct() {
    var cart =
        Cart.empty(1L).upsertItem(new CartItem(10L, "Widget", new BigDecimal("5.00"), "TRY", 2));

    var updated =
        cart.upsertItem(new CartItem(10L, "Widget", new BigDecimal("5.00"), "TRY", 3));

    assertThat(updated.items()).hasSize(1);
    assertThat(updated.items().get(0).quantity()).isEqualTo(5);
  }

  @Test
  void upsertItemAppendsDifferentProduct() {
    var cart =
        Cart.empty(1L).upsertItem(new CartItem(10L, "Widget", new BigDecimal("5.00"), "TRY", 1));

    var updated =
        cart.upsertItem(new CartItem(20L, "Gadget", new BigDecimal("8.00"), "TRY", 1));

    assertThat(updated.items()).hasSize(2);
  }

  @Test
  void removeItemEliminatesByProductId() {
    var cart =
        Cart.empty(1L)
            .upsertItem(new CartItem(10L, "A", new BigDecimal("1.00"), "TRY", 1))
            .upsertItem(new CartItem(20L, "B", new BigDecimal("2.00"), "TRY", 1));

    var updated = cart.removeItem(10L);

    assertThat(updated.items()).hasSize(1);
    assertThat(updated.items().get(0).productId()).isEqualTo(20L);
  }

  @Test
  void updateQuantityZeroRemovesItem() {
    var cart =
        Cart.empty(1L).upsertItem(new CartItem(10L, "A", new BigDecimal("1.00"), "TRY", 5));

    var updated = cart.updateQuantity(10L, 0);

    assertThat(updated.items()).isEmpty();
  }

  @Test
  void updateQuantityChangesQuantityWhenPositive() {
    var cart =
        Cart.empty(1L).upsertItem(new CartItem(10L, "A", new BigDecimal("1.00"), "TRY", 5));

    var updated = cart.updateQuantity(10L, 7);

    assertThat(updated.items().get(0).quantity()).isEqualTo(7);
  }

  @Test
  void totalAmountSumsLineTotals() {
    var cart =
        Cart.empty(1L)
            .upsertItem(new CartItem(10L, "A", new BigDecimal("10.00"), "TRY", 2))
            .upsertItem(new CartItem(20L, "B", new BigDecimal("3.50"), "TRY", 4));

    assertThat(cart.totalAmount()).isEqualByComparingTo(new BigDecimal("34.00"));
  }
```

- [ ] **Step 2: Run test — must fail**

Run: `mvn -pl services/cart-service test -Dtest=CartTest`
Expected: FAIL — `Cart` class missing.

- [ ] **Step 3: Implement `Cart`**

```java
package com.backendguru.cartservice.cart;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public record Cart(Long userId, List<CartItem> items, Instant updatedAt) {

  public static Cart empty(Long userId) {
    return new Cart(userId, List.of(), Instant.now());
  }

  public Cart upsertItem(CartItem incoming) {
    List<CartItem> next = new ArrayList<>(items.size() + 1);
    boolean merged = false;
    for (CartItem existing : items) {
      if (existing.productId().equals(incoming.productId())) {
        next.add(existing.withQuantity(existing.quantity() + incoming.quantity()));
        merged = true;
      } else {
        next.add(existing);
      }
    }
    if (!merged) next.add(incoming);
    return new Cart(userId, List.copyOf(next), Instant.now());
  }

  public Cart updateQuantity(Long productId, int quantity) {
    if (quantity <= 0) return removeItem(productId);
    List<CartItem> next = new ArrayList<>(items.size());
    for (CartItem it : items) {
      next.add(it.productId().equals(productId) ? it.withQuantity(quantity) : it);
    }
    return new Cart(userId, List.copyOf(next), Instant.now());
  }

  public Cart removeItem(Long productId) {
    List<CartItem> next = items.stream().filter(it -> !it.productId().equals(productId)).toList();
    return new Cart(userId, next, Instant.now());
  }

  public BigDecimal totalAmount() {
    return items.stream().map(CartItem::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
```

- [ ] **Step 4: Run test — must pass**

Run: `mvn -pl services/cart-service test -Dtest=CartTest`
Expected: PASS — 10 tests green.

- [ ] **Step 5: Commit**

```bash
git add services/cart-service/src/main/java/com/backendguru/cartservice/cart/Cart.java services/cart-service/src/test/java/com/backendguru/cartservice/cart/CartTest.java
git commit -m "feat(cart-service): add immutable Cart record with upsert/update/remove/total"
```

---

## Task 6: `CartStore` interface + `InMemoryCartStore` (TDD)

**Files:**
- Create: `services/cart-service/src/main/java/com/backendguru/cartservice/cart/CartStore.java`
- Create: `services/cart-service/src/main/java/com/backendguru/cartservice/cart/InMemoryCartStore.java`
- Test: `services/cart-service/src/test/java/com/backendguru/cartservice/cart/InMemoryCartStoreTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.backendguru.cartservice.cart;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryCartStoreTest {

  private InMemoryCartStore store;

  @BeforeEach
  void setUp() {
    store = new InMemoryCartStore();
  }

  @Test
  void getReturnsEmptyCartForUnknownUser() {
    var cart = store.get(99L);
    assertThat(cart.userId()).isEqualTo(99L);
    assertThat(cart.items()).isEmpty();
  }

  @Test
  void saveThenGetReturnsSameCart() {
    var cart =
        Cart.empty(1L).upsertItem(new CartItem(10L, "A", new BigDecimal("5.00"), "TRY", 2));
    store.save(cart);

    var loaded = store.get(1L);
    assertThat(loaded.items()).hasSize(1);
    assertThat(loaded.items().get(0).productId()).isEqualTo(10L);
  }

  @Test
  void clearRemovesUsersCart() {
    store.save(Cart.empty(1L).upsertItem(new CartItem(10L, "A", new BigDecimal("5.00"), "TRY", 1)));
    store.clear(1L);
    assertThat(store.get(1L).items()).isEmpty();
  }

  @Test
  void differentUsersHaveIndependentCarts() {
    store.save(Cart.empty(1L).upsertItem(new CartItem(10L, "A", new BigDecimal("5.00"), "TRY", 1)));
    store.save(Cart.empty(2L).upsertItem(new CartItem(20L, "B", new BigDecimal("8.00"), "TRY", 2)));

    assertThat(store.get(1L).items().get(0).productId()).isEqualTo(10L);
    assertThat(store.get(2L).items().get(0).productId()).isEqualTo(20L);
  }
}
```

- [ ] **Step 2: Run test — must fail**

Run: `mvn -pl services/cart-service test -Dtest=InMemoryCartStoreTest`
Expected: FAIL — `CartStore` and `InMemoryCartStore` not found.

- [ ] **Step 3: Implement `CartStore`**

```java
package com.backendguru.cartservice.cart;

public interface CartStore {

  Cart get(Long userId);

  Cart save(Cart cart);

  void clear(Long userId);
}
```

- [ ] **Step 4: Implement `InMemoryCartStore`**

```java
package com.backendguru.cartservice.cart;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryCartStore implements CartStore {

  private final ConcurrentMap<Long, Cart> store = new ConcurrentHashMap<>();

  @Override
  public Cart get(Long userId) {
    return store.computeIfAbsent(userId, Cart::empty);
  }

  @Override
  public Cart save(Cart cart) {
    store.put(cart.userId(), cart);
    return cart;
  }

  @Override
  public void clear(Long userId) {
    store.remove(userId);
  }
}
```

- [ ] **Step 5: Run test — must pass**

Run: `mvn -pl services/cart-service test -Dtest=InMemoryCartStoreTest`
Expected: PASS — 4 tests green.

- [ ] **Step 6: Commit**

```bash
git add services/cart-service/src/main/java/com/backendguru/cartservice/cart/CartStore.java services/cart-service/src/main/java/com/backendguru/cartservice/cart/InMemoryCartStore.java services/cart-service/src/test/java/com/backendguru/cartservice/cart/InMemoryCartStoreTest.java
git commit -m "feat(cart-service): add CartStore interface and in-memory implementation"
```

---

## Task 7: `ProductSnapshot` DTO + `ProductClient` Feign interface

**Files:**
- Create: `services/cart-service/src/main/java/com/backendguru/cartservice/product/dto/ProductSnapshot.java`
- Create: `services/cart-service/src/main/java/com/backendguru/cartservice/product/ProductClient.java`

- [ ] **Step 1: Write `ProductSnapshot.java`**

```java
package com.backendguru.cartservice.product.dto;

import java.math.BigDecimal;

public record ProductSnapshot(
    Long id,
    String name,
    BigDecimal priceAmount,
    String priceCurrency,
    int stockQuantity,
    boolean enabled) {}
```

- [ ] **Step 2: Write `ProductClient.java`**

```java
package com.backendguru.cartservice.product;

import com.backendguru.cartservice.product.dto.ProductSnapshot;
import com.backendguru.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "product-service", fallbackFactory = ProductClientFallbackFactory.class)
public interface ProductClient {

  @GetMapping("/api/products/{id}")
  ApiResponse<ProductSnapshot> getById(@PathVariable("id") Long id);
}
```

This will not compile yet — `ProductClientFallbackFactory` is added in Task 8.

- [ ] **Step 3: Commit (deferred verification — Task 8 makes it compile)**

```bash
git add services/cart-service/src/main/java/com/backendguru/cartservice/product/
git commit -m "feat(cart-service): add ProductSnapshot DTO and ProductClient Feign interface"
```

---

## Task 8: `ProductUnavailableException` + `ProductClientFallbackFactory`

**Files:**
- Create: `services/cart-service/src/main/java/com/backendguru/cartservice/exception/ProductUnavailableException.java`
- Create: `services/cart-service/src/main/java/com/backendguru/cartservice/product/ProductClientFallbackFactory.java`

- [ ] **Step 1: Write `ProductUnavailableException`**

```java
package com.backendguru.cartservice.exception;

import com.backendguru.common.error.BusinessException;
import com.backendguru.common.error.ErrorCode;

public class ProductUnavailableException extends BusinessException {

  public ProductUnavailableException(String message) {
    super(ErrorCode.SERVICE_UNAVAILABLE, message);
  }

  public ProductUnavailableException(String message, Throwable cause) {
    super(ErrorCode.SERVICE_UNAVAILABLE, message, cause);
  }
}
```

- [ ] **Step 2: Write `ProductClientFallbackFactory`**

```java
package com.backendguru.cartservice.product;

import com.backendguru.cartservice.exception.ProductUnavailableException;
import com.backendguru.cartservice.product.dto.ProductSnapshot;
import com.backendguru.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
@Slf4j
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

- [ ] **Step 3: Verify compile**

Run: `mvn -pl services/cart-service compile`
Expected: `BUILD SUCCESS`. Tasks 7+8 now compile together.

- [ ] **Step 4: Commit**

```bash
git add services/cart-service/src/main/java/com/backendguru/cartservice/exception/ProductUnavailableException.java services/cart-service/src/main/java/com/backendguru/cartservice/product/ProductClientFallbackFactory.java
git commit -m "feat(cart-service): add ProductUnavailableException and Feign fallback factory"
```

---

## Task 9: `CartService` with mocked `ProductClient` (TDD)

**Files:**
- Create: `services/cart-service/src/main/java/com/backendguru/cartservice/cart/CartService.java`
- Test: `services/cart-service/src/test/java/com/backendguru/cartservice/cart/CartServiceTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.backendguru.cartservice.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.backendguru.cartservice.exception.ProductUnavailableException;
import com.backendguru.cartservice.product.ProductClient;
import com.backendguru.cartservice.product.dto.ProductSnapshot;
import com.backendguru.common.dto.ApiResponse;
import com.backendguru.common.error.ResourceNotFoundException;
import com.backendguru.common.error.ValidationException;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

  @Mock ProductClient productClient;
  CartStore store;
  CartService service;

  @BeforeEach
  void setUp() {
    store = new InMemoryCartStore();
    service = new CartService(store, productClient);
  }

  private void stubProduct(Long id, int stock, boolean enabled) {
    when(productClient.getById(id))
        .thenReturn(
            ApiResponse.success(
                new ProductSnapshot(id, "Widget", new BigDecimal("10.00"), "TRY", stock, enabled)));
  }

  @Test
  void addItemFetchesProductAndStoresCart() {
    stubProduct(1L, 50, true);
    var cart = service.addItem(42L, 1L, 2);

    assertThat(cart.userId()).isEqualTo(42L);
    assertThat(cart.items()).hasSize(1);
    assertThat(cart.items().get(0).productId()).isEqualTo(1L);
    assertThat(cart.items().get(0).quantity()).isEqualTo(2);
    assertThat(cart.items().get(0).priceAmount()).isEqualByComparingTo(new BigDecimal("10.00"));
  }

  @Test
  void addItemMergesSameProduct() {
    stubProduct(1L, 50, true);
    service.addItem(42L, 1L, 2);
    var cart = service.addItem(42L, 1L, 3);

    assertThat(cart.items()).hasSize(1);
    assertThat(cart.items().get(0).quantity()).isEqualTo(5);
  }

  @Test
  void addItemRejectsNonPositiveQuantity() {
    assertThatThrownBy(() -> service.addItem(42L, 1L, 0))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("quantity must be positive");
  }

  @Test
  void addItemRejectsDisabledProduct() {
    stubProduct(1L, 50, false);
    assertThatThrownBy(() -> service.addItem(42L, 1L, 1))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("not available");
  }

  @Test
  void addItemRejectsInsufficientStock() {
    stubProduct(1L, 1, true);
    assertThatThrownBy(() -> service.addItem(42L, 1L, 5))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("Insufficient stock");
  }

  @Test
  void addItemPropagatesProductUnavailableFromFallback() {
    when(productClient.getById(99L))
        .thenThrow(new ProductUnavailableException("circuit open"));

    assertThatThrownBy(() -> service.addItem(42L, 99L, 1))
        .isInstanceOf(ProductUnavailableException.class);
  }

  @Test
  void differentUsersHaveIndependentCarts() {
    stubProduct(1L, 50, true);
    service.addItem(1L, 1L, 1);
    service.addItem(2L, 1L, 5);

    assertThat(service.getCart(1L).items().get(0).quantity()).isEqualTo(1);
    assertThat(service.getCart(2L).items().get(0).quantity()).isEqualTo(5);
  }

  @Test
  void updateQuantityZeroRemovesItem() {
    stubProduct(1L, 50, true);
    service.addItem(42L, 1L, 3);

    var cart = service.updateQuantity(42L, 1L, 0);
    assertThat(cart.items()).isEmpty();
  }

  @Test
  void updateQuantityChangesQuantity() {
    stubProduct(1L, 50, true);
    service.addItem(42L, 1L, 3);

    var cart = service.updateQuantity(42L, 1L, 7);
    assertThat(cart.items().get(0).quantity()).isEqualTo(7);
  }

  @Test
  void updateQuantityOnAbsentItemThrowsResourceNotFound() {
    assertThatThrownBy(() -> service.updateQuantity(42L, 999L, 1))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void removeItemThrowsWhenAbsent() {
    assertThatThrownBy(() -> service.removeItem(42L, 999L))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void clearEmptiesCart() {
    stubProduct(1L, 50, true);
    service.addItem(42L, 1L, 1);
    service.clear(42L);

    assertThat(service.getCart(42L).items()).isEmpty();
  }
}
```

- [ ] **Step 2: Run test — must fail**

Run: `mvn -pl services/cart-service test -Dtest=CartServiceTest`
Expected: FAIL — `CartService` not found.

- [ ] **Step 3: Implement `CartService`**

```java
package com.backendguru.cartservice.cart;

import com.backendguru.cartservice.product.ProductClient;
import com.backendguru.cartservice.product.dto.ProductSnapshot;
import com.backendguru.common.error.ResourceNotFoundException;
import com.backendguru.common.error.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CartService {

  private final CartStore store;
  private final ProductClient productClient;

  public Cart getCart(Long userId) {
    return store.get(userId);
  }

  public Cart addItem(Long userId, Long productId, int quantity) {
    if (quantity <= 0) {
      throw new ValidationException("quantity must be positive");
    }
    ProductSnapshot snap = fetchProduct(productId);
    if (!snap.enabled()) {
      throw new ValidationException("Product " + productId + " is not available");
    }
    if (snap.stockQuantity() < quantity) {
      throw new ValidationException(
          "Insufficient stock for product "
              + productId
              + " (available "
              + snap.stockQuantity()
              + ")");
    }
    Cart updated =
        store
            .get(userId)
            .upsertItem(
                new CartItem(
                    snap.id(),
                    snap.name(),
                    snap.priceAmount(),
                    snap.priceCurrency(),
                    quantity));
    return store.save(updated);
  }

  public Cart updateQuantity(Long userId, Long productId, int quantity) {
    Cart current = store.get(userId);
    if (current.items().stream().noneMatch(it -> it.productId().equals(productId))) {
      throw new ResourceNotFoundException("Item " + productId + " not in cart");
    }
    return store.save(current.updateQuantity(productId, quantity));
  }

  public Cart removeItem(Long userId, Long productId) {
    Cart current = store.get(userId);
    if (current.items().stream().noneMatch(it -> it.productId().equals(productId))) {
      throw new ResourceNotFoundException("Item " + productId + " not in cart");
    }
    return store.save(current.removeItem(productId));
  }

  public void clear(Long userId) {
    store.clear(userId);
  }

  private ProductSnapshot fetchProduct(Long productId) {
    var resp = productClient.getById(productId);
    if (resp == null || resp.data() == null) {
      throw new ResourceNotFoundException("Product " + productId + " not found");
    }
    return resp.data();
  }
}
```

- [ ] **Step 4: Run test — must pass**

Run: `mvn -pl services/cart-service test -Dtest=CartServiceTest`
Expected: PASS — 12 tests green.

- [ ] **Step 5: Commit**

```bash
git add services/cart-service/src/main/java/com/backendguru/cartservice/cart/CartService.java services/cart-service/src/test/java/com/backendguru/cartservice/cart/CartServiceTest.java
git commit -m "feat(cart-service): add CartService with Feign-backed product validation"
```

---

## Task 10: DTOs and `CartController`

**Files:**
- Create: `services/cart-service/src/main/java/com/backendguru/cartservice/cart/dto/{AddItemRequest,UpdateItemRequest,CartItemResponse,CartResponse}.java`
- Create: `services/cart-service/src/main/java/com/backendguru/cartservice/cart/CartController.java`

- [ ] **Step 1: Write `AddItemRequest`**

```java
package com.backendguru.cartservice.cart.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddItemRequest(@NotNull Long productId, @NotNull @Positive Integer quantity) {}
```

- [ ] **Step 2: Write `UpdateItemRequest`**

```java
package com.backendguru.cartservice.cart.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateItemRequest(@NotNull @PositiveOrZero Integer quantity) {}
```

- [ ] **Step 3: Write `CartItemResponse`**

```java
package com.backendguru.cartservice.cart.dto;

import com.backendguru.cartservice.cart.CartItem;
import java.math.BigDecimal;

public record CartItemResponse(
    Long productId,
    String productName,
    BigDecimal priceAmount,
    String priceCurrency,
    int quantity,
    BigDecimal lineTotal) {

  public static CartItemResponse from(CartItem item) {
    return new CartItemResponse(
        item.productId(),
        item.productName(),
        item.priceAmount(),
        item.priceCurrency(),
        item.quantity(),
        item.lineTotal());
  }
}
```

- [ ] **Step 4: Write `CartResponse`**

```java
package com.backendguru.cartservice.cart.dto;

import com.backendguru.cartservice.cart.Cart;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CartResponse(
    Long userId,
    List<CartItemResponse> items,
    int itemCount,
    BigDecimal totalAmount,
    Instant updatedAt) {

  public static CartResponse from(Cart cart) {
    var items = cart.items().stream().map(CartItemResponse::from).toList();
    return new CartResponse(
        cart.userId(), items, items.size(), cart.totalAmount(), cart.updatedAt());
  }
}
```

- [ ] **Step 5: Write `CartController`**

```java
package com.backendguru.cartservice.cart;

import com.backendguru.cartservice.cart.dto.AddItemRequest;
import com.backendguru.cartservice.cart.dto.CartResponse;
import com.backendguru.cartservice.cart.dto.UpdateItemRequest;
import com.backendguru.common.dto.ApiResponse;
import com.backendguru.common.error.UnauthorizedException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

  private final CartService service;

  @GetMapping
  public ApiResponse<CartResponse> getCart(Authentication auth) {
    return ApiResponse.success(CartResponse.from(service.getCart(currentUserId(auth))));
  }

  @PostMapping("/items")
  public ApiResponse<CartResponse> addItem(
      Authentication auth, @Valid @RequestBody AddItemRequest req) {
    return ApiResponse.success(
        CartResponse.from(service.addItem(currentUserId(auth), req.productId(), req.quantity())));
  }

  @PatchMapping("/items/{productId}")
  public ApiResponse<CartResponse> updateItem(
      Authentication auth,
      @PathVariable Long productId,
      @Valid @RequestBody UpdateItemRequest req) {
    return ApiResponse.success(
        CartResponse.from(
            service.updateQuantity(currentUserId(auth), productId, req.quantity())));
  }

  @DeleteMapping("/items/{productId}")
  public ApiResponse<CartResponse> removeItem(Authentication auth, @PathVariable Long productId) {
    return ApiResponse.success(
        CartResponse.from(service.removeItem(currentUserId(auth), productId)));
  }

  @DeleteMapping
  public ResponseEntity<Void> clear(Authentication auth) {
    service.clear(currentUserId(auth));
    return ResponseEntity.noContent().build();
  }

  private Long currentUserId(Authentication auth) {
    if (auth == null || !(auth.getPrincipal() instanceof Long userId)) {
      throw new UnauthorizedException("Authentication required");
    }
    return userId;
  }
}
```

- [ ] **Step 6: Compile**

Run: `mvn -pl services/cart-service compile`
Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add services/cart-service/src/main/java/com/backendguru/cartservice/cart/dto/ services/cart-service/src/main/java/com/backendguru/cartservice/cart/CartController.java
git commit -m "feat(cart-service): add DTOs and CartController endpoints"
```

---

## Task 11: `HeaderAuthenticationFilter` + `SecurityConfig`

**Files:**
- Create: `services/cart-service/src/main/java/com/backendguru/cartservice/auth/HeaderAuthenticationFilter.java`
- Create: `services/cart-service/src/main/java/com/backendguru/cartservice/auth/SecurityConfig.java`

- [ ] **Step 1: Write `HeaderAuthenticationFilter`**

```java
package com.backendguru.cartservice.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Slf4j
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String userId = req.getHeader("X-User-Id");
    String role = req.getHeader("X-User-Role");
    if (userId != null && !userId.isBlank() && role != null && !role.isBlank()) {
      try {
        var auth =
            new UsernamePasswordAuthenticationToken(
                Long.valueOf(userId), null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
      } catch (NumberFormatException e) {
        log.warn("Invalid X-User-Id header value: {}", userId);
      }
    }
    chain.doFilter(req, res);
  }
}
```

- [ ] **Step 2: Write `SecurityConfig`**

```java
package com.backendguru.cartservice.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final HeaderAuthenticationFilter headerFilter;

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(headerFilter, UsernamePasswordAuthenticationFilter.class)
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, ex) -> {
                          res.setStatus(401);
                          res.setContentType("application/json");
                          res.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"status\":401}");
                        })
                    .accessDeniedHandler(
                        (req, res, ex) -> {
                          res.setStatus(403);
                          res.setContentType("application/json");
                          res.getWriter().write("{\"code\":\"FORBIDDEN\",\"status\":403}");
                        }))
        .build();
  }
}
```

- [ ] **Step 3: Compile**

Run: `mvn -pl services/cart-service compile`
Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```bash
git add services/cart-service/src/main/java/com/backendguru/cartservice/auth/
git commit -m "feat(cart-service): add HeaderAuthenticationFilter and SecurityConfig (header trust)"
```

---

## Task 12: `GlobalExceptionHandler` and `OpenApiConfig`

**Files:**
- Create: `services/cart-service/src/main/java/com/backendguru/cartservice/exception/GlobalExceptionHandler.java`
- Create: `services/cart-service/src/main/java/com/backendguru/cartservice/config/OpenApiConfig.java`

- [ ] **Step 1: Write `GlobalExceptionHandler`**

```java
package com.backendguru.cartservice.exception;

import com.backendguru.common.dto.ErrorResponse;
import com.backendguru.common.error.BusinessException;
import com.backendguru.common.error.ErrorCode;
import com.backendguru.common.logging.LoggingConstants;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ErrorResponse> handle(BusinessException ex, HttpServletRequest req) {
    String traceId = MDC.get(LoggingConstants.MDC_TRACE_ID);
    return ResponseEntity.status(ex.getErrorCode().getStatus())
        .body(ErrorResponse.from(ex, req.getRequestURI(), traceId));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest req) {
    Map<String, Object> details =
        ex.getBindingResult().getFieldErrors().stream()
            .collect(
                Collectors.toMap(
                    FieldError::getField,
                    fe -> fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage(),
                    (a, b) -> a));
    String traceId = MDC.get(LoggingConstants.MDC_TRACE_ID);
    return ResponseEntity.badRequest()
        .body(
            ErrorResponse.builder()
                .code(ErrorCode.VALIDATION_FAILED.name())
                .message("Request payload failed validation")
                .status(400)
                .path(req.getRequestURI())
                .traceId(traceId)
                .timestamp(Instant.now())
                .details(details)
                .build());
  }
}
```

- [ ] **Step 2: Write `OpenApiConfig`**

```java
package com.backendguru.cartservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(title = "Cart Service API", version = "v1", description = "Shopping cart"),
    servers = {@Server(url = "/", description = "Default")},
    security = {@SecurityRequirement(name = "bearerAuth")})
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT")
public class OpenApiConfig {}
```

- [ ] **Step 3: Compile**

Run: `mvn -pl services/cart-service compile`
Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```bash
git add services/cart-service/src/main/java/com/backendguru/cartservice/exception/GlobalExceptionHandler.java services/cart-service/src/main/java/com/backendguru/cartservice/config/OpenApiConfig.java
git commit -m "feat(cart-service): add GlobalExceptionHandler and Swagger/OpenAPI config"
```

---

## Task 13: Smoke test (`CartServiceApplicationTests`)

**Files:**
- Create: `services/cart-service/src/test/java/com/backendguru/cartservice/CartServiceApplicationTests.java`

- [ ] **Step 1: Write smoke test**

```java
package com.backendguru.cartservice;

import com.backendguru.cartservice.product.ProductClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(
    properties = {
      "spring.cloud.config.enabled=false",
      "spring.config.import=",
      "eureka.client.enabled=false",
      "spring.cloud.discovery.enabled=false"
    })
class CartServiceApplicationTests {

  @MockBean ProductClient productClient;

  @Test
  void contextLoads() {}
}
```

- [ ] **Step 2: Install common to local repo, then run**

```bash
mvn -pl shared/common install -DskipTests
mvn -pl services/cart-service test -Dtest=CartServiceApplicationTests
```

Expected: PASS — context loads with mocked Feign client (Spring Cloud LoadBalancer would otherwise fail to resolve `lb://product-service` without Eureka).

- [ ] **Step 3: Commit**

```bash
git add services/cart-service/src/test/java/com/backendguru/cartservice/CartServiceApplicationTests.java
git commit -m "test(cart-service): add context-load smoke test"
```

---

## Task 14: WireMock-driven integration test

**Files:**
- Create: `services/cart-service/src/test/java/com/backendguru/cartservice/CartFlowIntegrationTest.java`

This task validates end-to-end: `MockMvc` → controller → service → Feign (Resilience4j wrapped) → WireMock stub. We verify happy path, retry on 5xx, circuit opens after threshold, fallback fires → 503.

- [ ] **Step 1: Write the integration test**

```java
package com.backendguru.cartservice;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;
import com.github.tomakehurst.wiremock.WireMockServer;

@SpringBootTest
@AutoConfigureMockMvc
@EnableWireMock
@TestPropertySource(
    properties = {
      "spring.cloud.config.enabled=false",
      "spring.config.import=",
      "eureka.client.enabled=false",
      "spring.cloud.discovery.enabled=false",
      // Point Feign at WireMock (override lb://product-service)
      "spring.cloud.openfeign.client.config.product-service.url=${wiremock.server.baseUrl}",
      // Tighten retry/timeouts so test runs fast
      "resilience4j.circuitbreaker.instances.productClient.minimum-number-of-calls=2",
      "resilience4j.circuitbreaker.instances.productClient.sliding-window-size=2",
      "resilience4j.retry.instances.productClient.max-attempts=2",
      "resilience4j.retry.instances.productClient.wait-duration=10ms",
      "resilience4j.timelimiter.instances.productClient.timeout-duration=2s"
    })
class CartFlowIntegrationTest {

  @Autowired MockMvc mockMvc;

  @InjectWireMock WireMockServer wm;

  private void stubProduct(long id, int stock, boolean enabled, double price) {
    wm.stubFor(
        get(urlEqualTo("/api/products/" + id))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "success": true,
                          "data": {
                            "id": %d,
                            "name": "Widget",
                            "priceAmount": %.2f,
                            "priceCurrency": "TRY",
                            "stockQuantity": %d,
                            "enabled": %b
                          },
                          "timestamp": "2026-05-01T10:00:00Z"
                        }
                        """
                            .formatted(id, price, stock, enabled))));
  }

  @Test
  void anonymousRequestReturnsUnauthorized() throws Exception {
    mockMvc
        .perform(post("/api/cart/items").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void authenticatedUserCanAddItem() throws Exception {
    stubProduct(1L, 50, true, 10.00);

    mockMvc
        .perform(
            post("/api/cart/items")
                .header("X-User-Id", "42")
                .header("X-User-Role", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":1,\"quantity\":2}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.userId").value(42))
        .andExpect(jsonPath("$.data.items[0].productId").value(1))
        .andExpect(jsonPath("$.data.items[0].quantity").value(2));
  }

  @Test
  void mergesQuantityWhenSameProductAddedTwice() throws Exception {
    stubProduct(2L, 50, true, 5.00);

    mockMvc
        .perform(
            post("/api/cart/items")
                .header("X-User-Id", "100")
                .header("X-User-Role", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":2,\"quantity\":1}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/cart/items")
                .header("X-User-Id", "100")
                .header("X-User-Role", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":2,\"quantity\":3}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()").value(1))
        .andExpect(jsonPath("$.data.items[0].quantity").value(4));
  }

  @Test
  void productServerErrorTriggersFallback503() throws Exception {
    wm.stubFor(
        get(urlEqualTo("/api/products/99"))
            .willReturn(aResponse().withStatus(500).withBody("oops")));

    // Two failures saturate the sliding window of 2 → CB opens, fallback engages
    mockMvc
        .perform(
            post("/api/cart/items")
                .header("X-User-Id", "200")
                .header("X-User-Role", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":99,\"quantity\":1}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));

    mockMvc
        .perform(
            post("/api/cart/items")
                .header("X-User-Id", "201")
                .header("X-User-Role", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":99,\"quantity\":1}"))
        .andExpect(status().isServiceUnavailable());
  }

  @Test
  void disabledProductRejectedWithValidation() throws Exception {
    stubProduct(3L, 50, false, 10.00);

    mockMvc
        .perform(
            post("/api/cart/items")
                .header("X-User-Id", "300")
                .header("X-User-Role", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":3,\"quantity\":1}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void differentUsersHaveIndependentCarts() throws Exception {
    stubProduct(4L, 50, true, 10.00);

    mockMvc
        .perform(
            post("/api/cart/items")
                .header("X-User-Id", "400")
                .header("X-User-Role", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":4,\"quantity\":1}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/cart").header("X-User-Id", "401").header("X-User-Role", "USER"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()").value(0));
  }

  @Test
  void deleteCartClearsItems() throws Exception {
    stubProduct(5L, 50, true, 10.00);
    mockMvc
        .perform(
            post("/api/cart/items")
                .header("X-User-Id", "500")
                .header("X-User-Role", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":5,\"quantity\":1}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(delete("/api/cart").header("X-User-Id", "500").header("X-User-Role", "USER"))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/cart").header("X-User-Id", "500").header("X-User-Role", "USER"))
        .andExpect(jsonPath("$.data.items.length()").value(0));
  }
}
```

- [ ] **Step 2: Run integration test**

Run: `mvn -pl services/cart-service test -Dtest=CartFlowIntegrationTest`
Expected: PASS — 7 tests green. WireMock starts on a random port and Feign is overridden to call it. Each scenario uses a different `X-User-Id` to avoid state leakage.

- [ ] **Step 3: Commit**

```bash
git add services/cart-service/src/test/java/com/backendguru/cartservice/CartFlowIntegrationTest.java
git commit -m "test(cart-service): add WireMock + MockMvc integration test (CB fallback, validation, multi-user)"
```

---

## Task 15: API Gateway route for `/api/cart/**`

**Files:**
- Modify: `infrastructure/config-server/src/main/resources/configs/api-gateway.yml`

- [ ] **Step 1: Add cart route to `api-gateway.yml`**

Open `infrastructure/config-server/src/main/resources/configs/api-gateway.yml` and append this route to the existing `routes:` block (after `product-service`):

```yaml
        - id: cart-service
          uri: lb://cart-service
          predicates:
            - Path=/api/cart/**
```

The full `routes:` block now reads:

```yaml
      routes:
        - id: user-service-public
          uri: lb://user-service
          predicates:
            - Path=/api/auth/**
        - id: user-service-protected
          uri: lb://user-service
          predicates:
            - Path=/api/users/**
        - id: product-service
          uri: lb://product-service
          predicates:
            - Path=/api/products/**
        - id: cart-service
          uri: lb://cart-service
          predicates:
            - Path=/api/cart/**
```

`GatewayJwtAuthenticationFilter` already requires Bearer for any path not in PUBLIC_PREFIXES; `/api/cart/**` is therefore protected by default — no filter change needed.

- [ ] **Step 2: Commit**

```bash
git add infrastructure/config-server/src/main/resources/configs/api-gateway.yml
git commit -m "feat(api-gateway): route /api/cart/** to cart-service (Bearer required)"
```

---

## Task 16: Full reactor build with Spotless

- [ ] **Step 1: Apply Spotless to fix any drift**

Run: `mvn spotless:apply`
Expected: Files reformatted in place; `BUILD SUCCESS`.

- [ ] **Step 2: Full clean verify**

Run: `mvn clean verify`
Expected: 7 modules — common, config-server, discovery-server, api-gateway, user-service, product-service, cart-service — all `SUCCESS`. All Spotless checks pass.

- [ ] **Step 3: Commit any formatting fixes (only if Step 1 changed files)**

```bash
git status
# If files were reformatted:
git add -A
git commit -m "chore: apply Spotless formatting after Phase 3 work"
```

---

## Task 17: Phase 3 docs (Turkish learning notes)

**Files:**
- Create: `docs/learning/phase-3-notes.md`
- Modify: `README.md` (mark Phase 3 ✅)

- [ ] **Step 1: Write Turkish learning notes**

Save to `docs/learning/phase-3-notes.md`. Cover (each section ~150-300 words, with code excerpts and interview questions):
1. Cart Service mimari özeti (header trust, in-memory store, Feign+R4J)
2. **OpenFeign nedir? `RestTemplate` / `WebClient` farkları** — declarative vs imperative
3. **Resilience4j chain order** — `TimeLimiter → Retry → CircuitBreaker → Feign` ve neden bu sırada
4. **Circuit Breaker state machine** — closed, open, half-open; sliding window count vs time
5. **Retry exponential backoff + idempotency** — niye GET'ler retry'lanabilir, POST'lar dikkatli
6. **TimeLimiter vs server-side timeout** — niye ikisi birden
7. **Bulkhead pattern** — semaphore vs threadpool isolation (anlatım, implement etmedik)
8. **Service mesh alternatifi** — Istio/Linkerd library-based resilience yerine
9. **Snapshot pattern** — niye cart'a price + name kopyalıyoruz, checkout'ta re-validate
10. **Strategy pattern via `CartStore`** — Phase 4'te Redis'e geçiş tek satır
11. **Immutable records** — concurrency safety, functional style
12. **WireMock testing** — niye Testcontainer değil, contract test simulation
13. **Kapanış: mülakat hikayesi**

- [ ] **Step 2: Mark Phase 3 ✅ in README**

In `README.md`, change:
```
| 3 | Inter-service communication (Feign, Resilience4j) | upcoming |
```
to:
```
| 3 | Inter-service communication (Feign, Resilience4j) | ✅ |
```

Add cart curl example under the "Try It" section:
```bash
# Add to cart (requires Bearer)
curl -X POST http://localhost:8080/api/cart/items \
  -H "Authorization: Bearer $ACCESS" \
  -H "Content-Type: application/json" \
  -d '{"productId":1,"quantity":2}'

# Get current user's cart
curl http://localhost:8080/api/cart -H "Authorization: Bearer $ACCESS"
```

Also update the module table to add cart-service row:
```
| `services/cart-service` | Spring Boot | 8083 | In-memory cart, Feign client to product-service, Resilience4j |
```

- [ ] **Step 3: Commit + tag + push**

```bash
git add docs/learning/phase-3-notes.md README.md
git commit -m "docs: Phase 3 README + Turkish learning notes for interview prep"
git tag phase-3-complete
git push origin main
git push origin phase-3-complete
```

---

## Task 18: Manual end-to-end smoke (optional but recommended)

This task validates Phase 3 against the running stack with real Postgres + Eureka. Not commit-driven; verification only.

- [ ] **Step 1: Bring up infra**

```bash
docker compose up -d postgres
```

- [ ] **Step 2: Start services in order, each in its own terminal**

```bash
# Terminal 1
mvn -pl infrastructure/config-server spring-boot:run
# Terminal 2 (after :8888 healthy)
mvn -pl infrastructure/discovery-server spring-boot:run
# Terminal 3 (after :8761 up)
mvn -pl services/user-service spring-boot:run
# Terminal 4
mvn -pl services/product-service spring-boot:run
# Terminal 5 (NEW for Phase 3)
mvn -pl services/cart-service spring-boot:run
# Terminal 6
mvn -pl infrastructure/api-gateway spring-boot:run
```

- [ ] **Step 3: Register, login, add to cart**

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"password123"}'

LOGIN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"password123"}')
ACCESS=$(echo "$LOGIN" | jq -r '.data.accessToken')

# Add product 1 (Wireless Headphones from Phase 2 seed)
curl -X POST http://localhost:8080/api/cart/items \
  -H "Authorization: Bearer $ACCESS" \
  -H "Content-Type: application/json" \
  -d '{"productId":1,"quantity":2}'

curl http://localhost:8080/api/cart -H "Authorization: Bearer $ACCESS"
```

Expected: cart contains product 1 with quantity 2 and snapshotted price.

- [ ] **Step 4: Stop product-service, observe circuit breaker behavior**

Stop the product-service terminal. Add another item:
```bash
curl -i -X POST http://localhost:8080/api/cart/items \
  -H "Authorization: Bearer $ACCESS" \
  -H "Content-Type: application/json" \
  -d '{"productId":2,"quantity":1}'
```
After ≥5 failures, expect HTTP 503 with `{"code":"SERVICE_UNAVAILABLE"}`.

- [ ] **Step 5: Stop everything**

```bash
lsof -ti:8080,8081,8082,8083,8761,8888 | xargs -r kill
```

---

## Self-Review

### Spec coverage
| Spec section | Task |
|---|---|
| §1 Goals (Cart Service on 8083, Feign + R4J, in-memory store, snapshot, gateway routing) | Tasks 1–15 |
| §2 Architecture + trust model | Tasks 1, 11, 15 |
| §3 Repository layout | Task 1 (skeleton) — exact dirs match spec §3 |
| §4 Domain (CartItem, Cart, snapshot rationale) | Tasks 4, 5 |
| §5 CartStore abstraction | Task 6 |
| §6 ProductClient + Resilience4j config | Tasks 3, 7, 8 |
| §7 CartService logic (add/update/remove/clear) | Task 9 |
| §8 Endpoints (`/api/cart`, `POST /items`, etc.) | Task 10 |
| §9 Gateway integration | Task 15 |
| §10 Testing matrix (unit + integration with WireMock) | Tasks 4, 5, 6, 9, 13, 14 |
| §11 Acceptance criteria | Tasks 13, 14, 16, 18 |

No gaps.

### Placeholder scan
No "TBD", "TODO", "implement later" or vague guidance. Each step has either complete code or an exact command + expected output.

### Type consistency
- `CartStore.get/save/clear` — same signatures across Tasks 6, 9, and `CartService` usage.
- `ProductSnapshot` fields (id, name, priceAmount, priceCurrency, stockQuantity, enabled) used identically in Tasks 7, 9, 14.
- `Cart.upsertItem(CartItem)` returns `Cart`, used identically in Tasks 5, 9.
- `ProductUnavailableException(message[, cause])` constructors used in Tasks 8, 9, 14.
- `ApiResponse<ProductSnapshot>` Feign return type matches Product Service's wire shape (already used in Phase 2).

All consistent.
