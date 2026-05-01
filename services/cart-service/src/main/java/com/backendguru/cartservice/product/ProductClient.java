package com.backendguru.cartservice.product;

import com.backendguru.cartservice.product.dto.ProductSnapshot;
import com.backendguru.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "product-service", fallbackFactory = ProductClientFallbackFactory.class)
public interface ProductClient {

  @GetMapping("/api/products/{id}")
  ApiResponse<ProductSnapshot> getById(@PathVariable("id") Long id);
}
