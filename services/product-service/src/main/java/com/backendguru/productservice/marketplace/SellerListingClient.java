package com.backendguru.productservice.marketplace;

import com.backendguru.common.dto.ApiResponse;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "seller-service", fallbackFactory = SellerListingClient.Fallback.class)
public interface SellerListingClient {

  @GetMapping("/api/listings/best")
  ApiResponse<Map<Long, BestListingDto>> bestForProducts(
      @RequestParam("productIds") List<Long> productIds);

  /**
   * Best-effort fallback: empty map → catalog falls back to its own price/stock columns. The
   * platform stays usable even if seller-service is down.
   */
  @Component
  class Fallback implements FallbackFactory<SellerListingClient> {
    private static final Logger log = LoggerFactory.getLogger(SellerListingClient.class);

    @Override
    public SellerListingClient create(Throwable cause) {
      return new SellerListingClient() {
        @Override
        public ApiResponse<Map<Long, BestListingDto>> bestForProducts(List<Long> ids) {
          log.warn("seller-service unreachable, catalog falls back: {}", cause.toString());
          return ApiResponse.success(Map.of());
        }
      };
    }
  }
}
