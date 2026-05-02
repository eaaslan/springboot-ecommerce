package com.backendguru.apigateway.ratelimit;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {

  /**
   * Rate-limit key by client IP. Behind a CDN/LB the gateway should respect {@code
   * X-Forwarded-For}; production would parse that header to find the real source IP.
   */
  @Bean
  public KeyResolver ipKeyResolver() {
    return exchange ->
        Mono.just(
            exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "anonymous");
  }
}
