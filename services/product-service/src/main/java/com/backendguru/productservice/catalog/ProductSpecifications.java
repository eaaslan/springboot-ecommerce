package com.backendguru.productservice.catalog;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import org.springframework.data.jpa.domain.Specification;

public final class ProductSpecifications {

  private ProductSpecifications() {}

  public static Specification<Product> nameContains(String q) {
    if (q == null || q.isBlank()) return null;
    String like = "%" + q.toLowerCase() + "%";
    return (root, cq, cb) -> cb.like(cb.lower(root.get("name")), like);
  }

  public static Specification<Product> hasCategory(Long categoryId) {
    if (categoryId == null) return null;
    return (root, cq, cb) -> cb.equal(root.get("category").get("id"), categoryId);
  }

  public static Specification<Product> priceBetween(BigDecimal min, BigDecimal max) {
    if (min == null && max == null) return null;
    return (root, cq, cb) -> {
      Predicate p = cb.conjunction();
      if (min != null) p = cb.and(p, cb.greaterThanOrEqualTo(root.get("priceAmount"), min));
      if (max != null) p = cb.and(p, cb.lessThanOrEqualTo(root.get("priceAmount"), max));
      return p;
    };
  }

  public static Specification<Product> inStock() {
    return (root, cq, cb) -> cb.greaterThan(root.get("stockQuantity"), 0);
  }

  public static Specification<Product> enabledOnly() {
    return (root, cq, cb) -> cb.isTrue(root.get("enabled"));
  }
}
