package com.backendguru.common.logging;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class CorrelationIdWebFilter implements WebFilter, Ordered {

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String inbound =
        exchange.getRequest().getHeaders().getFirst(LoggingConstants.CORRELATION_ID_HEADER);
    String correlationId =
        (inbound == null || inbound.isBlank()) ? UUID.randomUUID().toString() : inbound;

    exchange.getResponse().getHeaders().set(LoggingConstants.CORRELATION_ID_HEADER, correlationId);

    ServerWebExchange mutated =
        exchange
            .mutate()
            .request(r -> r.header(LoggingConstants.CORRELATION_ID_HEADER, correlationId))
            .build();

    return chain
        .filter(mutated)
        .contextWrite(ctx -> ctx.put(LoggingConstants.MDC_TRACE_ID, correlationId));
  }
}
