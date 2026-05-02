package com.backendguru.notificationservice.notification;

import com.backendguru.common.event.OrderConfirmedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaNotificationConsumer {

  private final NotificationService service;

  @KafkaListener(
      topics = "${app.kafka.topics.order-confirmed:order.confirmed}",
      containerFactory = "orderConfirmedKafkaListenerContainerFactory")
  public void onOrderConfirmedFromKafka(OrderConfirmedEvent event) {
    log.debug(
        "Received OrderConfirmedEvent (Kafka): eventId={} orderId={}",
        event.eventId(),
        event.orderId());
    service.handleOrderConfirmed(event);
  }
}
