package com.backendguru.paymentservice.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CardDetails(
    @NotBlank String holderName,
    @NotBlank @Pattern(regexp = "[0-9-]{13,25}") String number,
    @NotBlank @Size(min = 1, max = 2) String expireMonth,
    @NotBlank @Size(min = 4, max = 4) String expireYear,
    @NotBlank @Size(min = 3, max = 4) String cvc) {}
