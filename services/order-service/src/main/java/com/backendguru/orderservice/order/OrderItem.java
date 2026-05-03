package com.backendguru.orderservice.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString(of = {"id", "productId", "quantity", "priceAmount"})
public class OrderItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "order_id", nullable = false)
  private Order order;

  @Column(name = "product_id", nullable = false)
  private Long productId;

  @Column(name = "product_name", nullable = false, length = 200)
  private String productName;

  @Column(name = "price_amount", nullable = false, precision = 12, scale = 2)
  private BigDecimal priceAmount;

  @Column(name = "price_currency", nullable = false, length = 3)
  private String priceCurrency;

  @Column(nullable = false)
  private int quantity;

  @Column(name = "reservation_id")
  private Long reservationId;

  // Marketplace V2 — nullable. When present, this line came from a specific seller offer.
  @Column(name = "listing_id")
  private Long listingId;

  @Column(name = "seller_id")
  private Long sellerId;

  @Column(name = "seller_name", length = 120)
  private String sellerName;

  /** FK → sub_orders.id; populated by SubOrderSplitter after the order is created. */
  @Column(name = "sub_order_id")
  private Long subOrderId;
}
