package com.backendguru.cartservice.marketplace;

import com.backendguru.common.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "seller-service", fallbackFactory = SellerListingClient.Fallback.class)
public interface SellerListingClient {

  @GetMapping("/api/listings/{id}")
  ApiResponse<ListingSnapshot> getById(@PathVariable("id") Long id);

  /**
   * If seller-service is unavailable when the customer adds a listing-keyed item, return null so
   * the cart service treats it as a master-product add (graceful degradation rather than failing
   * the whole flow).
   */
  @Component
  class Fallback implements FallbackFactory<SellerListingClient> {
    private static final Logger log = LoggerFactory.getLogger(SellerListingClient.class);

    @Override
    public SellerListingClient create(Throwable cause) {
      return new SellerListingClient() {
        @Override
        public ApiResponse<ListingSnapshot> getById(Long id) {
          log.warn("seller-service unreachable, listing {} not enriched: {}", id, cause.toString());
          return ApiResponse.success(null);
        }
      };
    }
  }
}
