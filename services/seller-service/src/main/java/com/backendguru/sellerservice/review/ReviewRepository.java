package com.backendguru.sellerservice.review;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {

  Optional<Review> findByUserIdAndSellerIdAndProductId(Long userId, Long sellerId, Long productId);

  List<Review> findBySellerIdOrderByIdDesc(Long sellerId);

  List<Review> findByProductIdOrderByIdDesc(Long productId);
}
