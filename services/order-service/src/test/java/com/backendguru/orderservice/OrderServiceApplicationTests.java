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
      "spring.flyway.enabled=false",
      "spring.rabbitmq.listener.simple.auto-startup=false",
      "spring.kafka.bootstrap-servers=localhost:9092",
      "app.kafka.topics.order-confirmed=order.confirmed"
    })
class OrderServiceApplicationTests {

  @MockBean CartClient cartClient;
  @MockBean InventoryClient inventoryClient;
  @MockBean PaymentClient paymentClient;
  @MockBean org.springframework.amqp.rabbit.connection.ConnectionFactory amqpConnectionFactory;
  @MockBean org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate;
  @MockBean org.springframework.kafka.core.ProducerFactory<String, String> producerFactory;

  @Test
  void contextLoads() {}
}
