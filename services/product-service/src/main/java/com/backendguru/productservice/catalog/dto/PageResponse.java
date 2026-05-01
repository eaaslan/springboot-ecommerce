package com.backendguru.productservice.catalog.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last) {

  public static <T> PageResponse<T> of(Page<T> p) {
    return new PageResponse<>(
        p.getContent(),
        p.getNumber(),
        p.getSize(),
        p.getTotalElements(),
        p.getTotalPages(),
        p.isFirst(),
        p.isLast());
  }
}
