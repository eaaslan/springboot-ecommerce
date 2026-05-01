package com.backendguru.cartservice.cart.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateItemRequest(@NotNull @PositiveOrZero Integer quantity) {}
