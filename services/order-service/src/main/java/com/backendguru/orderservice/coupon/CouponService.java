package com.backendguru.orderservice.coupon;

import com.backendguru.common.error.DuplicateResourceException;
import com.backendguru.common.error.ResourceNotFoundException;
import com.backendguru.common.error.ValidationException;
import com.backendguru.orderservice.coupon.dto.CouponDtos.CouponCreateRequest;
import com.backendguru.orderservice.coupon.dto.CouponDtos.CouponResponse;
import com.backendguru.orderservice.coupon.dto.CouponDtos.CouponUpdateRequest;
import com.backendguru.orderservice.coupon.dto.CouponDtos.ValidateCouponResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CouponService {

  private final CouponRepository couponRepository;
  private final CouponRedemptionRepository redemptionRepository;

  // -------- read paths --------

  @Transactional(readOnly = true)
  public List<CouponResponse> list() {
    return couponRepository.findAll().stream().map(CouponResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public CouponResponse byId(Long id) {
    return CouponResponse.from(loadOrThrow(id));
  }

  // -------- admin write paths --------

  @Transactional
  public CouponResponse create(CouponCreateRequest req) {
    String code = normalize(req.code());
    couponRepository
        .findByCode(code)
        .ifPresent(
            c -> {
              throw new DuplicateResourceException("Coupon code already exists: " + code);
            });
    Coupon c =
        Coupon.builder()
            .code(code)
            .discountType(req.discountType())
            .discountValue(req.discountValue())
            .minOrderAmount(req.minOrderAmount())
            .maxUses(req.maxUses())
            .validFrom(req.validFrom())
            .validUntil(req.validUntil())
            .active(true)
            .build();
    return CouponResponse.from(couponRepository.save(c));
  }

  @Transactional
  public CouponResponse update(Long id, CouponUpdateRequest req) {
    Coupon c = loadOrThrow(id);
    if (req.discountType() != null) c.setDiscountType(req.discountType());
    if (req.discountValue() != null) c.setDiscountValue(req.discountValue());
    if (req.minOrderAmount() != null) c.setMinOrderAmount(req.minOrderAmount());
    if (req.maxUses() != null) c.setMaxUses(req.maxUses());
    if (req.validFrom() != null) c.setValidFrom(req.validFrom());
    if (req.validUntil() != null) c.setValidUntil(req.validUntil());
    if (req.active() != null) c.setActive(req.active());
    return CouponResponse.from(c);
  }

  @Transactional
  public void disable(Long id) {
    Coupon c = loadOrThrow(id);
    c.setActive(false);
  }

  // -------- validate (preview) --------

  /**
   * Pure preview — does NOT mutate counters. Used by the checkout UI to show the discount before
   * placing the order. The same checks run again inside {@link #validateAndCalculate} during the
   * saga.
   */
  @Transactional(readOnly = true)
  public ValidateCouponResponse preview(String code, BigDecimal orderAmount, Long userId) {
    Coupon coupon = loadActiveOrThrow(code);
    assertEligible(coupon, orderAmount, userId);
    BigDecimal discount = computeDiscount(coupon, orderAmount);
    return new ValidateCouponResponse(
        coupon.getCode(),
        coupon.getDiscountType(),
        coupon.getDiscountValue(),
        discount,
        orderAmount,
        orderAmount.subtract(discount),
        "OK");
  }

  // -------- saga apply (called from OrderService inside @Transactional) --------

  /** Returns the discount amount; throws ValidationException if not eligible. */
  public BigDecimal validateAndCalculate(String code, BigDecimal orderAmount, Long userId) {
    Coupon coupon = loadActiveOrThrow(code);
    assertEligible(coupon, orderAmount, userId);
    return computeDiscount(coupon, orderAmount);
  }

  /**
   * Records a redemption + bumps usedCount. Own transaction so dirty-tracking flushes the usedCount
   * increment even when called from the saga's non-@Transactional placeOrder method.
   */
  @Transactional
  public void recordRedemption(String code, Long userId, Long orderId, BigDecimal discountAmount) {
    Coupon coupon =
        couponRepository
            .findByCodeAndActiveTrue(normalize(code))
            .orElseThrow(() -> new ResourceNotFoundException("Coupon not found: " + code));
    coupon.setUsedCount(coupon.getUsedCount() + 1);
    couponRepository.save(coupon);
    redemptionRepository.save(
        CouponRedemption.builder()
            .couponId(coupon.getId())
            .userId(userId)
            .orderId(orderId)
            .discountAmount(discountAmount)
            .build());
  }

  // -------- internals --------

  private Coupon loadOrThrow(Long id) {
    return couponRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Coupon " + id + " not found"));
  }

  private Coupon loadActiveOrThrow(String code) {
    return couponRepository
        .findByCodeAndActiveTrue(normalize(code))
        .orElseThrow(() -> new ValidationException("Coupon not found or inactive: " + code));
  }

  private void assertEligible(Coupon c, BigDecimal orderAmount, Long userId) {
    OffsetDateTime now = OffsetDateTime.now();
    if (c.getValidFrom() != null && now.isBefore(c.getValidFrom())) {
      throw new ValidationException("Coupon not yet valid");
    }
    if (c.getValidUntil() != null && now.isAfter(c.getValidUntil())) {
      throw new ValidationException("Coupon has expired");
    }
    if (c.getMinOrderAmount() != null && orderAmount.compareTo(c.getMinOrderAmount()) < 0) {
      throw new ValidationException(
          "Minimum order amount " + c.getMinOrderAmount() + " required for this coupon");
    }
    if (c.getMaxUses() != null && c.getUsedCount() >= c.getMaxUses()) {
      throw new ValidationException("Coupon usage limit reached");
    }
    if (redemptionRepository.existsByCouponIdAndUserId(c.getId(), userId)) {
      throw new ValidationException(
          "This coupon ("
              + c.getCode()
              + ") was already redeemed by your account — one use per user");
    }
  }

  private BigDecimal computeDiscount(Coupon c, BigDecimal orderAmount) {
    BigDecimal discount =
        switch (c.getDiscountType()) {
          case PERCENT ->
              orderAmount
                  .multiply(c.getDiscountValue())
                  .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
          case FIXED -> c.getDiscountValue();
        };
    // Never discount below zero
    if (discount.compareTo(orderAmount) > 0) discount = orderAmount;
    return discount.setScale(2, RoundingMode.HALF_UP);
  }

  private static String normalize(String code) {
    return code == null ? null : code.trim().toUpperCase();
  }
}
