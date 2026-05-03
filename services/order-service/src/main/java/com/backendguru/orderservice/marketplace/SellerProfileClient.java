package com.backendguru.orderservice.marketplace;

import com.backendguru.common.dto.ApiResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;

/** Resolves the calling user → their seller record. Carries X-User-Id/Role automatically. */
@FeignClient(name = "seller-service", fallbackFactory = SellerProfileClient.Fallback.class)
public interface SellerProfileClient {

  @GetMapping("/api/sellers/me")
  ApiResponse<SellerProfile> me();

  @JsonIgnoreProperties(ignoreUnknown = true)
  record SellerProfile(Long id, Long userId, String businessName, String status) {}

  @Component
  class Fallback implements FallbackFactory<SellerProfileClient> {
    private static final Logger log = LoggerFactory.getLogger(SellerProfileClient.class);

    @Override
    public SellerProfileClient create(Throwable cause) {
      return new SellerProfileClient() {
        @Override
        public ApiResponse<SellerProfile> me() {
          log.warn("seller-service unreachable for /me: {}", cause.toString());
          return ApiResponse.success(null);
        }
      };
    }
  }
}
