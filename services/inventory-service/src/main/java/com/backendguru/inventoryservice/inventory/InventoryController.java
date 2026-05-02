package com.backendguru.inventoryservice.inventory;

import com.backendguru.common.dto.ApiResponse;
import com.backendguru.inventoryservice.inventory.dto.InventoryStatusResponse;
import com.backendguru.inventoryservice.inventory.dto.ReservationResponse;
import com.backendguru.inventoryservice.inventory.dto.ReserveRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

  private final InventoryService service;

  @PostMapping("/reservations")
  public ResponseEntity<ApiResponse<ReservationResponse>> reserve(
      @Valid @RequestBody ReserveRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.reserve(req)));
  }

  @PostMapping("/reservations/{id}/commit")
  public ResponseEntity<Void> commit(@PathVariable("id") Long id) {
    service.commit(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/reservations/{id}/release")
  public ResponseEntity<Void> release(@PathVariable("id") Long id) {
    service.release(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{productId}")
  public ApiResponse<InventoryStatusResponse> status(@PathVariable Long productId) {
    return ApiResponse.success(service.statusForProduct(productId));
  }
}
