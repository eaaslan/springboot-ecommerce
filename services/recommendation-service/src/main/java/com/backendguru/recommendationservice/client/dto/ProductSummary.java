package com.backendguru.recommendationservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductSummary(
    Long id,
    String sku,
    String name,
    String description,
    BigDecimal priceAmount,
    String priceCurrency,
    int stockQuantity,
    boolean enabled,
    CategoryRef category) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record CategoryRef(Long id, String name) {}
}
