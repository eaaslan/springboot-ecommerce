package com.backendguru.cartservice.product;

import com.backendguru.cartservice.exception.ProductUnavailableException;
import com.backendguru.cartservice.product.dto.ProductSnapshot;
import com.backendguru.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ProductClientFallbackFactory implements FallbackFactory<ProductClient> {

  @Override
  public ProductClient create(Throwable cause) {
    log.warn("ProductClient fallback engaged: {}", cause.toString());
    return new ProductClient() {
      @Override
      public ApiResponse<ProductSnapshot> getById(Long id) {
        throw new ProductUnavailableException(
            "Product " + id + " is temporarily unavailable", cause);
      }
    };
  }
}
