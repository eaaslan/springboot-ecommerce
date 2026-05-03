package com.backendguru.sellerservice.seller;

import com.backendguru.common.dto.ApiResponse;
import com.backendguru.common.error.ResourceNotFoundException;
import com.backendguru.common.error.UnauthorizedException;
import com.backendguru.sellerservice.listing.ListingService;
import com.backendguru.sellerservice.listing.dto.ListingDtos.ListingResponse;
import com.backendguru.sellerservice.seller.dto.SellerDtos.AdminUpdateRequest;
import com.backendguru.sellerservice.seller.dto.SellerDtos.ApplyRequest;
import com.backendguru.sellerservice.seller.dto.SellerDtos.SellerPublicResponse;
import com.backendguru.sellerservice.seller.dto.SellerDtos.SellerResponse;
import com.backendguru.sellerservice.seller.dto.SellerDtos.UpdateProfileRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sellers")
@RequiredArgsConstructor
public class SellerController {

  private final SellerService service;
  private final ListingService listingService;

  // -------- user-facing --------

  @PostMapping("/apply")
  public ResponseEntity<ApiResponse<SellerResponse>> apply(
      Authentication auth, @Valid @RequestBody ApplyRequest req) {
    Long userId = currentUserId(auth);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(service.apply(userId, req)));
  }

  @GetMapping("/me")
  public ApiResponse<SellerResponse> me(Authentication auth) {
    Long userId = currentUserId(auth);
    return ApiResponse.success(
        service
            .findByUserId(userId)
            .orElseThrow(() -> new ResourceNotFoundException("No seller record for current user")));
  }

  @PatchMapping("/me")
  public ApiResponse<SellerResponse> updateMe(
      Authentication auth, @Valid @RequestBody UpdateProfileRequest req) {
    Long userId = currentUserId(auth);
    return ApiResponse.success(service.updateProfile(userId, req));
  }

  // -------- public storefront --------

  @GetMapping("/{id:\\d+}/public")
  public ApiResponse<SellerPublicResponse> publicProfile(@PathVariable Long id) {
    return ApiResponse.success(service.publicById(id));
  }

  /** Storefront — every active listing belonging to the seller. */
  @GetMapping("/{id:\\d+}/listings")
  public ApiResponse<List<ListingResponse>> publicListings(@PathVariable Long id) {
    return ApiResponse.success(listingService.publicForSeller(id));
  }

  // -------- admin --------

  @GetMapping("/admin")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<List<SellerResponse>> adminList(
      @RequestParam(required = false) SellerStatus status) {
    return ApiResponse.success(service.listForAdmin(status));
  }

  @PatchMapping("/admin/{id:\\d+}")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<SellerResponse> adminUpdate(
      @PathVariable Long id, @Valid @RequestBody AdminUpdateRequest req) {
    return ApiResponse.success(service.adminUpdate(id, req));
  }

  // -------- helpers --------

  private Long currentUserId(Authentication auth) {
    if (auth == null || !(auth.getPrincipal() instanceof Long userId)) {
      throw new UnauthorizedException("Authentication required");
    }
    return userId;
  }
}
