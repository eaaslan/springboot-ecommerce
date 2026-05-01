package com.backendguru.cartservice.cart;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryCartStoreTest {

  private InMemoryCartStore store;

  @BeforeEach
  void setUp() {
    store = new InMemoryCartStore();
  }

  @Test
  void getReturnsEmptyCartForUnknownUser() {
    var cart = store.get(99L);
    assertThat(cart.userId()).isEqualTo(99L);
    assertThat(cart.items()).isEmpty();
  }

  @Test
  void saveThenGetReturnsSameCart() {
    var cart = Cart.empty(1L).upsertItem(new CartItem(10L, "A", new BigDecimal("5.00"), "TRY", 2));
    store.save(cart);

    var loaded = store.get(1L);
    assertThat(loaded.items()).hasSize(1);
    assertThat(loaded.items().get(0).productId()).isEqualTo(10L);
  }

  @Test
  void clearRemovesUsersCart() {
    store.save(Cart.empty(1L).upsertItem(new CartItem(10L, "A", new BigDecimal("5.00"), "TRY", 1)));
    store.clear(1L);
    assertThat(store.get(1L).items()).isEmpty();
  }

  @Test
  void differentUsersHaveIndependentCarts() {
    store.save(Cart.empty(1L).upsertItem(new CartItem(10L, "A", new BigDecimal("5.00"), "TRY", 1)));
    store.save(Cart.empty(2L).upsertItem(new CartItem(20L, "B", new BigDecimal("8.00"), "TRY", 2)));

    assertThat(store.get(1L).items().get(0).productId()).isEqualTo(10L);
    assertThat(store.get(2L).items().get(0).productId()).isEqualTo(20L);
  }
}
