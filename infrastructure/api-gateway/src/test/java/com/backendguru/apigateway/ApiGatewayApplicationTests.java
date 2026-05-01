package com.backendguru.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(
    properties = {
      "spring.cloud.config.enabled=false",
      "spring.config.import=",
      "eureka.client.enabled=false",
      "spring.cloud.discovery.enabled=false",
      "spring.cloud.gateway.discovery.locator.enabled=false",
      "app.jwt.secret=test-secret-256-bit-key-for-gateway-smoke-test-only-do-not-use-elsewhere"
    })
class ApiGatewayApplicationTests {

  @Test
  void contextLoads() {}
}
