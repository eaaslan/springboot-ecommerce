package com.backendguru.userservice.user;

import com.backendguru.common.error.ResourceNotFoundException;
import com.backendguru.userservice.user.dto.UserMapper;
import com.backendguru.userservice.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository repository;
  private final UserMapper mapper;

  @Transactional(readOnly = true)
  public UserResponse findById(Long id) {
    User user =
        repository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User " + id + " not found"));
    return mapper.toResponse(user);
  }

  @Transactional(readOnly = true)
  public UserResponse findByIdWithAddresses(Long id) {
    User user =
        repository
            .findWithAddressesById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User " + id + " not found"));
    return mapper.toResponseWithAddresses(user);
  }
}
