package com.backendguru.userservice.auth;

import com.backendguru.common.dto.ApiResponse;
import com.backendguru.userservice.auth.dto.LoginRequest;
import com.backendguru.userservice.auth.dto.RefreshRequest;
import com.backendguru.userservice.auth.dto.RegisterRequest;
import com.backendguru.userservice.auth.dto.TokenResponse;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @PostMapping("/register")
  public ResponseEntity<ApiResponse<Map<String, Long>>> register(
      @Valid @RequestBody RegisterRequest req) {
    Long id = authService.register(req);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(Map.of("id", id)));
  }

  @PostMapping("/login")
  public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest req) {
    return ApiResponse.success(authService.login(req));
  }

  @PostMapping("/refresh")
  public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest req) {
    return ApiResponse.success(authService.refresh(req));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(Authentication auth) {
    if (auth == null || !(auth.getPrincipal() instanceof Long userId)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    authService.logout(userId);
    return ResponseEntity.noContent().build();
  }
}
