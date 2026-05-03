package com.backendguru.orderservice.order;

import com.backendguru.common.dto.ApiResponse;
import com.backendguru.common.error.UnauthorizedException;
import com.backendguru.orderservice.idempotency.IdempotencyService;
import com.backendguru.orderservice.idempotency.ProcessedOrder;
import com.backendguru.orderservice.marketplace.ReturnsService;
import com.backendguru.orderservice.marketplace.SubOrder;
import com.backendguru.orderservice.marketplace.dto.SubOrderResponse;
import com.backendguru.orderservice.order.dto.OrderResponse;
import com.backendguru.orderservice.order.dto.PlaceOrderRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

  private final OrderService service;
  private final IdempotencyService idempotencyService;
  private final ReturnsService returnsService;

  @PostMapping
  public ResponseEntity<ApiResponse<OrderResponse>> place(
      Authentication auth,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody PlaceOrderRequest req) {
    Long userId = currentUserId(auth);

    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      Optional<ProcessedOrder> cached = idempotencyService.lookup(idempotencyKey, userId);
      if (cached.isPresent()) {
        try {
          OrderResponse cachedResp = idempotencyService.deserialize(cached.get());
          log.info(
              "Idempotency replay: key={}, user={}, orderId={}",
              idempotencyKey,
              userId,
              cachedResp.id());
          return ResponseEntity.status(cached.get().getResponseStatus())
              .body(ApiResponse.success(cachedResp));
        } catch (JsonProcessingException ex) {
          log.error("Failed to deserialize cached idempotent response — falling through", ex);
        }
      }
    }

    OrderResponse resp = service.placeOrder(userId, req);
    idempotencyService.capture(idempotencyKey, userId, resp, HttpStatus.CREATED.value());
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

  /** Customer flags a sub-order for return. State machine guards re-requests. */
  @PostMapping("/sub-orders/{id:\\d+}/return-request")
  public ApiResponse<SubOrderResponse> requestReturn(Authentication auth, @PathVariable Long id) {
    SubOrder sub = returnsService.requestReturn(id, currentUserId(auth));
    return ApiResponse.success(SubOrderResponse.from(sub));
  }

  private Long currentUserId(Authentication auth) {
    if (auth == null || !(auth.getPrincipal() instanceof Long userId)) {
      throw new UnauthorizedException("Authentication required");
    }
    return userId;
  }
}
