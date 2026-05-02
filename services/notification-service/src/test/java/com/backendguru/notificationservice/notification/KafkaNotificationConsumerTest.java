package com.backendguru.notificationservice.notification;

import static org.mockito.Mockito.verify;

import com.backendguru.common.event.OrderConfirmedEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KafkaNotificationConsumerTest {

  @Mock NotificationService service;
  @InjectMocks KafkaNotificationConsumer consumer;

  @Test
  void delegatesToHandleOrderConfirmed() {
    OrderConfirmedEvent event =
        new OrderConfirmedEvent(
            UUID.randomUUID().toString(),
            10L,
            5L,
            new BigDecimal("19.99"),
            "TRY",
            Instant.parse("2026-05-02T10:00:00Z"));

    consumer.onOrderConfirmedFromKafka(event);

    verify(service).handleOrderConfirmed(event);
  }
}
