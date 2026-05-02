package com.backendguru.orderservice.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString(of = {"id", "eventId", "aggregateType", "aggregateId", "eventType", "status"})
public class OutboxEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "event_id", nullable = false, unique = true, length = 36)
  private String eventId;

  @Column(name = "aggregate_type", nullable = false, length = 80)
  private String aggregateType;

  @Column(name = "aggregate_id", nullable = false, length = 80)
  private String aggregateId;

  @Column(name = "event_type", nullable = false, length = 80)
  private String eventType;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String payload;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @Builder.Default
  private OutboxStatus status = OutboxStatus.PENDING;

  @Column(nullable = false)
  @Builder.Default
  private int attempts = 0;

  @Column(name = "last_error", length = 500)
  private String lastError;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "published_at")
  private OffsetDateTime publishedAt;
}
