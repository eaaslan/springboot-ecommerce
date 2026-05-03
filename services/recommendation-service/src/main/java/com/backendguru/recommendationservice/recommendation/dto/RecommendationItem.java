package com.backendguru.recommendationservice.recommendation.dto;

import com.backendguru.recommendationservice.client.dto.ProductSummary;
import java.math.BigDecimal;

public record RecommendationItem(
    Long id,
    String name,
    String imageUrl,
    BigDecimal priceAmount,
    String priceCurrency,
    double score) {

  public static RecommendationItem of(ProductSummary p, double score) {
    return new RecommendationItem(
        p.id(), p.name(), p.imageUrl(), p.priceAmount(), p.priceCurrency(), score);
  }
}
