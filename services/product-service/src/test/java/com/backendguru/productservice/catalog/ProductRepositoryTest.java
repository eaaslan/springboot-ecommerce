package com.backendguru.productservice.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
    properties = {
      "spring.cloud.config.enabled=false",
      "spring.config.import=",
      "eureka.client.enabled=false"
    })
class ProductRepositoryTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired ProductRepository productRepository;
  @Autowired CategoryRepository categoryRepository;
  @PersistenceContext EntityManager em;

  @Test
  void seedDataLoadsViaFlyway() {
    assertThat(categoryRepository.count()).isEqualTo(5);
    assertThat(productRepository.count()).isEqualTo(20);
  }

  @Test
  void paginationHonoursSize() {
    var page = productRepository.findAll((Specification<Product>) null, PageRequest.of(0, 5));
    assertThat(page.getContent()).hasSize(5);
    assertThat(page.getTotalElements()).isEqualTo(20);
  }

  @Test
  void specificationFiltersByCategoryAndPrice() {
    Long electronicsId = categoryRepository.findBySlug("electronics").orElseThrow().getId();
    Specification<Product> spec =
        ProductSpecifications.hasCategory(electronicsId)
            .and(
                ProductSpecifications.priceBetween(new BigDecimal("1000"), new BigDecimal("5000")));
    var page = productRepository.findAll(spec, PageRequest.of(0, 50));
    assertThat(page.getContent())
        .isNotEmpty()
        .allSatisfy(
            p -> {
              assertThat(p.getCategory().getSlug()).isEqualTo("electronics");
              assertThat(p.getPriceAmount())
                  .isBetween(new BigDecimal("1000"), new BigDecimal("5000"));
            });
  }

  @Test
  void findAllWithEntityGraphAvoidsLazyInitException() {
    var page = productRepository.findAll((Specification<Product>) null, PageRequest.of(0, 30));
    em.clear();
    page.getContent().forEach(p -> assertThat(p.getCategory().getName()).isNotNull());
  }

  @Test
  void findWithCategoryByIdLoadsCategoryEagerly() {
    Long anyProductId = productRepository.findAll().get(0).getId();
    var loaded = productRepository.findWithCategoryById(anyProductId).orElseThrow();
    em.clear();
    assertThat(loaded.getCategory().getName()).isNotNull();
  }
}
