package com.backendguru.inventoryservice.inventory.dto;

import com.backendguru.inventoryservice.inventory.Reservation;
import com.backendguru.inventoryservice.inventory.ReservationStatus;

public record ReservationResponse(
    Long reservationId, Long productId, int quantity, ReservationStatus status) {

  public static ReservationResponse from(Reservation r, Long productId) {
    return new ReservationResponse(r.getId(), productId, r.getQuantity(), r.getStatus());
  }
}
