package com.backendguru.cartservice.cart.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * {@code listingId} is optional — when supplied, the cart locks the line to a specific seller offer
 * (price + seller info come from the listing). When null, behavior matches single-vendor mode and
 * the master-product price is used.
 */
public record AddItemRequest(
    @NotNull Long productId, @NotNull @Positive Integer quantity, Long listingId) {}
