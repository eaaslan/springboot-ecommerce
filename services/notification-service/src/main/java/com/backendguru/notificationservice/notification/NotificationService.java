package com.backendguru.notificationservice.notification;

import com.backendguru.common.event.OrderConfirmedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class NotificationService {

  private static final String EVENT_TYPE = "ORDER_CONFIRMED";

  private final NotificationRepository notificationRepo;
  private final ProcessedEventRepository processedRepo;
  private final MeterRegistry meterRegistry;
  private final SlackNotifier slackNotifier;

  public NotificationService(
      NotificationRepository notificationRepo,
      ProcessedEventRepository processedRepo,
      MeterRegistry meterRegistry,
      SlackNotifier slackNotifier) {
    this.notificationRepo = notificationRepo;
    this.processedRepo = processedRepo;
    this.meterRegistry = meterRegistry;
    this.slackNotifier = slackNotifier;
  }

  /**
   * Idempotent handler. Returns true if work was performed; false if event was a duplicate.
   *
   * <p>The {@code processed_events} insert and the {@code notifications} insert run in the same
   * transaction. A second concurrent delivery races on the {@code event_id} primary key — the loser
   * sees {@link DataIntegrityViolationException}, which we treat as "already handled" and ack-noop.
   */
  @Transactional
  public boolean handleOrderConfirmed(OrderConfirmedEvent event) {
    if (processedRepo.existsById(event.eventId())) {
      log.info("Skipping duplicate event {} (orderId={})", event.eventId(), event.orderId());
      meterRegistry.counter("notifications.duplicate").increment();
      return false;
    }

    try {
      processedRepo.save(new ProcessedEvent(event.eventId(), EVENT_TYPE));
    } catch (DataIntegrityViolationException dup) {
      log.info("Race-condition duplicate detected for event {}", event.eventId());
      meterRegistry.counter("notifications.duplicate").increment();
      return false;
    }

    String payload = renderEmailPayload(event);
    Notification n =
        Notification.builder()
            .eventId(event.eventId())
            .orderId(event.orderId())
            .userId(event.userId())
            .channel(NotificationChannel.EMAIL)
            .status(NotificationStatus.SENT)
            .payload(payload)
            .build();
    notificationRepo.save(n);
    Counter.builder("notifications.sent")
        .tag("channel", "EMAIL")
        .register(meterRegistry)
        .increment();
    log.info(
        "Notification SENT — eventId={} orderId={} userId={} channel=EMAIL",
        event.eventId(),
        event.orderId(),
        event.userId());
    slackNotifier.notifyOrderConfirmed(event);
    return true;
  }

  private String renderEmailPayload(OrderConfirmedEvent event) {
    return """
           Subject: Order #%d confirmed
           Hi user %d, your order totalling %s %s has been confirmed (at %s).
           """
        .formatted(
            event.orderId(),
            event.userId(),
            event.totalAmount(),
            event.currency(),
            event.occurredAt());
  }
}
