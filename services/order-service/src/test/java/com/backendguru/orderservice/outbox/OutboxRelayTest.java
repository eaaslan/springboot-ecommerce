package com.backendguru.orderservice.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

  @Mock OutboxEventRepository repository;

  @Mock
  @SuppressWarnings("rawtypes")
  KafkaTemplate kafkaTemplate;

  @Spy MeterRegistry meterRegistry = new SimpleMeterRegistry();

  @InjectMocks OutboxRelay relay;

  private OutboxEvent pendingRow() {
    return OutboxEvent.builder()
        .id(1L)
        .eventId("evt-123")
        .aggregateType("ORDER")
        .aggregateId("100")
        .eventType("ORDER_CONFIRMED")
        .payload("{\"orderId\":100}")
        .status(OutboxStatus.PENDING)
        .attempts(0)
        .build();
  }

  @Test
  @SuppressWarnings("unchecked")
  void successMarksRowPublished() throws Exception {
    ReflectionTestUtils.setField(relay, "orderConfirmedTopic", "order.confirmed");
    OutboxEvent row = pendingRow();
    var record = new ProducerRecord<String, String>("order.confirmed", "100", row.getPayload());
    var meta = new RecordMetadata(new TopicPartition("order.confirmed", 0), 0, 0, 0L, 0, 0);
    var sendResult = new SendResult<>(record, meta);
    when(kafkaTemplate.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(sendResult));

    relay.publishOne(row);

    assertThat(row.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    assertThat(row.getPublishedAt()).isNotNull();
    assertThat(row.getLastError()).isNull();
  }

  @Test
  @SuppressWarnings("unchecked")
  void kafkaSendFailureMarksRowFailedAndIncrementsAttempts() {
    ReflectionTestUtils.setField(relay, "orderConfirmedTopic", "order.confirmed");
    OutboxEvent row = pendingRow();
    var failed = new CompletableFuture<SendResult<String, String>>();
    failed.completeExceptionally(new ExecutionException(new RuntimeException("broker down")));
    when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failed);

    relay.publishOne(row);

    assertThat(row.getStatus()).isEqualTo(OutboxStatus.FAILED);
    assertThat(row.getAttempts()).isEqualTo(1);
    assertThat(row.getLastError()).contains("broker down");
    assertThat(row.getPublishedAt()).isNull();
  }
}
