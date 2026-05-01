package com.backendguru.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class BusinessExceptionTest {

  @Test
  void resourceNotFoundCarriesItsCodeAndStatus() {
    var ex = new ResourceNotFoundException("Product 42 not found");
    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    assertThat(ex.getErrorCode().getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(ex.getMessage()).isEqualTo("Product 42 not found");
  }

  @Test
  void duplicateResourceCarriesConflictStatus() {
    var ex = new DuplicateResourceException("Email already in use");
    assertThat(ex.getErrorCode().getStatus()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void unauthorizedCarries401() {
    assertThat(new UnauthorizedException("x").getErrorCode().getStatus())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void forbiddenCarries403() {
    assertThat(new ForbiddenException("x").getErrorCode().getStatus())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void validationCarries400() {
    assertThat(new ValidationException("x").getErrorCode().getStatus())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void preservesCause() {
    var cause = new IllegalStateException("boom");
    var ex = new ResourceNotFoundException("not found", cause);
    assertThat(ex.getCause()).isSameAs(cause);
  }

  @Test
  void allConcreteExceptionsExtendBusinessException() {
    assertThat(new ResourceNotFoundException("x")).isInstanceOf(BusinessException.class);
    assertThat(new DuplicateResourceException("x")).isInstanceOf(BusinessException.class);
    assertThat(new UnauthorizedException("x")).isInstanceOf(BusinessException.class);
    assertThat(new ForbiddenException("x")).isInstanceOf(BusinessException.class);
    assertThat(new ValidationException("x")).isInstanceOf(BusinessException.class);
  }
}
