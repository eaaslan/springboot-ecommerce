package com.backendguru.orderservice.coupon;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

  Optional<Coupon> findByCode(String code);

  Optional<Coupon> findByCodeAndActiveTrue(String code);
}
