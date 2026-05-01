package com.backendguru.productservice.catalog;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.lang.Nullable;

public interface ProductRepository
    extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

  Optional<Product> findBySku(String sku);

  @EntityGraph(attributePaths = "category")
  Optional<Product> findWithCategoryById(Long id);

  @Override
  @EntityGraph(attributePaths = "category")
  Page<Product> findAll(@Nullable Specification<Product> spec, Pageable pageable);
}
