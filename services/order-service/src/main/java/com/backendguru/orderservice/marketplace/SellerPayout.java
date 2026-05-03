package com.backendguru.orderservice.marketplace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
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
@Table(name = "seller_payouts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString(of = {"id", "sellerId", "periodStart", "periodEnd", "netAmount", "status"})
public class SellerPayout {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "seller_id", nullable = false)
  private Long sellerId;

  @Column(name = "period_start", nullable = false)
  private LocalDate periodStart;

  @Column(name = "period_end", nullable = false)
  private LocalDate periodEnd;

  @Column(name = "gross_amount", nullable = false, precision = 14, scale = 2)
  private BigDecimal grossAmount;

  @Column(name = "commission_amount", nullable = false, precision = 14, scale = 2)
  private BigDecimal commissionAmount;

  @Column(name = "net_amount", nullable = false, precision = 14, scale = 2)
  private BigDecimal netAmount;

  @Column(name = "sub_order_count", nullable = false)
  private int subOrderCount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(nullable = false, length = 20)
  @Builder.Default
  private String status = "SCHEDULED";

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "paid_at")
  private OffsetDateTime paidAt;
}
