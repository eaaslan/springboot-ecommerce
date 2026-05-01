package com.backendguru.userservice.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "spring.cloud.config.enabled=false",
      "spring.config.import=",
      "eureka.client.enabled=false",
      "app.jwt.secret=test-secret-256-bit-key-for-integration-tests-only-do-not-use-elsewhere",
      "app.jwt.access-token-ttl-minutes=15",
      "app.jwt.refresh-token-ttl-days=7",
      "app.jwt.issuer=test"
    })
class AuthFlowIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;

  @Test
  void registerThenLoginThenAccessProtectedEndpoint() throws Exception {
    var register = Map.of("email", "alice@example.com", "password", "password123");
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(register)))
        .andExpect(status().isCreated());

    var login = Map.of("email", "alice@example.com", "password", "password123");
    var loginResp =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(login)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String accessToken = JsonPath.read(loginResp, "$.data.accessToken");

    mockMvc
        .perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.email").value("alice@example.com"));
  }

  @Test
  void rejectsWithoutBearer() throws Exception {
    mockMvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void duplicateRegistrationReturnsConflict() throws Exception {
    var body = Map.of("email", "dup@example.com", "password", "password123");
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isConflict());
  }

  @Test
  void invalidPasswordReturnsUnauthorized() throws Exception {
    var register = Map.of("email", "wrongpw@example.com", "password", "password123");
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(register)))
        .andExpect(status().isCreated());
    var login = Map.of("email", "wrongpw@example.com", "password", "wrong-password");
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void refreshRotatesAndRevokesOldToken() throws Exception {
    var register = Map.of("email", "refresh@example.com", "password", "password123");
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(register)))
        .andExpect(status().isCreated());

    var loginResp =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(register)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String oldRefresh = JsonPath.read(loginResp, "$.data.refreshToken");

    var refresh1 =
        mockMvc
            .perform(
                post("/api/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("refreshToken", oldRefresh))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    mockMvc
        .perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", oldRefresh))))
        .andExpect(status().isUnauthorized());

    String newRefresh = JsonPath.read(refresh1, "$.data.refreshToken");
    mockMvc
        .perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", newRefresh))))
        .andExpect(status().isOk());
  }
}
