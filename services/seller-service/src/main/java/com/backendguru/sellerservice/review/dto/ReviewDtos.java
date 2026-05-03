package com.backendguru.sellerservice.review.dto;

import com.backendguru.sellerservice.review.Review;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public final class ReviewDtos {

  private ReviewDtos() {}

  public record CreateRequest(
      @NotNull Long sellerId,
      @NotNull Long productId,
      @NotNull @Min(1) @Max(5) Integer rating,
      @Size(max = 2000) String body) {}

  public record ReviewResponse(
      Long id,
      Long sellerId,
      Long productId,
      Long userId,
      int rating,
      String body,
      OffsetDateTime createdAt) {

    public static ReviewResponse from(Review r) {
      return new ReviewResponse(
          r.getId(),
          r.getSellerId(),
          r.getProductId(),
          r.getUserId(),
          r.getRating(),
          r.getBody(),
          r.getCreatedAt());
    }
  }

  public record SellerRatingSummary(BigDecimal averageRating, long ratingCount) {}
}
