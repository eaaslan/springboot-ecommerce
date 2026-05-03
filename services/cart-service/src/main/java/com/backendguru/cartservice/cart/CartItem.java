package com.backendguru.cartservice.cart;

import java.math.BigDecimal;

/**
 * Cart line. Seller fields ({@code listingId}, {@code sellerId}, {@code sellerName}) are nullable —
 * a missing listing means the line came from the master product (legacy / platform-owned).
 */
public record CartItem(
    Long productId,
    String productName,
    BigDecimal priceAmount,
    String priceCurrency,
    int quantity,
    Long listingId,
    Long sellerId,
    String sellerName) {

  /** Convenience constructor for non-marketplace lines (tests + legacy paths). */
  public CartItem(
      Long productId,
      String productName,
      BigDecimal priceAmount,
      String priceCurrency,
      int quantity) {
    this(productId, productName, priceAmount, priceCurrency, quantity, null, null, null);
  }

  public CartItem withQuantity(int newQuantity) {
    return new CartItem(
        productId,
        productName,
        priceAmount,
        priceCurrency,
        newQuantity,
        listingId,
        sellerId,
        sellerName);
  }

  public BigDecimal lineTotal() {
    return priceAmount.multiply(BigDecimal.valueOf(quantity));
  }
}
