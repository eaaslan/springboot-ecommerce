package com.backendguru.orderservice.coupon;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, Long> {

  boolean existsByCouponIdAndUserId(Long couponId, Long userId);
}
