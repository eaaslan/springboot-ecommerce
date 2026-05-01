package com.backendguru.apigateway.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class GatewayJwtAuthenticationFilter implements WebFilter, Ordered {

  private static final List<String> PUBLIC_PREFIXES =
      List.of("/api/auth/", "/actuator/", "/v3/api-docs", "/swagger-ui");

  private final SecretKey key;

  public GatewayJwtAuthenticationFilter(@Value("${app.jwt.secret}") String secret) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 10;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String path = exchange.getRequest().getPath().value();
    if (PUBLIC_PREFIXES.stream().anyMatch(path::startsWith)) {
      return chain.filter(exchange);
    }
    if (HttpMethod.GET.equals(exchange.getRequest().getMethod())
        && path.startsWith("/api/products")) {
      return chain.filter(stripUserHeaders(exchange));
    }
    String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    if (header == null || !header.startsWith("Bearer ")) {
      return reject(exchange, "Missing Bearer token");
    }
    try {
      Claims claims =
          Jwts.parser().verifyWith(key).build().parseSignedClaims(header.substring(7)).getPayload();
      String userId = claims.getSubject();
      String role = String.valueOf(claims.get("role"));
      ServerWebExchange mutated =
          exchange
              .mutate()
              .request(
                  r ->
                      r.headers(
                          h -> {
                            h.set("X-User-Id", userId);
                            h.set("X-User-Role", role);
                          }))
              .build();
      return chain.filter(mutated);
    } catch (Exception ex) {
      return reject(exchange, "Invalid or expired token");
    }
  }

  private ServerWebExchange stripUserHeaders(ServerWebExchange exchange) {
    return exchange
        .mutate()
        .request(
            r ->
                r.headers(
                    h -> {
                      h.remove("X-User-Id");
                      h.remove("X-User-Role");
                    }))
        .build();
  }

  private Mono<Void> reject(ServerWebExchange exchange, String msg) {
    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
    byte[] body =
        ("{\"code\":\"UNAUTHORIZED\",\"status\":401,\"message\":\"" + msg + "\"}").getBytes();
    return exchange
        .getResponse()
        .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
  }
}
