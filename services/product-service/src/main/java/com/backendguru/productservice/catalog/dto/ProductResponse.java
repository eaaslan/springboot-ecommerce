package com.backendguru.productservice.catalog.dto;

import com.backendguru.productservice.marketplace.BestListingDto;
import java.math.BigDecimal;

public record ProductResponse(
    Long id,
    String sku,
    String name,
    String description,
    String imageUrl,
    BigDecimal priceAmount,
    String priceCurrency,
    int stockQuantity,
    boolean enabled,
    CategoryResponse category,
    /** Marketplace V1: best seller offer (lowest price + shipping). Null if no listings. */
    BestListingDto bestListing) {

  /**
   * Returns a copy with {@code stockQuantity} replaced by the live value from inventory-service.
   * The DB column on product-service is just a seed/fallback; inventory-service is the source of
   * truth.
   */
  public ProductResponse withLiveStock(int liveStock) {
    return new ProductResponse(
        id,
        sku,
        name,
        description,
        imageUrl,
        priceAmount,
        priceCurrency,
        liveStock,
        enabled,
        category,
        bestListing);
  }

  public ProductResponse withBestListing(BestListingDto listing) {
    return new ProductResponse(
        id,
        sku,
        name,
        description,
        imageUrl,
        priceAmount,
        priceCurrency,
        stockQuantity,
        enabled,
        category,
        listing);
  }
}
