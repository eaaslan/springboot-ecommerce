package com.backendguru.cartservice.cart;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CartTest {

  @Test
  void cartItemWithQuantityReturnsNewItem() {
    var item = new CartItem(1L, "Widget", new BigDecimal("10.00"), "TRY", 2);
    var updated = item.withQuantity(5);

    assertThat(updated.quantity()).isEqualTo(5);
    assertThat(updated.productId()).isEqualTo(1L);
    assertThat(updated.productName()).isEqualTo("Widget");
    assertThat(item.quantity()).isEqualTo(2);
  }

  @Test
  void cartItemLineTotalMultipliesPriceByQuantity() {
    var item = new CartItem(1L, "Widget", new BigDecimal("10.00"), "TRY", 3);
    assertThat(item.lineTotal()).isEqualByComparingTo(new BigDecimal("30.00"));
  }
}
