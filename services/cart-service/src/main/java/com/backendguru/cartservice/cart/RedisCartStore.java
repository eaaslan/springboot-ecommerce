package com.backendguru.cartservice.cart;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@RequiredArgsConstructor
public class RedisCartStore implements CartStore {

  private static final String KEY_PREFIX = "cart:";
  private static final Duration TTL = Duration.ofDays(30);

  private final RedisTemplate<String, Cart> cartRedisTemplate;

  @Override
  public Cart get(Long userId) {
    Cart cart = cartRedisTemplate.opsForValue().get(key(userId));
    return cart != null ? cart : Cart.empty(userId);
  }

  @Override
  public Cart save(Cart cart) {
    cartRedisTemplate.opsForValue().set(key(cart.userId()), cart, TTL);
    return cart;
  }

  @Override
  public void clear(Long userId) {
    cartRedisTemplate.delete(key(userId));
  }

  private String key(Long userId) {
    return KEY_PREFIX + userId;
  }
}
