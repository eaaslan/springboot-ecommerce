package com.backendguru.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ErrorResponse error, Instant timestamp) {

  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(true, data, null, Instant.now());
  }

  public static <T> ApiResponse<T> failure(ErrorResponse error) {
    return new ApiResponse<>(false, null, error, Instant.now());
  }
}
