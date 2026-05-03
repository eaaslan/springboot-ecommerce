package com.backendguru.sellerservice.listing.dto;

import com.backendguru.sellerservice.listing.Listing;
import com.backendguru.sellerservice.listing.ListingCondition;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public final class ListingDtos {

  private ListingDtos() {}

  public record CreateRequest(
      @NotNull Long productId,
      @NotNull @Positive BigDecimal priceAmount,
      String priceCurrency,
      @PositiveOrZero Integer stockQuantity,
      ListingCondition condition,
      Integer shippingDays) {}

  public record UpdateRequest(
      BigDecimal priceAmount,
      String priceCurrency,
      Integer stockQuantity,
      Integer shippingDays,
      Boolean enabled) {}

  public record ListingResponse(
      Long id,
      Long productId,
      Long sellerId,
      String sellerName, // denormalized for client UI
      BigDecimal priceAmount,
      String priceCurrency,
      int stockQuantity,
      ListingCondition condition,
      int shippingDays,
      boolean enabled,
      OffsetDateTime updatedAt) {

    public static ListingResponse from(Listing l, String sellerName) {
      return new ListingResponse(
          l.getId(),
          l.getProductId(),
          l.getSellerId(),
          sellerName,
          l.getPriceAmount(),
          l.getPriceCurrency(),
          l.getStockQuantity(),
          l.getCondition(),
          l.getShippingDays(),
          l.isEnabled(),
          l.getUpdatedAt());
    }
  }
}
