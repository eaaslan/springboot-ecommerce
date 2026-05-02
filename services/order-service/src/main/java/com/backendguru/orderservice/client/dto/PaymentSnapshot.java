package com.backendguru.orderservice.client.dto;

import java.math.BigDecimal;

public record PaymentSnapshot(
    Long paymentId,
    Long orderId,
    String status,
    BigDecimal amount,
    String currency,
    String iyzicoPaymentId,
    String failureReason,
    String cardLastFour) {}
