package com.backendguru.notificationservice.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "processed_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "eventId")
@ToString
public class ProcessedEvent {

  @Id
  @Column(name = "event_id", length = 36, nullable = false)
  private String eventId;

  @Column(name = "event_type", nullable = false, length = 80)
  private String eventType;

  @CreationTimestamp
  @Column(name = "processed_at", nullable = false, updatable = false)
  private OffsetDateTime processedAt;

  public ProcessedEvent(String eventId, String eventType) {
    this.eventId = eventId;
    this.eventType = eventType;
  }
}
