package com.backendguru.userservice.auth.jwt;

import com.backendguru.userservice.user.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JwtService {

  private final JwtProperties props;

  private SecretKey key() {
    return Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
  }

  public String generateAccessToken(Long userId, String email, Role role) {
    Instant now = Instant.now();
    Instant exp = now.plusSeconds(props.accessTokenTtlMinutes() * 60);
    return Jwts.builder()
        .issuer(props.issuer())
        .subject(String.valueOf(userId))
        .claim("email", email)
        .claim("role", role.name())
        .issuedAt(Date.from(now))
        .expiration(Date.from(exp))
        .signWith(key())
        .compact();
  }

  public RefreshTokenDescriptor generateRefreshToken(Long userId) {
    Instant now = Instant.now();
    Instant exp = now.plusSeconds(props.refreshTokenTtlDays() * 24 * 60 * 60);
    String tokenId = UUID.randomUUID().toString();
    String token =
        Jwts.builder()
            .issuer(props.issuer())
            .subject(String.valueOf(userId))
            .id(tokenId)
            .issuedAt(Date.from(now))
            .expiration(Date.from(exp))
            .signWith(key())
            .compact();
    return new RefreshTokenDescriptor(token, tokenId, exp);
  }

  public Claims parse(String token) {
    return Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
  }

  public long accessTokenTtlSeconds() {
    return props.accessTokenTtlMinutes() * 60;
  }

  public long refreshTokenTtlSeconds() {
    return props.refreshTokenTtlDays() * 24 * 60 * 60;
  }

  public record RefreshTokenDescriptor(String token, String tokenId, Instant expiresAt) {}
}
