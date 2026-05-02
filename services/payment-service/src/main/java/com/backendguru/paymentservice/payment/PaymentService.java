package com.backendguru.paymentservice.payment;

import com.backendguru.common.error.BusinessException;
import com.backendguru.common.error.ErrorCode;
import com.backendguru.common.error.ResourceNotFoundException;
import com.backendguru.paymentservice.payment.dto.ChargeRequest;
import com.backendguru.paymentservice.payment.dto.PaymentResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {

  /** Iyzico-style deterministic-fail card. Any other number succeeds in this mock. */
  private static final String FAIL_CARD = "4111111111111115";

  private final PaymentRepository repository;

  @Transactional
  public PaymentResponse charge(ChargeRequest req) {
    String cardNumber = req.card().number().replaceAll("[^0-9]", "");
    String lastFour = cardNumber.substring(Math.max(0, cardNumber.length() - 4));

    Payment payment =
        Payment.builder()
            .orderId(req.orderId())
            .amount(req.amount())
            .currency(req.currency())
            .cardLastFour(lastFour)
            .build();

    if (FAIL_CARD.equals(cardNumber)) {
      payment.setStatus(PaymentStatus.FAILED);
      payment.setFailureReason("Card declined (mock)");
      Payment saved = repository.save(payment);
      throw new PaymentDeclinedException(
          "Payment declined for order " + req.orderId() + " (paymentId=" + saved.getId() + ")");
    }

    payment.setStatus(PaymentStatus.AUTHORIZED);
    payment.setIyzicoPaymentId("MOCK-" + UUID.randomUUID());
    return PaymentResponse.from(repository.save(payment));
  }

  @Transactional
  public PaymentResponse refund(Long paymentId) {
    Payment p =
        repository
            .findById(paymentId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment " + paymentId + " not found"));
    if (p.getStatus() == PaymentStatus.REFUNDED) {
      return PaymentResponse.from(p); // idempotent
    }
    if (p.getStatus() != PaymentStatus.AUTHORIZED) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Cannot refund payment in status " + p.getStatus()) {};
    }
    p.setStatus(PaymentStatus.REFUNDED);
    return PaymentResponse.from(p);
  }

  @Transactional(readOnly = true)
  public PaymentResponse get(Long paymentId) {
    return PaymentResponse.from(
        repository
            .findById(paymentId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Payment " + paymentId + " not found")));
  }

  /** Card was declined by mock. Maps to HTTP 402 via PAYMENT_FAILED ErrorCode. */
  public static class PaymentDeclinedException extends BusinessException {
    public PaymentDeclinedException(String message) {
      super(ErrorCode.PAYMENT_FAILED, message);
    }
  }
}
