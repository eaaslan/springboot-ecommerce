package com.backendguru.recommendationservice.recommendation;

import com.backendguru.common.error.ResourceNotFoundException;
import com.backendguru.recommendationservice.client.ProductClient;
import com.backendguru.recommendationservice.client.dto.ProductSummary;
import com.backendguru.recommendationservice.recommendation.dto.RecommendationItem;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Content-based recommender. No DB; pulls candidates from product-service via Feign.
 *
 * <p>Score(target, candidate) = 1.0 * categoryMatch + 0.6 * jaccard(nameTokens) + 0.4 *
 * priceProximity. Scores are bounded in [0, 2.0]. Higher = more similar.
 *
 * <p>Phase 9 keeps the algorithm transparent and deterministic. Phase 9.1 swaps in vector
 * embeddings + cosine similarity (pgvector) — same orchestration, same MCP tool surface.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {

  private static final int CANDIDATE_POOL_SIZE = 200;
  private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{L}0-9]+");

  private final ProductClient productClient;

  public List<RecommendationItem> similarProducts(long productId, int k) {
    if (k <= 0) k = 5;
    ProductSummary target =
        unwrap(productClient.getById(productId).data(), "Product " + productId + " not found");
    List<ProductSummary> pool = fetchPool();
    Set<String> targetTokens = tokens(target.name());
    return pool.stream()
        .filter(p -> p.enabled() && !p.id().equals(target.id()))
        .map(p -> new Scored(p, score(target, targetTokens, p)))
        .sorted(Comparator.comparingDouble(Scored::score).reversed())
        .limit(k)
        .map(s -> RecommendationItem.of(s.product(), s.score()))
        .toList();
  }

  public List<RecommendationItem> recommendForUser(long userId, int k) {
    // Phase 9 stub: popularity by stock proxy. Phase 10 replaces with order-history scoring.
    if (k <= 0) k = 5;
    return fetchPool().stream()
        .filter(ProductSummary::enabled)
        .sorted(Comparator.comparingInt(ProductSummary::stockQuantity).reversed())
        .limit(k)
        .map(p -> RecommendationItem.of(p, p.stockQuantity()))
        .toList();
  }

  public List<ProductSummary> searchProducts(String query, int limit) {
    if (limit <= 0) limit = 10;
    if (query == null || query.isBlank()) return List.of();
    Set<String> qTokens = tokens(query);
    return fetchPool().stream()
        .filter(ProductSummary::enabled)
        .filter(p -> overlaps(qTokens, tokens(p.name())))
        .limit(limit)
        .toList();
  }

  // -------------------- internals --------------------

  private List<ProductSummary> fetchPool() {
    var resp = productClient.list(0, CANDIDATE_POOL_SIZE).data();
    return resp == null || resp.content() == null ? List.of() : resp.content();
  }

  private double score(ProductSummary target, Set<String> targetTokens, ProductSummary candidate) {
    double categoryMatch =
        target.category() != null
                && candidate.category() != null
                && target.category().id() != null
                && target.category().id().equals(candidate.category().id())
            ? 1.0
            : 0.0;
    double tokenJaccard = jaccard(targetTokens, tokens(candidate.name()));
    double priceProx = priceProximity(target.priceAmount(), candidate.priceAmount());
    return categoryMatch + 0.6 * tokenJaccard + 0.4 * priceProx;
  }

  static Set<String> tokens(String s) {
    if (s == null || s.isBlank()) return Set.of();
    Set<String> out = new HashSet<>();
    for (String t : TOKEN_SPLIT.split(s.toLowerCase())) {
      if (t.length() >= 2) out.add(t);
    }
    return out;
  }

  static double jaccard(Set<String> a, Set<String> b) {
    if (a.isEmpty() && b.isEmpty()) return 0.0;
    Set<String> inter = new HashSet<>(a);
    inter.retainAll(b);
    Set<String> union = new HashSet<>(a);
    union.addAll(b);
    return union.isEmpty() ? 0.0 : (double) inter.size() / union.size();
  }

  static double priceProximity(BigDecimal a, BigDecimal b) {
    if (a == null || b == null) return 0.0;
    double da = a.doubleValue();
    double db = b.doubleValue();
    if (da <= 0 || db <= 0) return 0.0;
    double diff = Math.abs(Math.log(da) - Math.log(db));
    return 1.0 / (1.0 + diff);
  }

  private static boolean overlaps(Set<String> a, Set<String> b) {
    for (String t : a) if (b.contains(t)) return true;
    return false;
  }

  private static <T> T unwrap(T value, String notFoundMessage) {
    if (value == null) throw new ResourceNotFoundException(notFoundMessage);
    return value;
  }

  private record Scored(ProductSummary product, double score) {}
}
