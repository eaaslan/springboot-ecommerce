package com.backendguru.cartservice.cart;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
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
