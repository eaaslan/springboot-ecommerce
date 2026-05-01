package com.backendguru.apigateway.exception;

import com.backendguru.common.dto.ErrorResponse;
import com.backendguru.common.error.ErrorCode;
import com.backendguru.common.logging.LoggingConstants;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalErrorWebExceptionHandler implements WebExceptionHandler {

  private final ObjectMapper objectMapper;

  public GlobalErrorWebExceptionHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
    HttpStatus status;
    String code;
    String message;

    if (ex instanceof ResponseStatusException rse) {
      status = HttpStatus.valueOf(rse.getStatusCode().value());
      code = status.name();
      message = rse.getReason() != null ? rse.getReason() : status.getReasonPhrase();
    } else {
      status = HttpStatus.INTERNAL_SERVER_ERROR;
      code = ErrorCode.INTERNAL_ERROR.name();
      message = ex.getMessage() != null ? ex.getMessage() : "Unexpected error";
    }

    String traceId =
        exchange.getResponse().getHeaders().getFirst(LoggingConstants.CORRELATION_ID_HEADER);

    ErrorResponse body =
        ErrorResponse.builder()
            .code(code)
            .message(message)
            .status(status.value())
            .path(exchange.getRequest().getPath().value())
            .traceId(traceId)
            .timestamp(Instant.now())
            .build();

    exchange.getResponse().setStatusCode(status);
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

    byte[] bytes;
    try {
      bytes = objectMapper.writeValueAsBytes(body);
    } catch (JsonProcessingException e) {
      bytes = ("{\"code\":\"" + code + "\",\"status\":" + status.value() + "}").getBytes();
    }
    DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
    return exchange.getResponse().writeWith(Mono.just(buffer));
  }
}
