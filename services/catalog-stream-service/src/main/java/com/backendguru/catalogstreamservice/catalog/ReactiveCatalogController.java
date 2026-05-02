package com.backendguru.catalogstreamservice.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class ReactiveCatalogController {

  private final ReactiveCatalogService service;

  @GetMapping("/products")
  public Flux<ProductRow> page(
      @RequestParam(value = "page", defaultValue = "0") int page,
      @RequestParam(value = "size", defaultValue = "20") int size) {
    return service.page(page, size);
  }

  @GetMapping("/products/{id}")
  public Mono<ProductRow> byId(@PathVariable Long id) {
    return service.byId(id);
  }

  @GetMapping("/products/search")
  public Flux<ProductRow> search(
      @RequestParam("q") String query,
      @RequestParam(value = "limit", defaultValue = "10") int limit) {
    return service.search(query, limit);
  }

  /** Server-Sent Events: keeps the connection open and pushes products at intervals. */
  @GetMapping(value = "/products/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<ProductRow> stream(
      @RequestParam(value = "intervalSeconds", defaultValue = "2") int intervalSeconds) {
    return service.stream(intervalSeconds);
  }
}
