package com.backendguru.productservice.catalog;

import com.backendguru.common.error.DuplicateResourceException;
import com.backendguru.common.error.ResourceNotFoundException;
import com.backendguru.productservice.catalog.dto.CategoryResponse;
import com.backendguru.productservice.catalog.dto.PageResponse;
import com.backendguru.productservice.catalog.dto.ProductCreateRequest;
import com.backendguru.productservice.catalog.dto.ProductFilter;
import com.backendguru.productservice.catalog.dto.ProductMapper;
import com.backendguru.productservice.catalog.dto.ProductResponse;
import com.backendguru.productservice.catalog.dto.ProductUpdateRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

  private final ProductRepository productRepository;
  private final CategoryRepository categoryRepository;
  private final ProductMapper mapper;

  @Transactional(readOnly = true)
  public PageResponse<ProductResponse> list(ProductFilter filter, Pageable pageable) {
    Specification<Product> spec = ProductSpecifications.enabledOnly();
    Specification<Product> nameSpec = ProductSpecifications.nameContains(filter.name());
    if (nameSpec != null) spec = spec.and(nameSpec);
    Specification<Product> catSpec = ProductSpecifications.hasCategory(filter.categoryId());
    if (catSpec != null) spec = spec.and(catSpec);
    Specification<Product> priceSpec =
        ProductSpecifications.priceBetween(filter.minPrice(), filter.maxPrice());
    if (priceSpec != null) spec = spec.and(priceSpec);
    if (Boolean.TRUE.equals(filter.inStock())) spec = spec.and(ProductSpecifications.inStock());

    return PageResponse.of(productRepository.findAll(spec, pageable).map(mapper::toResponse));
  }

  @Transactional(readOnly = true)
  public ProductResponse getById(Long id) {
    Product p =
        productRepository
            .findWithCategoryById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Product " + id + " not found"));
    return mapper.toResponse(p);
  }

  @Transactional(readOnly = true)
  public List<CategoryResponse> listCategories() {
    return categoryRepository.findAll().stream().map(mapper::toCategoryResponse).toList();
  }

  @Transactional
  public ProductResponse create(ProductCreateRequest req) {
    if (productRepository.findBySku(req.sku()).isPresent()) {
      throw new DuplicateResourceException("SKU already exists: " + req.sku());
    }
    Category cat =
        categoryRepository
            .findById(req.categoryId())
            .orElseThrow(
                () -> new ResourceNotFoundException("Category " + req.categoryId() + " not found"));
    Product p =
        Product.builder()
            .sku(req.sku())
            .name(req.name())
            .description(req.description())
            .imageUrl(req.imageUrl())
            .priceAmount(req.priceAmount())
            .priceCurrency(req.priceCurrency())
            .stockQuantity(req.stockQuantity())
            .category(cat)
            .enabled(true)
            .build();
    return mapper.toResponse(productRepository.save(p));
  }

  @Transactional
  public ProductResponse update(Long id, ProductUpdateRequest req) {
    Product p =
        productRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Product " + id + " not found"));
    if (req.name() != null) p.setName(req.name());
    if (req.description() != null) p.setDescription(req.description());
    if (req.imageUrl() != null) p.setImageUrl(req.imageUrl());
    if (req.priceAmount() != null) p.setPriceAmount(req.priceAmount());
    if (req.priceCurrency() != null) p.setPriceCurrency(req.priceCurrency());
    if (req.stockQuantity() != null) p.setStockQuantity(req.stockQuantity());
    if (req.enabled() != null) p.setEnabled(req.enabled());
    if (req.categoryId() != null && !req.categoryId().equals(p.getCategory().getId())) {
      Category cat =
          categoryRepository
              .findById(req.categoryId())
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException("Category " + req.categoryId() + " not found"));
      p.setCategory(cat);
    }
    return mapper.toResponse(p);
  }

  @Transactional
  public void softDelete(Long id) {
    Product p =
        productRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Product " + id + " not found"));
    p.setEnabled(false);
  }
}
