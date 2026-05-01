package com.backendguru.cartservice;

import com.backendguru.cartservice.product.ProductClient;
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
class CartServiceApplicationTests {

  @MockBean ProductClient productClient;

  @Test
  void contextLoads() {}
}
