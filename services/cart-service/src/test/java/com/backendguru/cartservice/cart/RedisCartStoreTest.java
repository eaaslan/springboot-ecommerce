package com.backendguru.cartservice.cart;

import static org.assertj.core.api.Assertions.assertThat;

import com.backendguru.cartservice.product.ProductClient;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@TestPropertySource(
    properties = {
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
  @Autowired RedisTemplate<String, Cart> cartRedisTemplate;

  @AfterEach
  void cleanup() {
    var keys = cartRedisTemplate.keys("cart:*");
    if (keys != null && !keys.isEmpty()) cartRedisTemplate.delete(keys);
  }

  @Test
  void usesRedisStoreNotInMemoryWhenTestProfileNotActive() {
    assertThat(store).isInstanceOf(RedisCartStore.class);
  }

  @Test
  void getReturnsEmptyCartForUnknownUser() {
    var cart = store.get(99L);
    assertThat(cart.userId()).isEqualTo(99L);
    assertThat(cart.items()).isEmpty();
  }

  @Test
  void saveAndGetRoundTripsCartThroughRedis() {
    var cart =
        Cart.empty(1L).upsertItem(new CartItem(10L, "Widget", new BigDecimal("5.00"), "TRY", 2));
    store.save(cart);

    var loaded = store.get(1L);
    assertThat(loaded.items()).hasSize(1);
    assertThat(loaded.items().get(0).productId()).isEqualTo(10L);
    assertThat(loaded.items().get(0).quantity()).isEqualTo(2);
    assertThat(loaded.items().get(0).priceAmount()).isEqualByComparingTo(new BigDecimal("5.00"));
    assertThat(loaded.totalAmount()).isEqualByComparingTo(new BigDecimal("10.00"));
  }

  @Test
  void clearDeletesRedisKey() {
    store.save(Cart.empty(2L).upsertItem(new CartItem(10L, "A", new BigDecimal("1.00"), "TRY", 1)));
    assertThat(cartRedisTemplate.hasKey("cart:2")).isTrue();

    store.clear(2L);

    assertThat(cartRedisTemplate.hasKey("cart:2")).isFalse();
  }

  @Test
  void differentUsersHaveIndependentRedisKeys() {
    store.save(Cart.empty(3L).upsertItem(new CartItem(10L, "A", new BigDecimal("1.00"), "TRY", 1)));
    store.save(Cart.empty(4L).upsertItem(new CartItem(20L, "B", new BigDecimal("2.00"), "TRY", 5)));

    assertThat(store.get(3L).items().get(0).productId()).isEqualTo(10L);
    assertThat(store.get(4L).items().get(0).productId()).isEqualTo(20L);
    assertThat(store.get(4L).items().get(0).quantity()).isEqualTo(5);
  }

  @Test
  void saveSetsTtlOnRedisKey() {
    store.save(Cart.empty(5L).upsertItem(new CartItem(10L, "A", new BigDecimal("1.00"), "TRY", 1)));

    Long ttlSeconds = cartRedisTemplate.getExpire("cart:5");

    assertThat(ttlSeconds).isNotNull();
    assertThat(Duration.ofSeconds(ttlSeconds))
        .isLessThanOrEqualTo(Duration.ofDays(30))
        .isGreaterThan(Duration.ofDays(29));
  }
}
