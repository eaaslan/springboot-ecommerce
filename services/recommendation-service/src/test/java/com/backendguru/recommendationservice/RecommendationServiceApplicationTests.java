package com.backendguru.recommendationservice;

import com.backendguru.recommendationservice.client.ProductClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(
    properties = {
      "spring.cloud.config.enabled=false",
      "spring.config.import=",
      "eureka.client.enabled=false",
      "spring.cloud.discovery.enabled=false"
    })
class RecommendationServiceApplicationTests {

  @MockBean ProductClient productClient;

  @Test
  void contextLoads() {}
}
