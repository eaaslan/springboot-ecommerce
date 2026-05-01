package com.backendguru.cartservice.cart.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddItemRequest(@NotNull Long productId, @NotNull @Positive Integer quantity) {}
