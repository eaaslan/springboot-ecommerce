package com.backendguru.productservice.exception;

import com.backendguru.common.dto.ErrorResponse;
import com.backendguru.common.error.BusinessException;
import com.backendguru.common.error.ErrorCode;
import com.backendguru.common.logging.LoggingConstants;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
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

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest req) {
    Map<String, Object> details =
        ex.getBindingResult().getFieldErrors().stream()
            .collect(
                Collectors.toMap(
                    FieldError::getField,
                    fe -> fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage(),
                    (a, b) -> a));
    String traceId = MDC.get(LoggingConstants.MDC_TRACE_ID);
    return ResponseEntity.badRequest()
        .body(
            ErrorResponse.builder()
                .code(ErrorCode.VALIDATION_FAILED.name())
                .message("Request payload failed validation")
                .status(400)
                .path(req.getRequestURI())
                .traceId(traceId)
                .timestamp(Instant.now())
                .details(details)
                .build());
  }
}
