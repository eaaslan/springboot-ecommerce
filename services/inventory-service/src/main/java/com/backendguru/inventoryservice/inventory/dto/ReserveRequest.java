package com.backendguru.inventoryservice.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ReserveRequest(
    @NotNull Long productId, @NotNull @Positive Integer quantity, @NotNull Long orderId) {}
