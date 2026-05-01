package com.backendguru.common.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.backendguru.common.error.ErrorCode;
import com.backendguru.common.error.ResourceNotFoundException;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ErrorResponseTest {

  @Test
  void fromExceptionPopulatesCodeStatusMessage() {
    var ex = new ResourceNotFoundException("Product 42 not found");
    var response = ErrorResponse.from(ex, "/api/products/42", "trace-abc");
    assertThat(response.code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.name());
    assertThat(response.status()).isEqualTo(404);
    assertThat(response.message()).isEqualTo("Product 42 not found");
    assertThat(response.path()).isEqualTo("/api/products/42");
    assertThat(response.traceId()).isEqualTo("trace-abc");
    assertThat(response.timestamp()).isBeforeOrEqualTo(Instant.now());
  }

  @Test
  void builderProducesAllFields() {
    var response =
        ErrorResponse.builder()
            .code("VALIDATION_FAILED")
            .message("name must not be blank")
            .status(400)
            .path("/api/users")
            .traceId("trace-xyz")
            .timestamp(Instant.parse("2026-04-26T10:00:00Z"))
            .details(Map.of("field", "name"))
            .build();
    assertThat(response.code()).isEqualTo("VALIDATION_FAILED");
    assertThat(response.details()).containsEntry("field", "name");
  }
}
