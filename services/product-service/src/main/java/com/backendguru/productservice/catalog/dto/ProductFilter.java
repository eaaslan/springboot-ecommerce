package com.backendguru.productservice.catalog.dto;

import java.math.BigDecimal;

public record ProductFilter(
    String name, Long categoryId, BigDecimal minPrice, BigDecimal maxPrice, Boolean inStock) {}
