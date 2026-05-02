package com.backendguru.paymentservice;

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
      "spring.flyway.enabled=false"
    })
class PaymentServiceApplicationTests {

  @Test
  void contextLoads() {}
}
