package com.backendguru.orderservice.client.dto;

public record ReservationSnapshot(
    Long reservationId, Long productId, int quantity, String status) {}
