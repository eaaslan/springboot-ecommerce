package com.backendguru.userservice.user;

import com.backendguru.common.dto.ApiResponse;
import com.backendguru.common.error.UnauthorizedException;
import com.backendguru.userservice.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService service;

  @GetMapping("/me")
  public ApiResponse<UserResponse> me(Authentication auth) {
    return ApiResponse.success(service.findById(currentUserId(auth)));
  }

  @GetMapping("/me/with-addresses")
  public ApiResponse<UserResponse> meWithAddresses(Authentication auth) {
    return ApiResponse.success(service.findByIdWithAddresses(currentUserId(auth)));
  }

  private Long currentUserId(Authentication auth) {
    if (auth == null || !(auth.getPrincipal() instanceof Long userId)) {
      throw new UnauthorizedException("Authentication required");
    }
    return userId;
  }
}
