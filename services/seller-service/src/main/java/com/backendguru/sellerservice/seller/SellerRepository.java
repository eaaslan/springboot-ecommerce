package com.backendguru.sellerservice.seller;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerRepository extends JpaRepository<Seller, Long> {

  Optional<Seller> findByUserId(Long userId);

  List<Seller> findByStatus(SellerStatus status);
}
