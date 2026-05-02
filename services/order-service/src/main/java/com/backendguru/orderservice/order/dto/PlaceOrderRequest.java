package com.backendguru.orderservice.order.dto;

import com.backendguru.orderservice.client.dto.ChargeRequest.CardDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record PlaceOrderRequest(
    @NotNull @Valid CardDetails card,
    /** Optional coupon code applied at checkout. */
    String couponCode) {}
