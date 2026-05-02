package com.backendguru.orderservice.client;

import com.backendguru.common.dto.ApiResponse;
import com.backendguru.orderservice.client.dto.ChargeRequest;
import com.backendguru.orderservice.client.dto.PaymentSnapshot;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-service", fallbackFactory = PaymentClient.Fallback.class)
public interface PaymentClient {

  @PostMapping("/api/payments")
  ApiResponse<PaymentSnapshot> charge(@RequestBody ChargeRequest req);

  @PostMapping("/api/payments/{id}/refund")
  ApiResponse<PaymentSnapshot> refund(@PathVariable("id") Long paymentId);

  @org.springframework.stereotype.Component
  class Fallback implements org.springframework.cloud.openfeign.FallbackFactory<PaymentClient> {
    @Override
    public PaymentClient create(Throwable cause) {
      return new PaymentClient() {
        @Override
        public ApiResponse<PaymentSnapshot> charge(ChargeRequest req) {
          throw new com.backendguru.orderservice.exception.SagaException(
              "Payment service unavailable: " + cause.getMessage(), cause);
        }

        @Override
        public ApiResponse<PaymentSnapshot> refund(Long paymentId) {
          // best-effort: log and continue compensation
          org.slf4j.LoggerFactory.getLogger(PaymentClient.class)
              .warn("refund fallback triggered for payment {}: {}", paymentId, cause.toString());
          return null;
        }
      };
    }
  }
}
