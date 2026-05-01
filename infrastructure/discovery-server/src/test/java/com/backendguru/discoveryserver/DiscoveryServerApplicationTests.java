package com.backendguru.discoveryserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(
    properties = {
      "spring.cloud.config.enabled=false",
      "spring.config.import=",
      "eureka.client.register-with-eureka=false",
      "eureka.client.fetch-registry=false"
    })
class DiscoveryServerApplicationTests {

  @Test
  void contextLoads() {}
}
