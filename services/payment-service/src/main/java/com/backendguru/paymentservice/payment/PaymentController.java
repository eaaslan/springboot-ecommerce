package com.backendguru.paymentservice.payment;

import com.backendguru.common.dto.ApiResponse;
import com.backendguru.paymentservice.payment.dto.ChargeRequest;
import com.backendguru.paymentservice.payment.dto.PaymentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

  private final PaymentService service;

  @PostMapping
  public ResponseEntity<ApiResponse<PaymentResponse>> charge(
      @Valid @RequestBody ChargeRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.charge(req)));
  }

  @PostMapping("/{id}/refund")
  public ApiResponse<PaymentResponse> refund(@PathVariable("id") Long id) {
    return ApiResponse.success(service.refund(id));
  }

  @GetMapping("/{id}")
  public ApiResponse<PaymentResponse> get(@PathVariable("id") Long id) {
    return ApiResponse.success(service.get(id));
  }
}
