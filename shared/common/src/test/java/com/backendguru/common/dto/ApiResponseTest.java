package com.backendguru.common.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ApiResponseTest {

  @Test
  void successWrapsPayloadAndMarksOk() {
    var response = ApiResponse.success("hello");
    assertThat(response.success()).isTrue();
    assertThat(response.data()).isEqualTo("hello");
    assertThat(response.error()).isNull();
    assertThat(response.timestamp()).isBeforeOrEqualTo(Instant.now());
  }

  @Test
  void successWithNullPayloadStillMarksOk() {
    var response = ApiResponse.success(null);
    assertThat(response.success()).isTrue();
    assertThat(response.data()).isNull();
  }

  @Test
  void failureCarriesErrorAndNullData() {
    var error = ErrorResponse.builder().code("RNF").message("not found").status(404).build();
    var response = ApiResponse.failure(error);
    assertThat(response.success()).isFalse();
    assertThat(response.data()).isNull();
    assertThat(response.error()).isEqualTo(error);
  }
}
