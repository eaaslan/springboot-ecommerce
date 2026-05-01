package com.backendguru.userservice.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.backendguru.userservice.auth.jwt.JwtProperties;
import com.backendguru.userservice.auth.jwt.JwtService;
import com.backendguru.userservice.user.Role;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  private final JwtProperties props =
      new JwtProperties(
          "dev-only-256-bit-key-please-change-this-test-key-with-enough-bytes-ok", 15, 7, "test");
  private final JwtService service = new JwtService(props);

  @Test
  void accessTokenContainsExpectedClaims() {
    String token = service.generateAccessToken(42L, "a@b.c", Role.USER);
    var claims = service.parse(token);
    assertThat(claims.getSubject()).isEqualTo("42");
    assertThat(claims.get("email")).isEqualTo("a@b.c");
    assertThat(claims.get("role")).isEqualTo("USER");
    assertThat(claims.getIssuer()).isEqualTo("test");
  }

  @Test
  void refreshTokenHasIdClaim() {
    var desc = service.generateRefreshToken(99L);
    var claims = service.parse(desc.token());
    assertThat(claims.getId()).isEqualTo(desc.tokenId());
    assertThat(claims.getSubject()).isEqualTo("99");
  }

  @Test
  void parseRejectsTamperedToken() {
    String token = service.generateAccessToken(1L, "x@y.z", Role.USER);
    String tampered = token.substring(0, token.length() - 4) + "abcd";
    assertThatThrownBy(() -> service.parse(tampered)).isInstanceOf(JwtException.class);
  }
}
