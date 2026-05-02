package com.backendguru.orderservice.outbox;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polls outbox_events for PENDING rows and publishes them to Kafka. Marks rows PUBLISHED on
 * success, FAILED + attempts++ on error. Single-instance polling for MVP — see spec for
 * SKIP-LOCKED / leader-election production options.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

  private static final int BATCH_SIZE = 50;
  private static final long SEND_TIMEOUT_SECONDS = 5;

  private final OutboxEventRepository repository;
  private final KafkaTemplate<String, String> kafkaTemplate;

  @Value("${app.kafka.topics.order-confirmed:order.confirmed}")
  private String orderConfirmedTopic;

  @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:1000}")
  @Transactional
  public void publishPending() {
    List<OutboxEvent> batch = repository.findTop50ByStatusOrderByIdAsc(OutboxStatus.PENDING);
    if (batch.isEmpty()) return;
    log.debug("Outbox relay processing {} PENDING rows", batch.size());
    for (OutboxEvent ev : batch) {
      publishOne(ev);
    }
  }

  void publishOne(OutboxEvent ev) {
    String topic = topicFor(ev);
    try {
      kafkaTemplate
          .send(topic, ev.getAggregateId(), ev.getPayload())
          .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      ev.setStatus(OutboxStatus.PUBLISHED);
      ev.setPublishedAt(OffsetDateTime.now());
      ev.setLastError(null);
      log.info(
          "Outbox event {} published to {} (eventType={})",
          ev.getEventId(),
          topic,
          ev.getEventType());
    } catch (Exception ex) {
      ev.setStatus(OutboxStatus.FAILED);
      ev.setAttempts(ev.getAttempts() + 1);
      ev.setLastError(truncate(ex.getMessage(), 500));
      log.error(
          "Outbox publish failed for event {} (attempts={}): {}",
          ev.getEventId(),
          ev.getAttempts(),
          ex.toString());
    }
  }

  private String topicFor(OutboxEvent ev) {
    if ("ORDER_CONFIRMED".equals(ev.getEventType())) return orderConfirmedTopic;
    throw new IllegalStateException("Unknown outbox eventType: " + ev.getEventType());
  }

  private static String truncate(String s, int max) {
    if (s == null) return null;
    return s.length() <= max ? s : s.substring(0, max);
  }
}
