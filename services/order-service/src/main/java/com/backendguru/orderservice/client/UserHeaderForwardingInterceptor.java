package com.backendguru.orderservice.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Forwards the current request's {@code X-User-Id} and {@code X-User-Role} headers to every
 * outbound Feign call. Downstream services (cart-service, inventory-service, etc.) trust these
 * headers — without forwarding them they reject the call with 401.
 *
 * <p>The order-service saga also calls cart/inventory/payment from a non-request thread (no
 * RequestContextHolder); in that case we skip silently. The saga is initiated by an HTTP request,
 * so the request context IS present for the duration of {@code placeOrder()}.
 */
@Configuration
public class UserHeaderForwardingInterceptor {

  private static final String USER_ID_HEADER = "X-User-Id";
  private static final String USER_ROLE_HEADER = "X-User-Role";

  @org.springframework.context.annotation.Bean
  public RequestInterceptor userHeaderForwarder() {
    return (RequestTemplate template) -> {
      var attrs = RequestContextHolder.getRequestAttributes();
      if (!(attrs instanceof ServletRequestAttributes sra)) return;
      HttpServletRequest req = sra.getRequest();
      String userId = req.getHeader(USER_ID_HEADER);
      String role = req.getHeader(USER_ROLE_HEADER);
      if (userId != null && !userId.isBlank()) template.header(USER_ID_HEADER, userId);
      if (role != null && !role.isBlank()) template.header(USER_ROLE_HEADER, role);
    };
  }
}
