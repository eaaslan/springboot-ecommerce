package com.backendguru.userservice.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
    properties = {
      "spring.cloud.config.enabled=false",
      "spring.config.import=",
      "eureka.client.enabled=false",
      "app.jwt.secret=test-secret-256-bit-key-for-repository-test-only-do-not-use-elsewhere",
      "app.jwt.access-token-ttl-minutes=15",
      "app.jwt.refresh-token-ttl-days=7",
      "app.jwt.issuer=test"
    })
class UserRepositoryTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired UserRepository repository;

  @Test
  void persistsAndLoadsUser() {
    User user =
        User.builder()
            .email("alice@example.com")
            .passwordHash("hash")
            .role(Role.USER)
            .enabled(true)
            .build();
    User saved = repository.save(user);

    assertThat(saved.getId()).isNotNull();
    assertThat(repository.findByEmail("alice@example.com")).isPresent();
    assertThat(repository.existsByEmail("alice@example.com")).isTrue();
    assertThat(repository.existsByEmail("bob@example.com")).isFalse();
  }

  @Test
  void findWithAddressesByIdLoadsAddressesEagerlyInOneQuery() {
    User user =
        User.builder()
            .email("with-addr@example.com")
            .passwordHash("h")
            .role(Role.USER)
            .enabled(true)
            .build();
    user.addAddress(
        Address.builder().line1("L1").city("Istanbul").country("TR").isDefault(true).build());
    user.addAddress(
        Address.builder().line1("L2").city("Ankara").country("TR").isDefault(false).build());
    repository.save(user);

    var loaded = repository.findWithAddressesById(user.getId()).orElseThrow();
    assertThat(loaded.getAddresses()).hasSize(2);
  }
}
