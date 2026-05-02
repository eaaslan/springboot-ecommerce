package com.backendguru.recommendationservice.exception;

import com.backendguru.common.dto.ErrorResponse;
import com.backendguru.common.error.BusinessException;
import com.backendguru.common.logging.LoggingConstants;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ErrorResponse> handle(BusinessException ex, HttpServletRequest req) {
    String traceId = MDC.get(LoggingConstants.MDC_TRACE_ID);
    return ResponseEntity.status(ex.getErrorCode().getStatus())
        .body(ErrorResponse.from(ex, req.getRequestURI(), traceId));
  }
}
