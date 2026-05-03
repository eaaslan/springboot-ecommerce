package com.backendguru.productservice.marketplace;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/** Subset of seller-service ListingResponse — only fields the catalog UI needs. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BestListingDto(
    Long id,
    Long productId,
    Long sellerId,
    String sellerName,
    BigDecimal priceAmount,
    String priceCurrency,
    int stockQuantity,
    int shippingDays) {}
