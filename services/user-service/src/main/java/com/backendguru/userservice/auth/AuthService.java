package com.backendguru.userservice.auth;

import com.backendguru.common.error.DuplicateResourceException;
import com.backendguru.common.error.UnauthorizedException;
import com.backendguru.userservice.auth.dto.LoginRequest;
import com.backendguru.userservice.auth.dto.RefreshRequest;
import com.backendguru.userservice.auth.dto.RegisterRequest;
import com.backendguru.userservice.auth.dto.TokenResponse;
import com.backendguru.userservice.auth.jwt.JwtService;
import com.backendguru.userservice.auth.refresh.RefreshToken;
import com.backendguru.userservice.auth.refresh.RefreshTokenRepository;
import com.backendguru.userservice.user.Role;
import com.backendguru.userservice.user.User;
import com.backendguru.userservice.user.UserRepository;
import io.jsonwebtoken.Claims;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  @Transactional
  public Long register(RegisterRequest req) {
    if (userRepository.existsByEmail(req.email())) {
      throw new DuplicateResourceException("Email already in use: " + req.email());
    }
    User user =
        User.builder()
            .email(req.email())
            .passwordHash(passwordEncoder.encode(req.password()))
            .role(Role.USER)
            .enabled(true)
            .build();
    return userRepository.save(user).getId();
  }

  @Transactional
  public TokenResponse login(LoginRequest req) {
    User user =
        userRepository
            .findByEmail(req.email())
            .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
    if (!user.isEnabled()) {
      throw new UnauthorizedException("Account disabled");
    }
    if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
      throw new UnauthorizedException("Invalid credentials");
    }
    return issueTokens(user);
  }

  @Transactional
  public TokenResponse refresh(RefreshRequest req) {
    Claims claims;
    try {
      claims = jwtService.parse(req.refreshToken());
    } catch (Exception e) {
      throw new UnauthorizedException("Invalid refresh token");
    }
    String hash = sha256(req.refreshToken());
    RefreshToken stored =
        refreshRepository
            .findByTokenHash(hash)
            .orElseThrow(() -> new UnauthorizedException("Refresh token not found (replay?)"));
    if (stored.getRevokedAt() != null) {
      throw new UnauthorizedException("Refresh token revoked");
    }
    stored.setRevokedAt(OffsetDateTime.now(ZoneOffset.UTC));
    refreshRepository.save(stored);
    User user =
        userRepository
            .findById(Long.valueOf(claims.getSubject()))
            .orElseThrow(() -> new UnauthorizedException("User no longer exists"));
    return issueTokens(user);
  }

  @Transactional
  public void logout(Long userId) {
    refreshRepository.deleteByUserId(userId);
  }

  private TokenResponse issueTokens(User user) {
    String access = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
    var refresh = jwtService.generateRefreshToken(user.getId());
    RefreshToken row =
        RefreshToken.builder()
            .userId(user.getId())
            .tokenHash(sha256(refresh.token()))
            .expiresAt(OffsetDateTime.ofInstant(refresh.expiresAt(), ZoneOffset.UTC))
            .build();
    refreshRepository.save(row);
    return new TokenResponse(
        access,
        refresh.token(),
        jwtService.accessTokenTtlSeconds(),
        jwtService.refreshTokenTtlSeconds());
  }

  private static String sha256(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
