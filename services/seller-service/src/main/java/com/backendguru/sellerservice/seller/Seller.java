package com.backendguru.sellerservice.seller;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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

@Entity
@Table(name = "sellers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString(of = {"id", "userId", "businessName", "status"})
public class Seller {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false, unique = true)
  private Long userId;

  @Column(name = "business_name", nullable = false, length = 200)
  private String businessName;

  @Column(name = "tax_id", length = 40)
  private String taxId;

  @Column(length = 40)
  private String iban;

  @Column(name = "contact_email", length = 120)
  private String contactEmail;

  @Column(name = "contact_phone", length = 40)
  private String contactPhone;

  @Column(name = "commission_pct", nullable = false, precision = 5, scale = 2)
  @Builder.Default
  private BigDecimal commissionPct = new BigDecimal("8.00");

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @Builder.Default
  private SellerStatus status = SellerStatus.PENDING;

  @Column(precision = 3, scale = 2)
  private BigDecimal rating;

  @Column(name = "rating_count", nullable = false)
  @Builder.Default
  private int ratingCount = 0;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "approved_at")
  private OffsetDateTime approvedAt;

  @Version
  @Column(nullable = false)
  private long version;
}
