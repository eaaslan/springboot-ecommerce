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
