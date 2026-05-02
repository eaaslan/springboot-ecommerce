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
import com.backendguru.productservice.inventory.InventoryStatusDto;
import com.backendguru.productservice.inventory.InventoryStockClient;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
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
  private final InventoryStockClient inventoryClient;

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

    PageResponse<ProductResponse> page =
        PageResponse.of(productRepository.findAll(spec, pageable).map(mapper::toResponse));
    return page.withContent(enrichWithLiveStock(page.content()));
  }

  @Cacheable(cacheNames = "productById", key = "#id")
  @Transactional(readOnly = true)
  public ProductResponse getById(Long id) {
    Product p =
        productRepository
            .findWithCategoryById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Product " + id + " not found"));
    return mapper.toResponse(p);
  }

  /**
   * getById + live stock enrichment. Kept outside the cache so stock stays fresh while product
   * metadata stays cached.
   */
  public ProductResponse getByIdWithLiveStock(Long id) {
    ProductResponse base = getById(id);
    Map<Long, Integer> stocks = fetchLiveStock(List.of(id));
    Integer live = stocks.get(id);
    return live == null ? base : base.withLiveStock(live.intValue());
  }

  // -------- internals --------

  private List<ProductResponse> enrichWithLiveStock(List<ProductResponse> products) {
    if (products.isEmpty()) return products;
    List<Long> ids = products.stream().map(ProductResponse::id).toList();
    Map<Long, Integer> stocks = fetchLiveStock(ids);
    return products.stream()
        .map(
            p -> {
              Integer live = stocks.get(p.id());
              return live == null ? p : p.withLiveStock(live.intValue());
            })
        .toList();
  }

  /** Returns map productId → availableQty. Empty map on Feign failure (fallback). */
  private Map<Long, Integer> fetchLiveStock(List<Long> ids) {
    try {
      var resp = inventoryClient.statusBatch(ids);
      List<InventoryStatusDto> data = resp.data();
      if (data == null) return Map.of();
      return data.stream()
          .collect(
              Collectors.toMap(
                  InventoryStatusDto::productId, InventoryStatusDto::availableQty, (a, b) -> a));
    } catch (Exception e) {
      return Map.of();
    }
  }

  @Transactional(readOnly = true)
  public List<CategoryResponse> listCategories() {
    return categoryRepository.findAll().stream().map(mapper::toCategoryResponse).toList();
  }

  @Caching(
      evict = {
        @CacheEvict(cacheNames = "productById", allEntries = true),
        @CacheEvict(cacheNames = "productPage", allEntries = true)
      })
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

  @Caching(
      evict = {
        @CacheEvict(cacheNames = "productById", key = "#id"),
        @CacheEvict(cacheNames = "productPage", allEntries = true)
      })
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

  @Caching(
      evict = {
        @CacheEvict(cacheNames = "productById", key = "#id"),
        @CacheEvict(cacheNames = "productPage", allEntries = true)
      })
  @Transactional
  public void softDelete(Long id) {
    Product p =
        productRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Product " + id + " not found"));
    p.setEnabled(false);
  }
}
