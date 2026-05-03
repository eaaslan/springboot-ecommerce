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
    // CORS preflight requests must skip auth — browser sends them without credentials.
    if (HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())) {
      return chain.filter(exchange);
    }
    String path = exchange.getRequest().getPath().value();
    if (PUBLIC_PREFIXES.stream().anyMatch(path::startsWith)) {
      return chain.filter(exchange);
    }
    if (HttpMethod.GET.equals(exchange.getRequest().getMethod())
        && (path.startsWith("/api/products")
            || path.startsWith("/api/recommendations")
            || path.startsWith("/api/catalog")
            || path.startsWith("/api/listings/best") // marketplace V1 public buy-box
            || path.matches("^/api/sellers/\\d+/public$") // marketplace V1 public seller profile
            || path.matches("^/api/sellers/\\d+/listings$") // marketplace V4 public storefront
            || path.matches(
                "^/api/sellers/\\d+/reviews$") // marketplace V4 public reviews per seller
            || path.matches(
                "^/api/products/\\d+/reviews$") // marketplace V4 public reviews per product
            || path.startsWith("/mcp"))) {
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
