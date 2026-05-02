package com.backendguru.orderservice.client;

import com.backendguru.common.dto.ApiResponse;
import com.backendguru.orderservice.client.dto.CartSnapshot;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "cart-service", fallbackFactory = CartClient.Fallback.class)
public interface CartClient {

  @GetMapping("/api/cart")
  ApiResponse<CartSnapshot> getCart(@RequestHeader("X-User-Id") String userId);

  @DeleteMapping("/api/cart")
  void clearCart(@RequestHeader("X-User-Id") String userId);

  @org.springframework.stereotype.Component
  class Fallback implements org.springframework.cloud.openfeign.FallbackFactory<CartClient> {
    @Override
    public CartClient create(Throwable cause) {
      return new CartClient() {
        @Override
        public ApiResponse<CartSnapshot> getCart(String userId) {
          throw new com.backendguru.orderservice.exception.SagaException(
              "Cart service unavailable: " + cause.getMessage(), cause);
        }

        @Override
        public void clearCart(String userId) {
          // best-effort: log and ignore so order doesn't fail after CONFIRMED
          org.slf4j.LoggerFactory.getLogger(CartClient.class)
              .warn("clearCart fallback triggered: {}", cause.toString());
        }
      };
    }
  }
}
