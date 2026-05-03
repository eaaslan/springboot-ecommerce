package com.backendguru.orderservice.marketplace.dto;

import com.backendguru.orderservice.marketplace.SellerPayout;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public final class PayoutDtos {

  private PayoutDtos() {}

  public record RunRequest(@NotNull LocalDate periodStart, @NotNull LocalDate periodEnd) {}

  public record PayoutResponse(
      Long id,
      Long sellerId,
      LocalDate periodStart,
      LocalDate periodEnd,
      BigDecimal grossAmount,
      BigDecimal commissionAmount,
      BigDecimal netAmount,
      int subOrderCount,
      String currency,
      String status,
      OffsetDateTime createdAt,
      OffsetDateTime paidAt) {

    public static PayoutResponse from(SellerPayout p) {
      return new PayoutResponse(
          p.getId(),
          p.getSellerId(),
          p.getPeriodStart(),
          p.getPeriodEnd(),
          p.getGrossAmount(),
          p.getCommissionAmount(),
          p.getNetAmount(),
          p.getSubOrderCount(),
          p.getCurrency(),
          p.getStatus(),
          p.getCreatedAt(),
          p.getPaidAt());
    }
  }
}
