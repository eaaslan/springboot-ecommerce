package com.backendguru.catalogstreamservice.catalog;

import com.backendguru.common.error.ResourceNotFoundException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ReactiveCatalogService {

  private final ProductReactiveRepository repository;

  public Flux<ProductRow> page(int page, int size) {
    int p = Math.max(0, page);
    int s = Math.min(Math.max(1, size), 100);
    return repository.findByEnabledTrue(PageRequest.of(p, s));
  }

  public Mono<ProductRow> byId(Long id) {
    return repository
        .findById(id)
        .filter(ProductRow::enabled)
        .switchIfEmpty(Mono.error(new ResourceNotFoundException("Product " + id + " not found")));
  }

  public Flux<ProductRow> search(String q, int limit) {
    if (q == null || q.isBlank()) return Flux.empty();
    int safeLimit = Math.min(Math.max(1, limit), 50);
    return repository.searchEnabled(q.strip(), safeLimit);
  }

  /**
   * SSE demo: emits one new enabled product every {@code intervalSeconds}, cycling through the
   * first page. Useful to show backpressure + Server-Sent Events delivery without contriving
   * unbounded data.
   */
  public Flux<ProductRow> stream(int intervalSeconds) {
    int s = Math.max(1, intervalSeconds);
    return Flux.interval(Duration.ofSeconds(s))
        .onBackpressureDrop()
        .concatMap(tick -> repository.findByEnabledTrue(PageRequest.of(0, 5)));
  }
}
