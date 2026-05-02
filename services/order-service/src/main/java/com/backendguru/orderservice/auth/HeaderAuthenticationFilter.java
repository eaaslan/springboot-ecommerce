package com.backendguru.orderservice.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Slf4j
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

  /**
   * Stop Spring Boot's automatic servlet filter registration; this filter is wired into the Spring
   * Security chain explicitly via {@link SecurityConfig}, so registering it twice would blow away
   * the authentication we just set.
   */
  public static org.springframework.boot.web.servlet.FilterRegistrationBean<
          HeaderAuthenticationFilter>
      disableAutoRegistration(HeaderAuthenticationFilter f) {
    var reg = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(f);
    reg.setEnabled(false);
    return reg;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String userId = req.getHeader("X-User-Id");
    String role = req.getHeader("X-User-Role");
    if (userId != null && !userId.isBlank() && role != null && !role.isBlank()) {
      try {
        var auth =
            new UsernamePasswordAuthenticationToken(
                Long.valueOf(userId), null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
      } catch (NumberFormatException e) {
        log.warn("Invalid X-User-Id header value: {}", userId);
      }
    }
    chain.doFilter(req, res);
  }
}
