package com.backendguru.sellerservice.listing;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListingRepository extends JpaRepository<Listing, Long> {

  List<Listing> findByProductIdAndEnabledTrue(Long productId);

  List<Listing> findBySellerId(Long sellerId);

  List<Listing> findByProductIdInAndEnabledTrue(Collection<Long> productIds);
}
