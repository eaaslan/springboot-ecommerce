package com.backendguru.common.dto;

import com.backendguru.common.error.BusinessException;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;
import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    String code,
    String message,
    int status,
    String path,
    String traceId,
    Instant timestamp,
    Map<String, Object> details) {

  public static ErrorResponse from(BusinessException ex, String path, String traceId) {
    return ErrorResponse.builder()
        .code(ex.getErrorCode().name())
        .message(ex.getMessage())
        .status(ex.getErrorCode().getStatus().value())
        .path(path)
        .traceId(traceId)
        .timestamp(Instant.now())
        .build();
  }
}
