package com.backendguru.userservice.user.dto;

import com.backendguru.userservice.user.Role;
import java.util.List;

public record UserResponse(
    Long id, String email, Role role, boolean enabled, List<AddressDto> addresses) {
  public record AddressDto(Long id, String line1, String city, String country, boolean isDefault) {}
}
