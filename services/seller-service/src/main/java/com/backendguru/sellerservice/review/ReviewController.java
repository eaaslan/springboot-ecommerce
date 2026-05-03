package com.backendguru.sellerservice.review;

import com.backendguru.common.dto.ApiResponse;
import com.backendguru.common.error.UnauthorizedException;
import com.backendguru.sellerservice.review.dto.ReviewDtos.CreateRequest;
import com.backendguru.sellerservice.review.dto.ReviewDtos.ReviewResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ReviewController {

  private final ReviewService service;

  /** Create or update the calling user's review for one (seller, product) pair. */
  @PostMapping("/api/reviews")
  public ResponseEntity<ApiResponse<ReviewResponse>> create(
      Authentication auth, @Valid @RequestBody CreateRequest req) {
    Long userId = currentUserId(auth);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(service.upsert(userId, req)));
  }

  /** Public — every review for a given seller, newest first. */
  @GetMapping("/api/sellers/{id:\\d+}/reviews")
  public ApiResponse<List<ReviewResponse>> forSeller(@PathVariable Long id) {
    return ApiResponse.success(service.listForSeller(id));
  }

  /** Public — every review for a given product, newest first. */
  @GetMapping("/api/products/{id:\\d+}/reviews")
  public ApiResponse<List<ReviewResponse>> forProduct(@PathVariable Long id) {
    return ApiResponse.success(service.listForProduct(id));
  }

  private Long currentUserId(Authentication auth) {
    if (auth == null || !(auth.getPrincipal() instanceof Long userId)) {
      throw new UnauthorizedException("Authentication required");
    }
    return userId;
  }
}
