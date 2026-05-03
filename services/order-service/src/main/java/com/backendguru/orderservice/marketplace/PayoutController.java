package com.backendguru.orderservice.marketplace;

import com.backendguru.common.dto.ApiResponse;
import com.backendguru.orderservice.marketplace.dto.PayoutDtos.PayoutResponse;
import com.backendguru.orderservice.marketplace.dto.PayoutDtos.RunRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/payouts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PayoutController {

  private final PayoutService service;

  @PostMapping("/run")
  public ResponseEntity<ApiResponse<List<PayoutResponse>>> run(@Valid @RequestBody RunRequest req) {
    var created =
        service.runForPeriod(req.periodStart(), req.periodEnd()).stream()
            .map(PayoutResponse::from)
            .toList();
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
  }

  @GetMapping
  public ApiResponse<List<PayoutResponse>> list() {
    return ApiResponse.success(service.listAll().stream().map(PayoutResponse::from).toList());
  }

  @PostMapping("/{id:\\d+}/mark-paid")
  public ApiResponse<PayoutResponse> markPaid(@PathVariable Long id) {
    return ApiResponse.success(PayoutResponse.from(service.markPaid(id)));
  }
}
