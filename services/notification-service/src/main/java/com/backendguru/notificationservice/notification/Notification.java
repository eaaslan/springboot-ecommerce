package com.backendguru.notificationservice.notification;

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
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString(of = {"id", "eventId", "orderId", "channel", "status"})
public class Notification {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "event_id", nullable = false, length = 36)
  private String eventId;

  @Column(name = "order_id", nullable = false)
  private Long orderId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private NotificationChannel channel;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private NotificationStatus status;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String payload;

  @Column(name = "failure_reason", length = 500)
  private String failureReason;

  @CreationTimestamp
  @Column(name = "sent_at", nullable = false, updatable = false)
  private OffsetDateTime sentAt;
}
