package com.backendguru.productservice.inventory;

import com.backendguru.common.dto.ApiResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "inventory-service", fallbackFactory = InventoryStockClient.Fallback.class)
public interface InventoryStockClient {

  @GetMapping("/api/inventory/batch")
  ApiResponse<List<InventoryStatusDto>> statusBatch(@RequestParam("ids") List<Long> ids);

  /** Best-effort fallback: return empty list so catalog falls back to product-service stock. */
  @Component
  class Fallback implements FallbackFactory<InventoryStockClient> {
    private static final Logger log = LoggerFactory.getLogger(InventoryStockClient.class);

    @Override
    public InventoryStockClient create(Throwable cause) {
      return new InventoryStockClient() {
        @Override
        public ApiResponse<List<InventoryStatusDto>> statusBatch(List<Long> ids) {
          log.warn("Inventory batch lookup unavailable, falling back: {}", cause.toString());
          return ApiResponse.success(List.of());
        }
      };
    }
  }
}
