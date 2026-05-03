package com.backendguru.orderservice.marketplace;

import com.backendguru.common.error.ForbiddenException;
import com.backendguru.common.error.ResourceNotFoundException;
import com.backendguru.common.error.ValidationException;
import com.backendguru.orderservice.order.Order;
import com.backendguru.orderservice.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V4.0 returns flow — status-only state machine on sub_orders.status.
 *
 * <pre>
 *   PENDING ──[customer requests]──▶ RETURN_REQUESTED
 *   RETURN_REQUESTED ──[seller approves]──▶ REFUNDED
 *   RETURN_REQUESTED ──[seller rejects]──▶ RETURN_REJECTED
 * </pre>
 *
 * <p>Once a sub-order is locked into a payout (payoutId != null), returns are blocked — the money
 * has already been earmarked. V5 will introduce reverse-payout logic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReturnsService {

  private final SubOrderRepository subOrderRepository;
  private final OrderRepository orderRepository;
  private final SellerProfileClient sellerProfileClient;

  @Transactional
  public SubOrder requestReturn(Long subOrderId, Long requestingUserId) {
    SubOrder sub = loadOrThrow(subOrderId);
    Order order =
        orderRepository
            .findById(sub.getOrder().getId())
            .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    if (!order.getUserId().equals(requestingUserId)) {
      throw new ForbiddenException("You can only request a return on your own order");
    }
    if (sub.getPayoutId() != null) {
      throw new ValidationException("Sub-order already paid out — return must be handled manually");
    }
    if (!"PENDING".equals(sub.getStatus())) {
      throw new ValidationException(
          "Sub-order is " + sub.getStatus() + " — return cannot be requested in this state");
    }
    sub.setStatus("RETURN_REQUESTED");
    log.info("Sub-order {} return requested by user {}", subOrderId, requestingUserId);
    return sub;
  }

  /** Seller approves or rejects a pending return. */
  @Transactional
  public SubOrder decide(Long subOrderId, boolean approve, Long requestingUserId) {
    SubOrder sub = loadOrThrow(subOrderId);
    Long sellerId = currentSellerId(requestingUserId);
    if (sub.getSellerId() == null || !sub.getSellerId().equals(sellerId)) {
      throw new ForbiddenException("Sub-order does not belong to your seller account");
    }
    if (!"RETURN_REQUESTED".equals(sub.getStatus())) {
      throw new ValidationException(
          "Sub-order is " + sub.getStatus() + " — only RETURN_REQUESTED can be decided");
    }
    sub.setStatus(approve ? "REFUNDED" : "RETURN_REJECTED");
    log.info(
        "Sub-order {} return {} by seller {}",
        subOrderId,
        approve ? "APPROVED → REFUNDED" : "REJECTED",
        sellerId);
    return sub;
  }

  private SubOrder loadOrThrow(Long id) {
    return subOrderRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Sub-order " + id + " not found"));
  }

  private Long currentSellerId(Long userId) {
    var resp = sellerProfileClient.me();
    var profile = resp == null ? null : resp.data();
    if (profile == null) {
      throw new ForbiddenException("Seller record required");
    }
    if (!"ACTIVE".equalsIgnoreCase(profile.status())) {
      throw new ForbiddenException("Seller account is not active");
    }
    return profile.id();
  }
}
