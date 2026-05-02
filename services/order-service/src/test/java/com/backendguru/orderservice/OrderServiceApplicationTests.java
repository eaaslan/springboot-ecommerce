package com.backendguru.orderservice;

import com.backendguru.orderservice.client.CartClient;
import com.backendguru.orderservice.client.InventoryClient;
import com.backendguru.orderservice.client.PaymentClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(
    properties = {
      "spring.cloud.config.enabled=false",
      "spring.config.import=",
      "eureka.client.enabled=false",
      "spring.cloud.discovery.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.flyway.enabled=false"
    })
class OrderServiceApplicationTests {

  @MockBean CartClient cartClient;
  @MockBean InventoryClient inventoryClient;
  @MockBean PaymentClient paymentClient;

  @Test
  void contextLoads() {}
}
