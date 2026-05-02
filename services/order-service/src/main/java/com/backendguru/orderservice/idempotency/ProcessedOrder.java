package com.backendguru.orderservice.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "processed_orders")
@IdClass(ProcessedOrder.PK.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedOrder {

  @Id
  @Column(name = "idempotency_key", length = 80, nullable = false)
  private String idempotencyKey;

  @Id
  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "order_id", nullable = false)
  private Long orderId;

  @Column(name = "response_body", columnDefinition = "TEXT", nullable = false)
  private String responseBody;

  @Column(name = "response_status", nullable = false)
  private int responseStatus;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @EqualsAndHashCode
  @NoArgsConstructor
  @AllArgsConstructor
  public static class PK implements Serializable {
    private String idempotencyKey;
    private Long userId;
  }
}
