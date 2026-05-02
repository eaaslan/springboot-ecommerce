package com.backendguru.recommendationservice.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.backendguru.common.dto.ApiResponse;
import com.backendguru.common.error.ResourceNotFoundException;
import com.backendguru.recommendationservice.client.ProductClient;
import com.backendguru.recommendationservice.client.dto.PageResponse;
import com.backendguru.recommendationservice.client.dto.ProductSummary;
import com.backendguru.recommendationservice.client.dto.ProductSummary.CategoryRef;
import com.backendguru.recommendationservice.recommendation.dto.RecommendationItem;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

  @Mock ProductClient productClient;
  @InjectMocks RecommendationService service;

  private ProductSummary p(long id, String name, double price, long catId, int stock) {
    return new ProductSummary(
        id,
        "SKU-" + id,
        name,
        name + " description",
        BigDecimal.valueOf(price),
        "TRY",
        stock,
        true,
        new CategoryRef(catId, "cat" + catId));
  }

  // -------- score helpers --------

  @Test
  void jaccardEmptyAndOverlap() {
    assertThat(RecommendationService.jaccard(Set.of(), Set.of())).isEqualTo(0.0);
    assertThat(RecommendationService.jaccard(Set.of("a"), Set.of("a"))).isEqualTo(1.0);
    assertThat(RecommendationService.jaccard(Set.of("a", "b"), Set.of("b", "c")))
        .isEqualTo(1.0 / 3.0, within(1e-9));
  }

  @Test
  void priceProximityIdenticalIsOne() {
    assertThat(RecommendationService.priceProximity(BigDecimal.valueOf(100), BigDecimal.valueOf(100)))
        .isEqualTo(1.0, within(1e-9));
  }

  @Test
  void priceProximityFarApartIsLessThanOne() {
    double far =
        RecommendationService.priceProximity(BigDecimal.valueOf(10), BigDecimal.valueOf(1000));
    assertThat(far).isLessThan(0.5).isGreaterThan(0.0);
  }

  @Test
  void tokensSplitsOnNonLettersAndDropsShort() {
    assertThat(RecommendationService.tokens("Wireless Bluetooth-Headphones X1"))
        .containsExactlyInAnyOrder("wireless", "bluetooth", "headphones", "x1");
  }

  // -------- similarProducts --------

  @Test
  void similarRanksSameCategoryHigherThanOtherCategory() {
    ProductSummary target = p(1, "wireless headphones", 100, 1L, 10);
    ProductSummary sameCat = p(2, "wireless earbuds", 110, 1L, 5);
    ProductSummary otherCat = p(3, "wireless headphones", 105, 99L, 5);
    ProductSummary unrelated = p(4, "garden hose", 50, 2L, 5);

    when(productClient.getById(1L)).thenReturn(ApiResponse.success(target));
    when(productClient.list(anyInt(), anyInt()))
        .thenReturn(
            ApiResponse.success(
                new PageResponse<>(List.of(target, sameCat, otherCat, unrelated), 0, 200, 4)));

    List<RecommendationItem> top = service.similarProducts(1L, 3);

    assertThat(top).hasSize(3);
    // sameCat (cat match + token overlap) should outrank otherCat (only token overlap)
    assertThat(top.get(0).id()).isEqualTo(2L);
    assertThat(top.stream().map(RecommendationItem::id)).doesNotContain(1L);
    // unrelated should rank lowest
    assertThat(top.get(2).id()).isEqualTo(4L);
  }

  @Test
  void similarThrowsWhenTargetMissing() {
    when(productClient.getById(99L)).thenReturn(ApiResponse.success(null));
    assertThatThrownBy(() -> service.similarProducts(99L, 5))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  // -------- recommendForUser --------

  @Test
  void recommendForUserPopularityProxyByStock() {
    ProductSummary low = p(1, "low stock", 10, 1L, 3);
    ProductSummary high = p(2, "high stock", 10, 1L, 100);
    ProductSummary mid = p(3, "mid stock", 10, 1L, 50);
    when(productClient.list(anyInt(), anyInt()))
        .thenReturn(ApiResponse.success(new PageResponse<>(List.of(low, high, mid), 0, 200, 3)));

    List<RecommendationItem> top = service.recommendForUser(42L, 2);
    assertThat(top).hasSize(2);
    assertThat(top.get(0).id()).isEqualTo(2L);
    assertThat(top.get(1).id()).isEqualTo(3L);
  }

  // -------- searchProducts --------

  @Test
  void searchReturnsItemsWhoseNameOverlapsQuery() {
    when(productClient.list(anyInt(), anyInt()))
        .thenReturn(
            ApiResponse.success(
                new PageResponse<>(
                    List.of(
                        p(1, "wireless headphones", 100, 1L, 5),
                        p(2, "garden hose", 50, 2L, 5),
                        p(3, "wireless mouse", 30, 3L, 5)),
                    0,
                    200,
                    3)));

    List<ProductSummary> hits = service.searchProducts("wireless", 5);
    assertThat(hits.stream().map(ProductSummary::id)).containsExactlyInAnyOrder(1L, 3L);
  }

  @Test
  void searchEmptyQueryReturnsEmpty() {
    assertThat(service.searchProducts("", 5)).isEmpty();
    assertThat(service.searchProducts(null, 5)).isEmpty();
  }
}
