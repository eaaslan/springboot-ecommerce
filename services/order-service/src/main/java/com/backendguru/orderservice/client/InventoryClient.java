package com.backendguru.orderservice.client;

import com.backendguru.common.dto.ApiResponse;
import com.backendguru.orderservice.client.dto.ReservationSnapshot;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "inventory-service", fallbackFactory = InventoryClient.Fallback.class)
public interface InventoryClient {

  @PostMapping("/api/inventory/reservations")
  ApiResponse<ReservationSnapshot> reserve(@RequestBody Map<String, Object> request);

  @PostMapping("/api/inventory/reservations/{id}/commit")
  void commit(@PathVariable("id") Long reservationId);

  @PostMapping("/api/inventory/reservations/{id}/release")
  void release(@PathVariable("id") Long reservationId);

  @org.springframework.stereotype.Component
  class Fallback implements org.springframework.cloud.openfeign.FallbackFactory<InventoryClient> {
    @Override
    public InventoryClient create(Throwable cause) {
      return new InventoryClient() {
        @Override
        public ApiResponse<ReservationSnapshot> reserve(Map<String, Object> request) {
          throw new com.backendguru.orderservice.exception.SagaException(
              "Inventory service unavailable: " + cause.getMessage(), cause);
        }

        @Override
        public void commit(Long reservationId) {
          throw new com.backendguru.orderservice.exception.SagaException(
              "Inventory commit failed: " + cause.getMessage(), cause);
        }

        @Override
        public void release(Long reservationId) {
          // log and continue — best-effort compensation
          org.slf4j.LoggerFactory.getLogger(InventoryClient.class)
              .warn(
                  "release fallback triggered for reservation {}: {}",
                  reservationId,
                  cause.toString());
        }
      };
    }
  }
}
