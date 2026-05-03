package com.backendguru.paymentservice.payment;

import com.backendguru.common.error.BusinessException;
import com.backendguru.common.error.ErrorCode;
import com.backendguru.common.error.ResourceNotFoundException;
import com.backendguru.paymentservice.payment.dto.ChargeRequest;
import com.backendguru.paymentservice.payment.dto.PaymentResponse;
import com.backendguru.paymentservice.payment.iyzico.IyzicoClient;
import com.backendguru.paymentservice.payment.iyzico.IyzicoClient.IyzicoDeclinedException;
import com.backendguru.paymentservice.payment.iyzico.IyzicoProperties;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

  /** Test card that always declines in the mock branch. Iyzico has its own decline cards. */
  private static final String FAIL_CARD = "4111111111111115";

  private final PaymentRepository repository;
  private final IyzicoProperties iyzicoProps;
  private final IyzicoClient iyzicoClient;

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

    if (iyzicoProps.isConfigured()) {
      return chargeViaIyzico(payment, req);
    }
    log.debug("Iyzico not configured — using built-in mock for order {}", req.orderId());
    return chargeViaMock(payment, cardNumber, req);
  }

  private PaymentResponse chargeViaIyzico(Payment payment, ChargeRequest req) {
    try {
      var response = iyzicoClient.charge(req);
      payment.setStatus(PaymentStatus.AUTHORIZED);
      payment.setIyzicoPaymentId(response.getPaymentId());
      log.info(
          "Order {} charged via Iyzico — iyzicoPaymentId={} authCode={}",
          req.orderId(),
          response.getPaymentId(),
          response.getAuthCode());
      return PaymentResponse.from(repository.save(payment));
    } catch (IyzicoDeclinedException e) {
      payment.setStatus(PaymentStatus.FAILED);
      payment.setFailureReason("Iyzico: " + e.getMessage());
      Payment saved = repository.save(payment);
      throw new PaymentDeclinedException(
          "Payment declined for order "
              + req.orderId()
              + ": "
              + e.getMessage()
              + " (paymentId="
              + saved.getId()
              + ")");
    } catch (RuntimeException e) {
      // Network / config / unexpected — record + escalate
      payment.setStatus(PaymentStatus.FAILED);
      payment.setFailureReason("Iyzico unreachable: " + e.getClass().getSimpleName());
      repository.save(payment);
      log.error("Iyzico call failed for order {}", req.orderId(), e);
      throw new PaymentDeclinedException(
          "Payment provider unreachable for order " + req.orderId() + ": " + e.getMessage());
    }
  }

  private PaymentResponse chargeViaMock(Payment payment, String cardNumber, ChargeRequest req) {
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
            .orElseThrow(
                () -> new ResourceNotFoundException("Payment " + paymentId + " not found"));
    if (p.getStatus() == PaymentStatus.REFUNDED) {
      return PaymentResponse.from(p); // idempotent
    }
    if (p.getStatus() != PaymentStatus.AUTHORIZED) {
      throw new BusinessException(
          ErrorCode.VALIDATION_FAILED, "Cannot refund payment in status " + p.getStatus()) {};
    }

    if (iyzicoProps.isConfigured()) {
      try {
        iyzicoClient.refund(p.getIyzicoPaymentId(), p.getAmount(), p.getCurrency());
      } catch (IyzicoDeclinedException e) {
        // Iyzico itself rejected the refund — record but don't flip the local status to REFUNDED.
        log.error("Iyzico refund REJECTED for payment {}: {}", paymentId, e.getMessage());
        throw new BusinessException(
            ErrorCode.VALIDATION_FAILED, "Refund rejected by provider: " + e.getMessage()) {};
      }
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

  /** Card was declined (by Iyzico or by the mock). Maps to HTTP 402 via PAYMENT_FAILED. */
  public static class PaymentDeclinedException extends BusinessException {
    public PaymentDeclinedException(String message) {
      super(ErrorCode.PAYMENT_FAILED, message);
    }
  }
}
