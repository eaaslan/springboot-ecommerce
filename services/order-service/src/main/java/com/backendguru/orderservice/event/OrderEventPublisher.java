package com.backendguru.orderservice.event;

import com.backendguru.common.event.OrderConfirmedEvent;
import com.backendguru.orderservice.order.Order;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Best-effort publisher: failures are logged but do NOT abort the order. The order has already
 * been CONFIRMED in the local DB; if RabbitMQ is unavailable we accept eventual loss for now. Phase
 * 7 will replace this with the Outbox pattern (event row written in same TX as order, separate
 * relay process publishes).
 */
@Component
@Slf4j
public class OrderEventPublisher {

  private final RabbitTemplate rabbit;
  private final String exchange;
  private final String routingKey;

  public OrderEventPublisher(
      RabbitTemplate rabbit,
      @Value("${app.rabbit.exchange:order.events.exchange}") String exchange,
      @Value("${app.rabbit.routing-key.order-confirmed:order.confirmed}") String routingKey) {
    this.rabbit = rabbit;
    this.exchange = exchange;
    this.routingKey = routingKey;
  }

  public void publishOrderConfirmed(Order order) {
    OrderConfirmedEvent event =
        new OrderConfirmedEvent(
            UUID.randomUUID().toString(),
            order.getId(),
            order.getUserId(),
            order.getTotalAmount(),
            order.getCurrency(),
            Instant.now());
    try {
      rabbit.convertAndSend(exchange, routingKey, event);
      log.info(
          "Published OrderConfirmedEvent eventId={} orderId={}", event.eventId(), order.getId());
    } catch (AmqpException ex) {
      log.error(
          "Failed to publish OrderConfirmedEvent for order {} — message will be lost (Outbox in Phase 7)",
          order.getId(),
          ex);
    }
  }
}
