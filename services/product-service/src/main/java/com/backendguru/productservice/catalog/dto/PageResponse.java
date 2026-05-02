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

  /** Returns a copy of this page with the same metadata but a new content list. */
  public PageResponse<T> withContent(List<T> newContent) {
    return new PageResponse<>(newContent, page, size, totalElements, totalPages, first, last);
  }
}
