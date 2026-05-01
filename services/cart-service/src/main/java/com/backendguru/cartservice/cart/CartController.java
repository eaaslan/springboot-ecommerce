package com.backendguru.cartservice.cart;

import com.backendguru.cartservice.cart.dto.AddItemRequest;
import com.backendguru.cartservice.cart.dto.CartResponse;
import com.backendguru.cartservice.cart.dto.UpdateItemRequest;
import com.backendguru.common.dto.ApiResponse;
import com.backendguru.common.error.UnauthorizedException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

  private final CartService service;

  @GetMapping
  public ApiResponse<CartResponse> getCart(Authentication auth) {
    return ApiResponse.success(CartResponse.from(service.getCart(currentUserId(auth))));
  }

  @PostMapping("/items")
  public ApiResponse<CartResponse> addItem(
      Authentication auth, @Valid @RequestBody AddItemRequest req) {
    return ApiResponse.success(
        CartResponse.from(service.addItem(currentUserId(auth), req.productId(), req.quantity())));
  }

  @PatchMapping("/items/{productId}")
  public ApiResponse<CartResponse> updateItem(
      Authentication auth,
      @PathVariable Long productId,
      @Valid @RequestBody UpdateItemRequest req) {
    return ApiResponse.success(
        CartResponse.from(service.updateQuantity(currentUserId(auth), productId, req.quantity())));
  }

  @DeleteMapping("/items/{productId}")
  public ApiResponse<CartResponse> removeItem(Authentication auth, @PathVariable Long productId) {
    return ApiResponse.success(
        CartResponse.from(service.removeItem(currentUserId(auth), productId)));
  }

  @DeleteMapping
  public ResponseEntity<Void> clear(Authentication auth) {
    service.clear(currentUserId(auth));
    return ResponseEntity.noContent().build();
  }

  private Long currentUserId(Authentication auth) {
    if (auth == null || !(auth.getPrincipal() instanceof Long userId)) {
      throw new UnauthorizedException("Authentication required");
    }
    return userId;
  }
}
