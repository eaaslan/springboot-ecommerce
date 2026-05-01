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

  @Test
  void emptyCartHasNoItemsAndZeroTotal() {
    var cart = Cart.empty(42L);
    assertThat(cart.userId()).isEqualTo(42L);
    assertThat(cart.items()).isEmpty();
    assertThat(cart.totalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void upsertItemAddsNewProduct() {
    var cart = Cart.empty(1L);
    var item = new CartItem(10L, "Widget", new BigDecimal("5.00"), "TRY", 2);
    var updated = cart.upsertItem(item);
    assertThat(updated.items()).hasSize(1);
    assertThat(updated.items().get(0).quantity()).isEqualTo(2);
  }

  @Test
  void upsertItemMergesQuantityForExistingProduct() {
    var cart =
        Cart.empty(1L).upsertItem(new CartItem(10L, "Widget", new BigDecimal("5.00"), "TRY", 2));
    var updated = cart.upsertItem(new CartItem(10L, "Widget", new BigDecimal("5.00"), "TRY", 3));
    assertThat(updated.items()).hasSize(1);
    assertThat(updated.items().get(0).quantity()).isEqualTo(5);
  }

  @Test
  void upsertItemAppendsDifferentProduct() {
    var cart =
        Cart.empty(1L).upsertItem(new CartItem(10L, "Widget", new BigDecimal("5.00"), "TRY", 1));
    var updated = cart.upsertItem(new CartItem(20L, "Gadget", new BigDecimal("8.00"), "TRY", 1));
    assertThat(updated.items()).hasSize(2);
  }

  @Test
  void removeItemEliminatesByProductId() {
    var cart =
        Cart.empty(1L)
            .upsertItem(new CartItem(10L, "A", new BigDecimal("1.00"), "TRY", 1))
            .upsertItem(new CartItem(20L, "B", new BigDecimal("2.00"), "TRY", 1));
    var updated = cart.removeItem(10L);
    assertThat(updated.items()).hasSize(1);
    assertThat(updated.items().get(0).productId()).isEqualTo(20L);
  }

  @Test
  void updateQuantityZeroRemovesItem() {
    var cart = Cart.empty(1L).upsertItem(new CartItem(10L, "A", new BigDecimal("1.00"), "TRY", 5));
    var updated = cart.updateQuantity(10L, 0);
    assertThat(updated.items()).isEmpty();
  }

  @Test
  void updateQuantityChangesQuantityWhenPositive() {
    var cart = Cart.empty(1L).upsertItem(new CartItem(10L, "A", new BigDecimal("1.00"), "TRY", 5));
    var updated = cart.updateQuantity(10L, 7);
    assertThat(updated.items().get(0).quantity()).isEqualTo(7);
  }

  @Test
  void totalAmountSumsLineTotals() {
    var cart =
        Cart.empty(1L)
            .upsertItem(new CartItem(10L, "A", new BigDecimal("10.00"), "TRY", 2))
            .upsertItem(new CartItem(20L, "B", new BigDecimal("3.50"), "TRY", 4));
    assertThat(cart.totalAmount()).isEqualByComparingTo(new BigDecimal("34.00"));
  }
}
