package com.backendguru.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ErrorCodeTest {

  @Test
  void resourceNotFoundMapsTo404() {
    assertThat(ErrorCode.RESOURCE_NOT_FOUND.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void duplicateResourceMapsTo409() {
    assertThat(ErrorCode.DUPLICATE_RESOURCE.getStatus()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void unauthorizedMapsTo401() {
    assertThat(ErrorCode.UNAUTHORIZED.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void forbiddenMapsTo403() {
    assertThat(ErrorCode.FORBIDDEN.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void validationFailedMapsTo400() {
    assertThat(ErrorCode.VALIDATION_FAILED.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void serviceUnavailableMapsTo503() {
    assertThat(ErrorCode.SERVICE_UNAVAILABLE.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }

  @Test
  void internalErrorMapsTo500() {
    assertThat(ErrorCode.INTERNAL_ERROR.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @Test
  void everyCodeHasNonBlankDefaultMessage() {
    for (ErrorCode code : ErrorCode.values()) {
      assertThat(code.getDefaultMessage()).isNotBlank();
    }
  }
}
