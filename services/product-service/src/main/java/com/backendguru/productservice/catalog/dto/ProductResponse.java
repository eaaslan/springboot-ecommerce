package com.backendguru.productservice.catalog.dto;

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
    CategoryResponse category) {}
