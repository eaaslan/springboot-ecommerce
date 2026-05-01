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
