package com.backendguru.userservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(
    properties = {
      "spring.cloud.config.enabled=false",
      "spring.config.import=",
      "eureka.client.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.flyway.enabled=false",
      "app.jwt.secret=test-secret-256-bit-key-for-smoke-test-only-please-do-not-use-prod-anywhere",
      "app.jwt.access-token-ttl-minutes=15",
      "app.jwt.refresh-token-ttl-days=7",
      "app.jwt.issuer=test"
    })
class UserServiceApplicationTests {

  @Test
  void contextLoads() {}
}
