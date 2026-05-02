package com.backendguru.orderservice.order;

import com.backendguru.common.dto.ApiResponse;
import com.backendguru.common.error.UnauthorizedException;
import com.backendguru.orderservice.order.dto.OrderResponse;
import com.backendguru.orderservice.order.dto.PlaceOrderRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

  private final OrderService service;

  @PostMapping
  public ResponseEntity<ApiResponse<OrderResponse>> place(
      Authentication auth, @Valid @RequestBody PlaceOrderRequest req) {
    Long userId = currentUserId(auth);
    OrderResponse resp = service.placeOrder(userId, req);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(resp));
  }

  @GetMapping("/{id}")
  public ApiResponse<OrderResponse> get(Authentication auth, @PathVariable Long id) {
    return ApiResponse.success(service.getOrder(id, currentUserId(auth)));
  }

  @GetMapping
  public ApiResponse<List<OrderResponse>> list(Authentication auth) {
    return ApiResponse.success(service.listOrdersForUser(currentUserId(auth)));
  }

  private Long currentUserId(Authentication auth) {
    if (auth == null || !(auth.getPrincipal() instanceof Long userId)) {
      throw new UnauthorizedException("Authentication required");
    }
    return userId;
  }
}
