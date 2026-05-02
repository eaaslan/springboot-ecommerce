package com.backendguru.orderservice.outbox;

import com.backendguru.common.event.OrderConfirmedEvent;
import com.backendguru.orderservice.order.Order;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Helper that produces an outbox row representing an OrderConfirmedEvent. Designed to be called
 * from inside the saga's existing @Transactional boundary so the row is committed atomically with
 * the order status update.
 */
@Component
@RequiredArgsConstructor
public class OutboxAppender {

  private static final String AGGREGATE_TYPE = "ORDER";
  private static final String EVENT_TYPE = "ORDER_CONFIRMED";

  private final ObjectMapper objectMapper;

  public OutboxEvent buildOrderConfirmed(Order order) {
    String eventId = UUID.randomUUID().toString();
    OrderConfirmedEvent event =
        new OrderConfirmedEvent(
            eventId,
            order.getId(),
            order.getUserId(),
            order.getTotalAmount(),
            order.getCurrency(),
            Instant.now());
    String payload;
    try {
      payload = objectMapper.writeValueAsString(event);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException(
          "Failed to serialize OrderConfirmedEvent for order " + order.getId(), ex);
    }
    return OutboxEvent.builder()
        .eventId(eventId)
        .aggregateType(AGGREGATE_TYPE)
        .aggregateId(String.valueOf(order.getId()))
        .eventType(EVENT_TYPE)
        .payload(payload)
        .status(OutboxStatus.PENDING)
        .build();
  }
}
