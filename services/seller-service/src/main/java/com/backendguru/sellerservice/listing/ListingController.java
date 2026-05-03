package com.backendguru.sellerservice.listing;

import com.backendguru.common.dto.ApiResponse;
import com.backendguru.common.error.UnauthorizedException;
import com.backendguru.sellerservice.listing.dto.ListingDtos.CreateRequest;
import com.backendguru.sellerservice.listing.dto.ListingDtos.ListingResponse;
import com.backendguru.sellerservice.listing.dto.ListingDtos.UpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ListingController {

  private final ListingService service;

  // -------- public catalog enrichment paths --------

  /** All active listings for a product — used by the storefront "Other sellers" panel. */
  @GetMapping("/api/products/{productId:\\d+}/listings")
  public ApiResponse<List<ListingResponse>> publicForProduct(@PathVariable Long productId) {
    return ApiResponse.success(service.publicForProduct(productId));
  }

  /** Single listing by id — used by cart-service when the customer adds a specific offer. */
  @GetMapping("/api/listings/{id:\\d+}")
  public ApiResponse<ListingResponse> publicById(@PathVariable Long id) {
    return ApiResponse.success(service.publicById(id));
  }

  /** Best listing per product (buy box winner). Batched to avoid N+1 from product-service. */
  @GetMapping("/api/listings/best")
  public ApiResponse<Map<Long, ListingResponse>> bestForProducts(
      @RequestParam("productIds") List<Long> productIds) {
    return ApiResponse.success(service.bestListingsForProducts(productIds));
  }

  // -------- seller-only (auth required) --------

  @PostMapping("/api/listings")
  public ResponseEntity<ApiResponse<ListingResponse>> create(
      Authentication auth, @Valid @RequestBody CreateRequest req) {
    Long userId = currentUserId(auth);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(service.createForUser(userId, req)));
  }

  @GetMapping("/api/listings/me")
  public ApiResponse<List<ListingResponse>> listMine(Authentication auth) {
    return ApiResponse.success(service.listForUser(currentUserId(auth)));
  }

  @PutMapping("/api/listings/{id:\\d+}")
  public ApiResponse<ListingResponse> update(
      Authentication auth, @PathVariable Long id, @Valid @RequestBody UpdateRequest req) {
    return ApiResponse.success(service.updateForUser(currentUserId(auth), id, req));
  }

  @DeleteMapping("/api/listings/{id:\\d+}")
  public ResponseEntity<Void> disable(Authentication auth, @PathVariable Long id) {
    service.disableForUser(currentUserId(auth), id);
    return ResponseEntity.noContent().build();
  }

  private Long currentUserId(Authentication auth) {
    if (auth == null || !(auth.getPrincipal() instanceof Long userId)) {
      throw new UnauthorizedException("Authentication required");
    }
    return userId;
  }
}
