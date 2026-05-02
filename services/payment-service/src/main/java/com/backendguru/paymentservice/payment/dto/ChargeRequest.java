package com.backendguru.paymentservice.payment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ChargeRequest(
    @NotNull Long orderId,
    @NotNull @Positive BigDecimal amount,
    @NotBlank @Size(min = 3, max = 3) String currency,
    @NotNull @Valid CardDetails card) {}
