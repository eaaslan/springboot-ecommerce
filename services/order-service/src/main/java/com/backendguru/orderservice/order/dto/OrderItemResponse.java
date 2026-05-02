package com.backendguru.orderservice.order.dto;

import com.backendguru.orderservice.order.OrderItem;
import java.math.BigDecimal;

public record OrderItemResponse(
    Long productId,
    String productName,
    BigDecimal priceAmount,
    String priceCurrency,
    int quantity,
    Long reservationId) {

  public static OrderItemResponse from(OrderItem i) {
    return new OrderItemResponse(
        i.getProductId(),
        i.getProductName(),
        i.getPriceAmount(),
        i.getPriceCurrency(),
        i.getQuantity(),
        i.getReservationId());
  }
}
