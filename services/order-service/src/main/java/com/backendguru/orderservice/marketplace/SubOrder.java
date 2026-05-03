package com.backendguru.orderservice.marketplace;

import com.backendguru.orderservice.order.Order;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * One slice of an order belonging to a single seller. {@code sellerId} may be null for legacy /
 * platform-owned items (the "Platform" sub-order).
 */
@Entity
@Table(name = "sub_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString(of = {"id", "orderId", "sellerId", "subtotalAmount", "status"})
public class SubOrder {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "order_id", nullable = false)
  private Order order;

  @Column(name = "order_id", insertable = false, updatable = false)
  private Long orderId;

  @Column(name = "seller_id")
  private Long sellerId;

  @Column(name = "seller_name", length = 120)
  private String sellerName;

  @Column(name = "subtotal_amount", nullable = false, precision = 12, scale = 2)
  private BigDecimal subtotalAmount;

  @Column(name = "commission_pct", nullable = false, precision = 5, scale = 2)
  @Builder.Default
  private BigDecimal commissionPct = BigDecimal.ZERO;

  @Column(name = "commission_amount", nullable = false, precision = 12, scale = 2)
  @Builder.Default
  private BigDecimal commissionAmount = BigDecimal.ZERO;

  @Column(name = "payout_amount", nullable = false, precision = 12, scale = 2)
  @Builder.Default
  private BigDecimal payoutAmount = BigDecimal.ZERO;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(nullable = false, length = 20)
  @Builder.Default
  private String status = "PENDING";

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  /** FK → seller_payouts.id once a payout run includes this sub-order. */
  @Column(name = "payout_id")
  private Long payoutId;
}
