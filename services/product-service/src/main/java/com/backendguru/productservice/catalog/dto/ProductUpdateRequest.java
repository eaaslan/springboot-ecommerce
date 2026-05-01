package com.backendguru.productservice.catalog.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProductUpdateRequest(
    @Size(max = 200) String name,
    String description,
    @Size(max = 500) String imageUrl,
    @Positive BigDecimal priceAmount,
    @Size(min = 3, max = 3) String priceCurrency,
    @PositiveOrZero Integer stockQuantity,
    Long categoryId,
    Boolean enabled) {}
