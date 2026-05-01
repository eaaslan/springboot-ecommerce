package com.backendguru.userservice.auth.refresh;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  @Modifying
  @Transactional
  @Query("delete from RefreshToken r where r.userId = :userId")
  void deleteByUserId(Long userId);
}
