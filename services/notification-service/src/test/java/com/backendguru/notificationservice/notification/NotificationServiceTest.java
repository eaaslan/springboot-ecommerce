package com.backendguru.notificationservice.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.backendguru.common.event.OrderConfirmedEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

  @Mock NotificationRepository notificationRepo;
  @Mock ProcessedEventRepository processedRepo;

  @org.mockito.Spy
  io.micrometer.core.instrument.MeterRegistry meterRegistry =
      new io.micrometer.core.instrument.simple.SimpleMeterRegistry();

  @InjectMocks NotificationService service;

  private OrderConfirmedEvent event() {
    return new OrderConfirmedEvent(
        UUID.randomUUID().toString(),
        101L,
        42L,
        new BigDecimal("99.50"),
        "TRY",
        Instant.parse("2026-05-02T10:00:00Z"));
  }

  @Test
  void freshEventPersistsProcessedEventAndNotification() {
    OrderConfirmedEvent e = event();
    when(processedRepo.existsById(e.eventId())).thenReturn(false);

    boolean handled = service.handleOrderConfirmed(e);

    assertThat(handled).isTrue();
    verify(processedRepo, times(1)).save(any(ProcessedEvent.class));
    ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepo).save(cap.capture());
    Notification saved = cap.getValue();
    assertThat(saved.getEventId()).isEqualTo(e.eventId());
    assertThat(saved.getOrderId()).isEqualTo(101L);
    assertThat(saved.getChannel()).isEqualTo(NotificationChannel.EMAIL);
    assertThat(saved.getStatus()).isEqualTo(NotificationStatus.SENT);
    assertThat(saved.getPayload()).contains("Order #101", "99.50", "TRY");
  }

  @Test
  void duplicateEventByExistsIsSkipped() {
    OrderConfirmedEvent e = event();
    when(processedRepo.existsById(e.eventId())).thenReturn(true);

    boolean handled = service.handleOrderConfirmed(e);

    assertThat(handled).isFalse();
    verify(processedRepo, never()).save(any());
    verify(notificationRepo, never()).save(any());
  }

  @Test
  void raceConditionDuplicateInsertIsSwallowedAsAlreadyHandled() {
    OrderConfirmedEvent e = event();
    when(processedRepo.existsById(e.eventId())).thenReturn(false);
    when(processedRepo.save(any(ProcessedEvent.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique"));

    boolean handled = service.handleOrderConfirmed(e);

    assertThat(handled).isFalse();
    verify(notificationRepo, never()).save(any());
  }
}
