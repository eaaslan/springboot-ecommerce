package com.backendguru.recommendationservice.client;

import com.backendguru.common.dto.ApiResponse;
import com.backendguru.recommendationservice.client.dto.PageResponse;
import com.backendguru.recommendationservice.client.dto.ProductSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "product-service")
public interface ProductClient {

  @GetMapping("/api/products/{id}")
  ApiResponse<ProductSummary> getById(@PathVariable("id") Long id);

  @GetMapping("/api/products")
  ApiResponse<PageResponse<ProductSummary>> list(
      @RequestParam(value = "page", defaultValue = "0") int page,
      @RequestParam(value = "size", defaultValue = "200") int size);

  @GetMapping("/api/products")
  ApiResponse<PageResponse<ProductSummary>> search(
      @RequestParam("q") String query, @RequestParam(value = "size", defaultValue = "20") int size);
}
