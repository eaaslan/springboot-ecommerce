package com.backendguru.orderservice.coupon.dto;

import com.backendguru.orderservice.coupon.Coupon;
import com.backendguru.orderservice.coupon.DiscountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public final class CouponDtos {

  private CouponDtos() {}

  public record CouponCreateRequest(
      @NotBlank String code,
      @NotNull DiscountType discountType,
      @NotNull @Positive BigDecimal discountValue,
      BigDecimal minOrderAmount,
      Integer maxUses,
      OffsetDateTime validFrom,
      OffsetDateTime validUntil) {}

  public record CouponUpdateRequest(
      DiscountType discountType,
      BigDecimal discountValue,
      BigDecimal minOrderAmount,
      Integer maxUses,
      OffsetDateTime validFrom,
      OffsetDateTime validUntil,
      Boolean active) {}

  public record CouponResponse(
      Long id,
      String code,
      DiscountType discountType,
      BigDecimal discountValue,
      BigDecimal minOrderAmount,
      Integer maxUses,
      int usedCount,
      OffsetDateTime validFrom,
      OffsetDateTime validUntil,
      boolean active) {

    public static CouponResponse from(Coupon c) {
      return new CouponResponse(
          c.getId(),
          c.getCode(),
          c.getDiscountType(),
          c.getDiscountValue(),
          c.getMinOrderAmount(),
          c.getMaxUses(),
          c.getUsedCount(),
          c.getValidFrom(),
          c.getValidUntil(),
          c.isActive());
    }
  }

  public record ValidateCouponRequest(@NotBlank String code, @NotNull BigDecimal orderAmount) {}

  public record ValidateCouponResponse(
      String code,
      DiscountType discountType,
      BigDecimal discountValue,
      BigDecimal discountAmount,
      BigDecimal originalAmount,
      BigDecimal finalAmount,
      String message) {}
}
