package com.backendguru.catalogstreamservice.catalog;

import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface ProductReactiveRepository extends ReactiveCrudRepository<ProductRow, Long> {

  Flux<ProductRow> findByEnabledTrue(Pageable pageable);

  @Query("SELECT * FROM products WHERE enabled = true AND name ILIKE '%' || :q || '%' LIMIT :limit")
  Flux<ProductRow> searchEnabled(String q, int limit);
}
