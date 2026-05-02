package com.backendguru.sellerservice.listing;

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
@Table(name = "listings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString(of = {"id", "productId", "sellerId", "priceAmount", "stockQuantity", "enabled"})
public class Listing {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "product_id", nullable = false)
  private Long productId;

  @Column(name = "seller_id", nullable = false)
  private Long sellerId;

  @Column(name = "price_amount", nullable = false, precision = 12, scale = 2)
  private BigDecimal priceAmount;

  @Column(name = "price_currency", nullable = false, length = 3)
  @Builder.Default
  private String priceCurrency = "TRY";

  @Column(name = "stock_quantity", nullable = false)
  @Builder.Default
  private int stockQuantity = 0;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @Builder.Default
  private ListingCondition condition = ListingCondition.NEW;

  @Column(name = "shipping_days", nullable = false)
  @Builder.Default
  private int shippingDays = 2;

  @Column(nullable = false)
  @Builder.Default
  private boolean enabled = true;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Version
  @Column(nullable = false)
  private long version;
}
