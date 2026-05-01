package com.backendguru.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CorrelationIdWebFilterTest {

  private final CorrelationIdWebFilter filter = new CorrelationIdWebFilter();

  @Test
  void usesIncomingHeaderWhenPresent() {
    var request =
        MockServerHttpRequest.get("/")
            .header(LoggingConstants.CORRELATION_ID_HEADER, "abc-123")
            .build();
    var exchange = MockServerWebExchange.from(request);

    WebFilterChain chain = ex -> Mono.empty();
    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertThat(exchange.getResponse().getHeaders().getFirst(LoggingConstants.CORRELATION_ID_HEADER))
        .isEqualTo("abc-123");
  }

  @Test
  void generatesUuidWhenHeaderMissing() {
    var request = MockServerHttpRequest.get("/").build();
    var exchange = MockServerWebExchange.from(request);

    WebFilterChain chain = ex -> Mono.empty();
    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    var generated =
        exchange.getResponse().getHeaders().getFirst(LoggingConstants.CORRELATION_ID_HEADER);
    assertThat(generated).isNotBlank();
    UUID.fromString(generated);
  }

  @Test
  void putsTraceIdIntoReactorContext() {
    var request =
        MockServerHttpRequest.get("/")
            .header(LoggingConstants.CORRELATION_ID_HEADER, "ctx-id")
            .build();
    var exchange = MockServerWebExchange.from(request);

    WebFilterChain chain =
        ex ->
            Mono.deferContextual(
                ctx -> {
                  String traceId = ctx.get(LoggingConstants.MDC_TRACE_ID);
                  assertThat(traceId).isEqualTo("ctx-id");
                  return Mono.empty();
                });

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
  }
}
