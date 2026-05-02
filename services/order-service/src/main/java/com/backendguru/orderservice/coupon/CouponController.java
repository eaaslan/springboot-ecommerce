package com.backendguru.orderservice.coupon;

import com.backendguru.common.dto.ApiResponse;
import com.backendguru.common.error.UnauthorizedException;
import com.backendguru.orderservice.coupon.dto.CouponDtos.CouponCreateRequest;
import com.backendguru.orderservice.coupon.dto.CouponDtos.CouponResponse;
import com.backendguru.orderservice.coupon.dto.CouponDtos.CouponUpdateRequest;
import com.backendguru.orderservice.coupon.dto.CouponDtos.ValidateCouponRequest;
import com.backendguru.orderservice.coupon.dto.CouponDtos.ValidateCouponResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {

  private final CouponService service;

  // -------- admin --------

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<List<CouponResponse>> list() {
    return ApiResponse.success(service.list());
  }

  @GetMapping("/{id:\\d+}")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<CouponResponse> byId(@PathVariable Long id) {
    return ApiResponse.success(service.byId(id));
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<CouponResponse>> create(
      @Valid @RequestBody CouponCreateRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.create(req)));
  }

  @PutMapping("/{id:\\d+}")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<CouponResponse> update(
      @PathVariable Long id, @Valid @RequestBody CouponUpdateRequest req) {
    return ApiResponse.success(service.update(id, req));
  }

  @DeleteMapping("/{id:\\d+}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> disable(@PathVariable Long id) {
    service.disable(id);
    return ResponseEntity.noContent().build();
  }

  // -------- user-facing preview --------

  @PostMapping("/validate")
  public ApiResponse<ValidateCouponResponse> validate(
      Authentication auth, @Valid @RequestBody ValidateCouponRequest req) {
    Long userId = currentUserId(auth);
    return ApiResponse.success(service.preview(req.code(), req.orderAmount(), userId));
  }

  private Long currentUserId(Authentication auth) {
    if (auth == null || !(auth.getPrincipal() instanceof Long userId)) {
      throw new UnauthorizedException("Authentication required");
    }
    return userId;
  }
}
