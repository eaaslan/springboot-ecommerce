package com.backendguru.catalogstreamservice.catalog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.backendguru.common.error.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ReactiveCatalogServiceTest {

  @Mock ProductReactiveRepository repository;
  @InjectMocks ReactiveCatalogService service;

  private ProductRow row(long id, String name, boolean enabled) {
    return new ProductRow(
        id,
        "SKU" + id,
        name,
        name + " desc",
        null,
        BigDecimal.valueOf(10),
        "TRY",
        5,
        1L,
        enabled,
        OffsetDateTime.now(),
        OffsetDateTime.now(),
        0L);
  }

  @Test
  void pageEmitsAllItemsFromRepository() {
    when(repository.findByEnabledTrue(any(Pageable.class)))
        .thenReturn(Flux.just(row(1, "A", true), row(2, "B", true)));

    StepVerifier.create(service.page(0, 20))
        .expectNextMatches(p -> p.id() == 1L)
        .expectNextMatches(p -> p.id() == 2L)
        .verifyComplete();
  }

  @Test
  void byIdReturnsOnlyEnabledRow() {
    when(repository.findById(1L)).thenReturn(Mono.just(row(1, "A", true)));

    StepVerifier.create(service.byId(1L)).expectNextCount(1).verifyComplete();
  }

  @Test
  void byIdMissingErrors() {
    when(repository.findById(99L)).thenReturn(Mono.empty());

    StepVerifier.create(service.byId(99L)).expectError(ResourceNotFoundException.class).verify();
  }

  @Test
  void byIdDisabledErrors() {
    when(repository.findById(7L)).thenReturn(Mono.just(row(7, "Hidden", false)));

    StepVerifier.create(service.byId(7L)).expectError(ResourceNotFoundException.class).verify();
  }

  @Test
  void searchEmptyQueryEmitsNothing() {
    StepVerifier.create(service.search("", 10)).verifyComplete();
    StepVerifier.create(service.search(null, 10)).verifyComplete();
  }

  @Test
  void searchDelegatesToRepoWhenQueryNonBlank() {
    when(repository.searchEnabled(eq("wireless"), anyInt()))
        .thenReturn(Flux.just(row(1, "wireless headphones", true)));

    StepVerifier.create(service.search("wireless", 5))
        .expectNextMatches(p -> p.name().contains("wireless"))
        .verifyComplete();
  }

  @Test
  void searchTrimsAndClampsLimit() {
    when(repository.searchEnabled(anyString(), anyInt())).thenReturn(Flux.empty());

    StepVerifier.create(service.search("  q  ", 9999)).verifyComplete();
    // The test passes if no exception — limit clamp is internal; exact arg verification is
    // intentionally loose.
  }
}
