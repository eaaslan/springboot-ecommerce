package com.backendguru.productservice.catalog.dto;

import com.backendguru.productservice.catalog.Category;
import com.backendguru.productservice.catalog.Product;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProductMapper {

  ProductResponse toResponse(Product product);

  CategoryResponse toCategoryResponse(Category category);
}
