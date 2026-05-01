package com.backendguru.productservice.catalog;

import com.backendguru.common.dto.ApiResponse;
import com.backendguru.productservice.catalog.dto.CategoryResponse;
import com.backendguru.productservice.catalog.dto.PageResponse;
import com.backendguru.productservice.catalog.dto.ProductCreateRequest;
import com.backendguru.productservice.catalog.dto.ProductFilter;
import com.backendguru.productservice.catalog.dto.ProductResponse;
import com.backendguru.productservice.catalog.dto.ProductUpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

  private static final Set<String> ALLOWED_SORT = Set.of("id", "name", "priceAmount", "createdAt");
  private static final int MAX_PAGE_SIZE = 100;

  private final ProductService service;

  @GetMapping
  public ApiResponse<PageResponse<ProductResponse>> list(
      @ModelAttribute ProductFilter filter,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "id") String sortBy,
      @RequestParam(defaultValue = "asc") String sortDir) {
    String safeSortBy = ALLOWED_SORT.contains(sortBy) ? sortBy : "id";
    int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    Sort sort =
        "desc".equalsIgnoreCase(sortDir)
            ? Sort.by(safeSortBy).descending()
            : Sort.by(safeSortBy).ascending();
    return ApiResponse.success(
        service.list(filter, PageRequest.of(Math.max(page, 0), safeSize, sort)));
  }

  @GetMapping("/{id}")
  public ApiResponse<ProductResponse> getById(@PathVariable Long id) {
    return ApiResponse.success(service.getById(id));
  }

  @GetMapping("/categories")
  public ApiResponse<List<CategoryResponse>> categories() {
    return ApiResponse.success(service.listCategories());
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<ProductResponse>> create(
      @Valid @RequestBody ProductCreateRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.create(req)));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<ProductResponse> update(
      @PathVariable Long id, @Valid @RequestBody ProductUpdateRequest req) {
    return ApiResponse.success(service.update(id, req));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    service.softDelete(id);
    return ResponseEntity.noContent().build();
  }
}
