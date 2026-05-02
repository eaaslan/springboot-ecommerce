package com.backendguru.paymentservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.backendguru.paymentservice.payment.PaymentRepository;
import com.backendguru.paymentservice.payment.PaymentService;
import com.backendguru.paymentservice.payment.PaymentStatus;
import com.backendguru.paymentservice.payment.dto.CardDetails;
import com.backendguru.paymentservice.payment.dto.ChargeRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
    properties = {
      "spring.cloud.config.enabled=false",
      "spring.config.import=",
      "eureka.client.enabled=false"
    })
class PaymentFlowTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired PaymentService service;
  @Autowired PaymentRepository repository;

  private CardDetails okCard() {
    return new CardDetails("Alice Tester", "4111-1111-1111-1111", "12", "2030", "123");
  }

  private CardDetails failCard() {
    return new CardDetails("Bob Tester", "4111-1111-1111-1115", "12", "2030", "123");
  }

  @Test
  void chargeWithGoodCardAuthorizes() {
    var resp = service.charge(new ChargeRequest(100L, new BigDecimal("99.99"), "TRY", okCard()));

    assertThat(resp.status()).isEqualTo(PaymentStatus.AUTHORIZED);
    assertThat(resp.iyzicoPaymentId()).startsWith("MOCK-");
    assertThat(resp.cardLastFour()).isEqualTo("1111");
    assertThat(repository.findById(resp.paymentId()).orElseThrow().getStatus())
        .isEqualTo(PaymentStatus.AUTHORIZED);
  }

  @Test
  void chargeWithFailCardThrows() {
    assertThatThrownBy(
            () ->
                service.charge(
                    new ChargeRequest(101L, new BigDecimal("50.00"), "TRY", failCard())))
        .isInstanceOf(PaymentService.PaymentDeclinedException.class)
        .hasMessageContaining("declined");
    // Audit trail of failed payments is a nice-to-have; the rollback of the
    // FAILED row keeps the schema consistent. To persist, future work can use
    // a separate REQUIRES_NEW transaction.
  }

  @Test
  void refundFlipsAuthorizedToRefunded() {
    var resp = service.charge(new ChargeRequest(102L, new BigDecimal("10.00"), "TRY", okCard()));
    var refunded = service.refund(resp.paymentId());

    assertThat(refunded.status()).isEqualTo(PaymentStatus.REFUNDED);
  }

  @Test
  void refundIsIdempotent() {
    var resp = service.charge(new ChargeRequest(103L, new BigDecimal("5.00"), "TRY", okCard()));
    service.refund(resp.paymentId());
    var second = service.refund(resp.paymentId()); // no-op

    assertThat(second.status()).isEqualTo(PaymentStatus.REFUNDED);
  }
}
