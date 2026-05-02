package com.backendguru.catalogstreamservice.exception;

import com.backendguru.common.dto.ErrorResponse;
import com.backendguru.common.error.BusinessException;
import com.backendguru.common.logging.LoggingConstants;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public Mono<ResponseEntity<ErrorResponse>> handle(BusinessException ex, ServerWebExchange ex2) {
    String traceId = MDC.get(LoggingConstants.MDC_TRACE_ID);
    return Mono.just(
        ResponseEntity.status(ex.getErrorCode().getStatus())
            .body(ErrorResponse.from(ex, ex2.getRequest().getPath().value(), traceId)));
  }
}
