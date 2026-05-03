package com.backendguru.orderservice.marketplace.dto;

import com.backendguru.orderservice.marketplace.SubOrder;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record SubOrderResponse(
    Long id,
    Long orderId,
    Long sellerId,
    String sellerName,
    BigDecimal subtotalAmount,
    BigDecimal commissionPct,
    BigDecimal commissionAmount,
    BigDecimal payoutAmount,
    String currency,
    String status,
    OffsetDateTime createdAt) {

  public static SubOrderResponse from(SubOrder s) {
    return new SubOrderResponse(
        s.getId(),
        s.getOrderId(),
        s.getSellerId(),
        s.getSellerName(),
        s.getSubtotalAmount(),
        s.getCommissionPct(),
        s.getCommissionAmount(),
        s.getPayoutAmount(),
        s.getCurrency(),
        s.getStatus(),
        s.getCreatedAt());
  }
}
