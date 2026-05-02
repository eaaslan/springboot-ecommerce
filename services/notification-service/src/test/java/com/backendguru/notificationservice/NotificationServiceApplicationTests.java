package com.backendguru.notificationservice;

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
      "spring.rabbitmq.listener.simple.auto-startup=false",
      "spring.rabbitmq.dynamic=false",
      "spring.kafka.listener.auto-startup=false",
      "spring.kafka.bootstrap-servers=localhost:9092",
      "spring.kafka.consumer.group-id=notification-service-test",
      "app.kafka.topics.order-confirmed=order.confirmed",
      "app.rabbit.exchange=test.exchange",
      "app.rabbit.dlx=test.dlx",
      "app.rabbit.queue=test.queue",
      "app.rabbit.dlq=test.dlq",
      "app.rabbit.routing-key=test.routing"
    })
class NotificationServiceApplicationTests {

  @Test
  void contextLoads() {}
}
