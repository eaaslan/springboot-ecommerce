package com.backendguru.cartservice.cart;

import com.backendguru.cartservice.marketplace.ListingSnapshot;
import com.backendguru.cartservice.marketplace.SellerListingClient;
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
  private final SellerListingClient listingClient;

  public Cart getCart(Long userId) {
    return store.get(userId);
  }

  /** Legacy/non-marketplace overload: master-product add (no listing locked in). */
  public Cart addItem(Long userId, Long productId, int quantity) {
    return addItem(userId, productId, quantity, null);
  }

  public Cart addItem(Long userId, Long productId, int quantity, Long listingId) {
    if (quantity <= 0) {
      throw new ValidationException("quantity must be positive");
    }
    ProductSnapshot snap = fetchProduct(productId);
    if (!snap.enabled()) {
      throw new ValidationException("Product " + productId + " is not available");
    }

    CartItem item;
    if (listingId != null) {
      ListingSnapshot l = fetchListing(listingId);
      if (l == null) {
        throw new ResourceNotFoundException("Listing " + listingId + " not found");
      }
      if (!l.enabled()) {
        throw new ValidationException("Listing " + listingId + " is no longer available");
      }
      if (!l.productId().equals(productId)) {
        throw new ValidationException(
            "Listing " + listingId + " does not belong to product " + productId);
      }
      if (l.stockQuantity() < quantity) {
        throw new ValidationException(
            "Insufficient stock from seller (available " + l.stockQuantity() + ")");
      }
      item =
          new CartItem(
              snap.id(),
              snap.name(),
              l.priceAmount(),
              l.priceCurrency(),
              quantity,
              l.id(),
              l.sellerId(),
              l.sellerName());
    } else {
      if (snap.stockQuantity() < quantity) {
        throw new ValidationException(
            "Insufficient stock for product "
                + productId
                + " (available "
                + snap.stockQuantity()
                + ")");
      }
      item =
          new CartItem(
              snap.id(),
              snap.name(),
              snap.priceAmount(),
              snap.priceCurrency(),
              quantity,
              null,
              null,
              null);
    }
    return store.save(store.get(userId).upsertItem(item));
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

  private ListingSnapshot fetchListing(Long listingId) {
    var resp = listingClient.getById(listingId);
    return resp == null ? null : resp.data();
  }
}
