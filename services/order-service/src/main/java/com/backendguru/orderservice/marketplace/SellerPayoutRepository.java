package com.backendguru.orderservice.marketplace;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerPayoutRepository extends JpaRepository<SellerPayout, Long> {

  List<SellerPayout> findAllByOrderByIdDesc();

  List<SellerPayout> findBySellerIdOrderByIdDesc(Long sellerId);
}
