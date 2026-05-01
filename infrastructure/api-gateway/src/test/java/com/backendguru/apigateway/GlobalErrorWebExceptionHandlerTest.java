package com.backendguru.apigateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.backendguru.apigateway.exception.GlobalErrorWebExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;

class GlobalErrorWebExceptionHandlerTest {

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private final GlobalErrorWebExceptionHandler handler =
      new GlobalErrorWebExceptionHandler(objectMapper);

  private String readBody(MockServerWebExchange exchange) {
    return DataBufferUtils.join(exchange.getResponse().getBody())
        .map(
            buf -> {
              byte[] bytes = new byte[buf.readableByteCount()];
              buf.read(bytes);
              return new String(bytes, StandardCharsets.UTF_8);
            })
        .block();
  }

  @Test
  void writes404ProblemForResponseStatusException() {
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/missing"));
    var ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "no route");
    StepVerifier.create(handler.handle(exchange, ex)).verifyComplete();
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    var body = readBody(exchange);
    assertThat(body).contains("\"status\":404");
    assertThat(body).contains("\"path\":\"/api/missing\"");
  }

  @Test
  void writes500ForGenericException() {
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/boom"));
    var ex = new RuntimeException("kaboom");
    StepVerifier.create(handler.handle(exchange, ex)).verifyComplete();
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    var body = readBody(exchange);
    assertThat(body).contains("\"status\":500");
    assertThat(body).contains("INTERNAL_ERROR");
  }
}
