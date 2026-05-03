package com.backendguru.orderservice.order.dto;

import com.backendguru.orderservice.marketplace.SubOrder;
import com.backendguru.orderservice.marketplace.dto.SubOrderResponse;
import com.backendguru.orderservice.order.Order;
import com.backendguru.orderservice.order.OrderStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record OrderResponse(
    Long id,
    Long userId,
    OrderStatus status,
    BigDecimal totalAmount,
    BigDecimal subtotalAmount,
    String couponCode,
    BigDecimal discountAmount,
    String currency,
    Long paymentId,
    String failureReason,
    List<OrderItemResponse> items,
    List<SubOrderResponse> subOrders,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  public static OrderResponse from(Order o) {
    return from(o, List.of());
  }

  public static OrderResponse from(Order o, List<SubOrder> subOrders) {
    return new OrderResponse(
        o.getId(),
        o.getUserId(),
        o.getStatus(),
        o.getTotalAmount(),
        o.getSubtotalAmount(),
        o.getCouponCode(),
        o.getDiscountAmount(),
        o.getCurrency(),
        o.getPaymentId(),
        o.getFailureReason(),
        o.getItems().stream().map(OrderItemResponse::from).toList(),
        subOrders.stream().map(SubOrderResponse::from).toList(),
        o.getCreatedAt(),
        o.getUpdatedAt());
  }
}
