package com.backendguru.productservice.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProductCreateRequest(
    @NotBlank @Size(max = 60) String sku,
    @NotBlank @Size(max = 200) String name,
    String description,
    @Size(max = 500) String imageUrl,
    @NotNull @Positive BigDecimal priceAmount,
    @NotBlank @Size(min = 3, max = 3) String priceCurrency,
    @NotNull @PositiveOrZero Integer stockQuantity,
    @NotNull Long categoryId) {}
