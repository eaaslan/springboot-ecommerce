package com.backendguru.cartservice.marketplace;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/** Subset of seller-service ListingResponse that cart-service consumes. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ListingSnapshot(
    Long id,
    Long productId,
    Long sellerId,
    String sellerName,
    BigDecimal priceAmount,
    String priceCurrency,
    int stockQuantity,
    boolean enabled) {}
