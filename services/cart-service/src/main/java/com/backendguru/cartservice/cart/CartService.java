package com.backendguru.cartservice.cart;

import com.backendguru.cartservice.product.ProductClient;
import com.backendguru.cartservice.product.dto.ProductSnapshot;
import com.backendguru.common.error.ResourceNotFoundException;
import com.backendguru.common.error.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CartService {

  private final CartStore store;
  private final ProductClient productClient;

  public Cart getCart(Long userId) {
    return store.get(userId);
  }

  public Cart addItem(Long userId, Long productId, int quantity) {
    if (quantity <= 0) {
      throw new ValidationException("quantity must be positive");
    }
    ProductSnapshot snap = fetchProduct(productId);
    if (!snap.enabled()) {
      throw new ValidationException("Product " + productId + " is not available");
    }
    if (snap.stockQuantity() < quantity) {
      throw new ValidationException(
          "Insufficient stock for product "
              + productId
              + " (available "
              + snap.stockQuantity()
              + ")");
    }
    Cart updated =
        store
            .get(userId)
            .upsertItem(
                new CartItem(
                    snap.id(),
                    snap.name(),
                    snap.priceAmount(),
                    snap.priceCurrency(),
                    quantity));
    return store.save(updated);
  }

  public Cart updateQuantity(Long userId, Long productId, int quantity) {
    Cart current = store.get(userId);
    if (current.items().stream().noneMatch(it -> it.productId().equals(productId))) {
      throw new ResourceNotFoundException("Item " + productId + " not in cart");
    }
    return store.save(current.updateQuantity(productId, quantity));
  }

  public Cart removeItem(Long userId, Long productId) {
    Cart current = store.get(userId);
    if (current.items().stream().noneMatch(it -> it.productId().equals(productId))) {
      throw new ResourceNotFoundException("Item " + productId + " not in cart");
    }
    return store.save(current.removeItem(productId));
  }

  public void clear(Long userId) {
    store.clear(userId);
  }

  private ProductSnapshot fetchProduct(Long productId) {
    var resp = productClient.getById(productId);
    if (resp == null || resp.data() == null) {
      throw new ResourceNotFoundException("Product " + productId + " not found");
    }
    return resp.data();
  }
}
