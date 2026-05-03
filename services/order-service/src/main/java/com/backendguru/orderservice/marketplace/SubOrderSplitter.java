package com.backendguru.orderservice.marketplace;

import com.backendguru.orderservice.order.Order;
import com.backendguru.orderservice.order.OrderItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Splits an Order's line items into per-seller sub-orders. Items with no {@code sellerId} bucket
 * into a single "Platform" sub-order ({@code seller_id IS NULL}).
 *
 * <p>Commission math (V3.0): flat platform default of {@link #DEFAULT_COMMISSION_PCT} when the
 * sellerId is set; 0% for the platform bucket. V3.1+ will look up per-seller rates via an internal
 * seller-service endpoint.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubOrderSplitter {

  /** Default platform commission rate, applied when no seller-specific rate is available. */
  static final BigDecimal DEFAULT_COMMISSION_PCT = new BigDecimal("8.00");

  private final SubOrderRepository subOrderRepository;

  public List<SubOrder> split(Order order) {
    Map<Long, BigDecimal> bucketSubtotals = new LinkedHashMap<>();
    Map<Long, String> bucketNames = new HashMap<>();
    for (OrderItem it : order.getItems()) {
      // Use 0L as the key for null seller (Map can't key on null reliably).
      long key = it.getSellerId() == null ? 0L : it.getSellerId();
      BigDecimal line = it.getPriceAmount().multiply(BigDecimal.valueOf(it.getQuantity()));
      bucketSubtotals.merge(key, line, BigDecimal::add);
      if (key != 0L && bucketNames.get(key) == null && it.getSellerName() != null) {
        bucketNames.put(key, it.getSellerName());
      }
    }

    Map<Long, SubOrder> created = new HashMap<>();
    for (Map.Entry<Long, BigDecimal> e : bucketSubtotals.entrySet()) {
      Long sellerId = e.getKey() == 0L ? null : e.getKey();
      BigDecimal subtotal = e.getValue();
      BigDecimal pct = sellerId == null ? BigDecimal.ZERO : DEFAULT_COMMISSION_PCT;
      BigDecimal commission =
          subtotal.multiply(pct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
      BigDecimal payout = subtotal.subtract(commission);
      SubOrder sub =
          SubOrder.builder()
              .order(order)
              .sellerId(sellerId)
              .sellerName(sellerId == null ? null : bucketNames.get(e.getKey()))
              .subtotalAmount(subtotal)
              .commissionPct(pct)
              .commissionAmount(commission)
              .payoutAmount(payout)
              .currency(order.getCurrency())
              .status("PENDING")
              .build();
      sub = subOrderRepository.save(sub);
      created.put(e.getKey(), sub);
    }

    // Stamp sub_order_id back on each line.
    for (OrderItem it : order.getItems()) {
      long key = it.getSellerId() == null ? 0L : it.getSellerId();
      SubOrder sub = created.get(key);
      if (sub != null) it.setSubOrderId(sub.getId());
    }

    log.info(
        "Order {} split into {} sub-order(s): {}", order.getId(), created.size(), created.values());
    return List.copyOf(created.values());
  }
}
