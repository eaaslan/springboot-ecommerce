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
