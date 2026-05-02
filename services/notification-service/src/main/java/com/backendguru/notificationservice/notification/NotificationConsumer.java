package com.backendguru.notificationservice.notification;

import com.backendguru.common.event.OrderConfirmedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {

  private final NotificationService service;

  @RabbitListener(queues = "${app.rabbit.queue}")
  public void onOrderConfirmed(OrderConfirmedEvent event) {
    log.debug("Received OrderConfirmedEvent: eventId={} orderId={}", event.eventId(), event.orderId());
    service.handleOrderConfirmed(event);
  }
}
