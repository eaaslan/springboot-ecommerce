package com.backendguru.cartservice.product.dto;

import java.math.BigDecimal;

public record ProductSnapshot(
    Long id,
    String name,
    BigDecimal priceAmount,
    String priceCurrency,
    int stockQuantity,
    boolean enabled) {}
