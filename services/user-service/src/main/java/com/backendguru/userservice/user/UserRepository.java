package com.backendguru.userservice.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByEmail(String email);

  boolean existsByEmail(String email);

  @EntityGraph(attributePaths = "addresses")
  Optional<User> findWithAddressesById(Long id);
}
