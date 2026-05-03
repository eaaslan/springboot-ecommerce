package com.backendguru.cartservice.cart.dto;

import com.backendguru.cartservice.cart.CartItem;
import java.math.BigDecimal;

public record CartItemResponse(
    Long productId,
    String productName,
    BigDecimal priceAmount,
    String priceCurrency,
    int quantity,
    BigDecimal lineTotal,
    Long listingId,
    Long sellerId,
    String sellerName) {

  public static CartItemResponse from(CartItem item) {
    return new CartItemResponse(
        item.productId(),
        item.productName(),
        item.priceAmount(),
        item.priceCurrency(),
        item.quantity(),
        item.lineTotal(),
        item.listingId(),
        item.sellerId(),
        item.sellerName());
  }
}
