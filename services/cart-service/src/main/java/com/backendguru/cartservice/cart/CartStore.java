package com.backendguru.cartservice.cart;

public interface CartStore {

  Cart get(Long userId);

  Cart save(Cart cart);

  void clear(Long userId);
}
