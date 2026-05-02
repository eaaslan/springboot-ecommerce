package com.backendguru.recommendationservice.recommendation;

import com.backendguru.common.dto.ApiResponse;
import com.backendguru.recommendationservice.client.dto.ProductSummary;
import com.backendguru.recommendationservice.recommendation.dto.RecommendationItem;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

  private final RecommendationService service;

  @GetMapping("/products/{id}/similar")
  public ApiResponse<List<RecommendationItem>> similar(
      @PathVariable("id") long productId,
      @RequestParam(value = "k", defaultValue = "5") int k) {
    return ApiResponse.success(service.similarProducts(productId, k));
  }

  @GetMapping("/users/{id}")
  public ApiResponse<List<RecommendationItem>> forUser(
      @PathVariable("id") long userId,
      @RequestParam(value = "k", defaultValue = "5") int k) {
    return ApiResponse.success(service.recommendForUser(userId, k));
  }

  @GetMapping("/search")
  public ApiResponse<List<ProductSummary>> search(
      @RequestParam("q") String query,
      @RequestParam(value = "limit", defaultValue = "10") int limit) {
    return ApiResponse.success(service.searchProducts(query, limit));
  }
}
