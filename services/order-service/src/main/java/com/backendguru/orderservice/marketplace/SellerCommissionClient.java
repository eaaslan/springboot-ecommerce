package com.backendguru.orderservice.marketplace;

import com.backendguru.common.dto.ApiResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Looks up the per-seller commission rate when splitting an order. Public endpoint — no auth
 * needed. On failure falls back to {@link SubOrderSplitter#DEFAULT_COMMISSION_PCT} so the saga
 * never blocks on seller-service.
 */
@FeignClient(
    name = "seller-service",
    contextId = "sellerCommission",
    fallbackFactory = SellerCommissionClient.Fallback.class)
public interface SellerCommissionClient {

  @GetMapping("/api/sellers/{id}/public")
  ApiResponse<SellerPublic> getById(@PathVariable("id") Long id);

  @JsonIgnoreProperties(ignoreUnknown = true)
  record SellerPublic(Long id, String businessName, BigDecimal commissionPct) {}

  @Component
  class Fallback implements FallbackFactory<SellerCommissionClient> {
    private static final Logger log = LoggerFactory.getLogger(SellerCommissionClient.class);

    @Override
    public SellerCommissionClient create(Throwable cause) {
      return new SellerCommissionClient() {
        @Override
        public ApiResponse<SellerPublic> getById(Long id) {
          log.warn(
              "seller-service unreachable, falling back to default commission for seller {}: {}",
              id,
              cause.toString());
          return ApiResponse.success(null);
        }
      };
    }
  }
}
