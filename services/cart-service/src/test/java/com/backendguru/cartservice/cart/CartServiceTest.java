package com.backendguru.cartservice.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.backendguru.cartservice.exception.ProductUnavailableException;
import com.backendguru.cartservice.product.ProductClient;
import com.backendguru.cartservice.product.dto.ProductSnapshot;
import com.backendguru.common.dto.ApiResponse;
import com.backendguru.common.error.ResourceNotFoundException;
import com.backendguru.common.error.ValidationException;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

  @Mock ProductClient productClient;
  CartStore store;
  CartService service;

  @BeforeEach
  void setUp() {
    store = new InMemoryCartStore();
    service = new CartService(store, productClient);
  }

  private void stubProduct(Long id, int stock, boolean enabled) {
    when(productClient.getById(id))
        .thenReturn(
            ApiResponse.success(
                new ProductSnapshot(id, "Widget", new BigDecimal("10.00"), "TRY", stock, enabled)));
  }

  @Test
  void addItemFetchesProductAndStoresCart() {
    stubProduct(1L, 50, true);
    var cart = service.addItem(42L, 1L, 2);

    assertThat(cart.userId()).isEqualTo(42L);
    assertThat(cart.items()).hasSize(1);
    assertThat(cart.items().get(0).productId()).isEqualTo(1L);
    assertThat(cart.items().get(0).quantity()).isEqualTo(2);
    assertThat(cart.items().get(0).priceAmount()).isEqualByComparingTo(new BigDecimal("10.00"));
  }

  @Test
  void addItemMergesSameProduct() {
    stubProduct(1L, 50, true);
    service.addItem(42L, 1L, 2);
    var cart = service.addItem(42L, 1L, 3);

    assertThat(cart.items()).hasSize(1);
    assertThat(cart.items().get(0).quantity()).isEqualTo(5);
  }

  @Test
  void addItemRejectsNonPositiveQuantity() {
    assertThatThrownBy(() -> service.addItem(42L, 1L, 0))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("quantity must be positive");
  }

  @Test
  void addItemRejectsDisabledProduct() {
    stubProduct(1L, 50, false);
    assertThatThrownBy(() -> service.addItem(42L, 1L, 1))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("not available");
  }

  @Test
  void addItemRejectsInsufficientStock() {
    stubProduct(1L, 1, true);
    assertThatThrownBy(() -> service.addItem(42L, 1L, 5))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("Insufficient stock");
  }

  @Test
  void addItemPropagatesProductUnavailableFromFallback() {
    when(productClient.getById(99L)).thenThrow(new ProductUnavailableException("circuit open"));
    assertThatThrownBy(() -> service.addItem(42L, 99L, 1))
        .isInstanceOf(ProductUnavailableException.class);
  }

  @Test
  void differentUsersHaveIndependentCarts() {
    stubProduct(1L, 50, true);
    service.addItem(1L, 1L, 1);
    service.addItem(2L, 1L, 5);

    assertThat(service.getCart(1L).items().get(0).quantity()).isEqualTo(1);
    assertThat(service.getCart(2L).items().get(0).quantity()).isEqualTo(5);
  }

  @Test
  void updateQuantityZeroRemovesItem() {
    stubProduct(1L, 50, true);
    service.addItem(42L, 1L, 3);
    var cart = service.updateQuantity(42L, 1L, 0);
    assertThat(cart.items()).isEmpty();
  }

  @Test
  void updateQuantityChangesQuantity() {
    stubProduct(1L, 50, true);
    service.addItem(42L, 1L, 3);
    var cart = service.updateQuantity(42L, 1L, 7);
    assertThat(cart.items().get(0).quantity()).isEqualTo(7);
  }

  @Test
  void updateQuantityOnAbsentItemThrowsResourceNotFound() {
    assertThatThrownBy(() -> service.updateQuantity(42L, 999L, 1))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void removeItemThrowsWhenAbsent() {
    assertThatThrownBy(() -> service.removeItem(42L, 999L))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void clearEmptiesCart() {
    stubProduct(1L, 50, true);
    service.addItem(42L, 1L, 1);
    service.clear(42L);
    assertThat(service.getCart(42L).items()).isEmpty();
  }
}
