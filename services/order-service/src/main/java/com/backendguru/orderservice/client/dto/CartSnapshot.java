package com.backendguru.orderservice.client.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartSnapshot(
    Long userId, List<CartItemSnapshot> items, int itemCount, BigDecimal totalAmount) {

  public record CartItemSnapshot(
      Long productId,
      String productName,
      BigDecimal priceAmount,
      String priceCurrency,
      int quantity,
      BigDecimal lineTotal,
      Long listingId,
      Long sellerId,
      String sellerName) {

    /** Convenience constructor for non-marketplace lines (tests + legacy paths). */
    public CartItemSnapshot(
        Long productId,
        String productName,
        BigDecimal priceAmount,
        String priceCurrency,
        int quantity,
        BigDecimal lineTotal) {
      this(
          productId,
          productName,
          priceAmount,
          priceCurrency,
          quantity,
          lineTotal,
          null,
          null,
          null);
    }
  }
}
