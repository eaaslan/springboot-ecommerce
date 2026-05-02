package com.backendguru.paymentservice.payment.dto;

import com.backendguru.paymentservice.payment.Payment;
import com.backendguru.paymentservice.payment.PaymentStatus;
import java.math.BigDecimal;

public record PaymentResponse(
    Long paymentId,
    Long orderId,
    PaymentStatus status,
    BigDecimal amount,
    String currency,
    String iyzicoPaymentId,
    String failureReason,
    String cardLastFour) {

  public static PaymentResponse from(Payment p) {
    return new PaymentResponse(
        p.getId(),
        p.getOrderId(),
        p.getStatus(),
        p.getAmount(),
        p.getCurrency(),
        p.getIyzicoPaymentId(),
        p.getFailureReason(),
        p.getCardLastFour());
  }
}
