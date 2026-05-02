package com.backendguru.orderservice.order;

import com.backendguru.common.error.ResourceNotFoundException;
import com.backendguru.common.error.ValidationException;
import com.backendguru.orderservice.client.CartClient;
import com.backendguru.orderservice.client.InventoryClient;
import com.backendguru.orderservice.client.PaymentClient;
import com.backendguru.orderservice.client.dto.CartSnapshot;
import com.backendguru.orderservice.client.dto.ChargeRequest;
import com.backendguru.orderservice.client.dto.PaymentSnapshot;
import com.backendguru.orderservice.client.dto.ReservationSnapshot;
import com.backendguru.orderservice.event.OrderEventPublisher;
import com.backendguru.orderservice.exception.InventoryUnavailableException;
import com.backendguru.orderservice.exception.PaymentFailedException;
import com.backendguru.orderservice.exception.SagaException;
import com.backendguru.orderservice.order.dto.OrderResponse;
import com.backendguru.orderservice.order.dto.PlaceOrderRequest;
import com.backendguru.orderservice.observability.OrderMetrics;
import com.backendguru.orderservice.outbox.OutboxAppender;
import com.backendguru.orderservice.outbox.OutboxEventRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saga orchestrator. Each step is its own short transaction; compensations run on failure.
 *
 * <p>Steps: 1) fetch cart, 2) persist order PENDING, 3) reserve inventory, 4) charge payment, 5)
 * commit inventory, 6) mark order CONFIRMED, 7) clear cart (best-effort).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

  private final OrderRepository orderRepository;
  private final CartClient cartClient;
  private final InventoryClient inventoryClient;
  private final PaymentClient paymentClient;
  private final OrderEventPublisher eventPublisher;
  private final OutboxEventRepository outboxRepository;
  private final OutboxAppender outboxAppender;
  private final OrderMetrics metrics;

  public OrderResponse placeOrder(Long userId, PlaceOrderRequest req) {
    return metrics.placeOrderTimer().record(() -> placeOrderInternal(userId, req));
  }

  private OrderResponse placeOrderInternal(Long userId, PlaceOrderRequest req) {
    // 1. Fetch cart
    CartSnapshot cart = cartClient.getCart(String.valueOf(userId)).data();
    if (cart == null || cart.items() == null || cart.items().isEmpty()) {
      throw new ValidationException("Cart is empty");
    }
    String currency = cart.items().get(0).priceCurrency();

    // 2. Persist PENDING order
    Order order = persistPendingOrder(userId, cart, currency);
    log.info("Order {} created PENDING for user {}", order.getId(), userId);

    List<Long> reservationIds = new ArrayList<>();
    Long paymentId = null;

    try {
      // 3. Reserve inventory for each item
      for (OrderItem item : order.getItems()) {
        ReservationSnapshot reservation;
        try {
          reservation =
              inventoryClient
                  .reserve(
                      Map.of(
                          "productId", item.getProductId(),
                          "quantity", item.getQuantity(),
                          "orderId", order.getId()))
                  .data();
        } catch (RuntimeException ex) {
          // Insufficient stock or service down
          releaseReservations(reservationIds);
          markCancelled(order, "Reservation failed: " + ex.getMessage());
          if (ex instanceof SagaException) throw ex;
          throw new InventoryUnavailableException(ex.getMessage());
        }
        item.setReservationId(reservation.reservationId());
        reservationIds.add(reservation.reservationId());
      }
      orderRepository.save(order); // persist reservation ids
      log.info("Order {} reserved {} items", order.getId(), reservationIds.size());

      // 4. Charge payment
      PaymentSnapshot payment;
      try {
        payment =
            paymentClient
                .charge(
                    new ChargeRequest(order.getId(), order.getTotalAmount(), currency, req.card()))
                .data();
      } catch (RuntimeException ex) {
        releaseReservations(reservationIds);
        markCancelled(order, "Payment failed: " + ex.getMessage());
        if (ex instanceof SagaException) throw ex;
        throw new PaymentFailedException(ex.getMessage());
      }
      paymentId = payment.paymentId();
      order.setPaymentId(paymentId);
      orderRepository.save(order);
      log.info("Order {} payment {} authorized", order.getId(), paymentId);

      // 5. Commit reservations
      try {
        for (Long resId : reservationIds) inventoryClient.commit(resId);
      } catch (RuntimeException ex) {
        // Critical: payment authorized but commit failed → refund + release any HELD
        if (paymentId != null) {
          try {
            paymentClient.refund(paymentId);
          } catch (RuntimeException refundEx) {
            log.error("Refund failed during compensation for payment {}", paymentId, refundEx);
          }
        }
        releaseReservations(reservationIds);
        markCancelled(order, "Inventory commit failed: " + ex.getMessage());
        throw new SagaException("Inventory commit failed", ex);
      }

      // 6. Mark CONFIRMED + write outbox row in the SAME transaction (atomic dual-write fix)
      order.setStatus(OrderStatus.CONFIRMED);
      orderRepository.save(order);
      outboxRepository.save(outboxAppender.buildOrderConfirmed(order));
      metrics.incrementPlaced(order.getCurrency());
      log.info("Order {} CONFIRMED + outbox event queued", order.getId());

      // 6b. Direct AMQP publish (best-effort, low-latency path for Phase 6 consumer)
      eventPublisher.publishOrderConfirmed(order);

      // 7. Clear cart (best-effort; fallback logs and continues)
      try {
        cartClient.clearCart(String.valueOf(userId));
      } catch (RuntimeException ex) {
        log.warn(
            "Cart clear failed after CONFIRMED order {} — manual cleanup may be needed",
            order.getId(),
            ex);
      }

      return OrderResponse.from(order);
    } catch (SagaException | InventoryUnavailableException | PaymentFailedException e) {
      throw e;
    } catch (RuntimeException unexpected) {
      // Defensive: any other failure → compensate everything
      log.error("Unexpected saga failure for order {}", order.getId(), unexpected);
      if (paymentId != null) {
        try {
          paymentClient.refund(paymentId);
        } catch (RuntimeException ignored) {
        }
      }
      releaseReservations(reservationIds);
      markCancelled(order, "Unexpected: " + unexpected.getMessage());
      throw new SagaException("Saga failed", unexpected);
    }
  }

  @Transactional(readOnly = true)
  public OrderResponse getOrder(Long orderId, Long requestingUserId) {
    Order order =
        orderRepository
            .findWithItemsById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order " + orderId + " not found"));
    if (!order.getUserId().equals(requestingUserId)) {
      // Avoid leaking existence; return 404 instead of 403.
      throw new ResourceNotFoundException("Order " + orderId + " not found");
    }
    return OrderResponse.from(order);
  }

  @Transactional(readOnly = true)
  public List<OrderResponse> listOrdersForUser(Long userId) {
    return orderRepository.findByUserIdOrderByIdDesc(userId).stream()
        .map(OrderResponse::from)
        .toList();
  }

  // -------------------- helpers --------------------

  @Transactional
  protected Order persistPendingOrder(Long userId, CartSnapshot cart, String currency) {
    Order order =
        Order.builder()
            .userId(userId)
            .status(OrderStatus.PENDING)
            .totalAmount(cart.totalAmount())
            .currency(currency)
            .build();
    for (var ci : cart.items()) {
      OrderItem oi =
          OrderItem.builder()
              .productId(ci.productId())
              .productName(ci.productName())
              .priceAmount(ci.priceAmount())
              .priceCurrency(ci.priceCurrency())
              .quantity(ci.quantity())
              .build();
      order.addItem(oi);
    }
    if (order.getTotalAmount() == null) order.setTotalAmount(BigDecimal.ZERO);
    return orderRepository.save(order);
  }

  @Transactional
  protected void markCancelled(Order order, String reason) {
    order.setStatus(OrderStatus.CANCELLED);
    order.setFailureReason(reason);
    orderRepository.save(order);
    metrics.incrementCancelled(classifyReason(reason));
  }

  private static String classifyReason(String reason) {
    if (reason == null) return "unknown";
    String r = reason.toLowerCase();
    if (r.contains("reservation")) return "reservation";
    if (r.contains("payment")) return "payment";
    if (r.contains("commit")) return "commit";
    return "unexpected";
  }

  private void releaseReservations(List<Long> reservationIds) {
    for (Long id : reservationIds) {
      try {
        inventoryClient.release(id);
      } catch (RuntimeException ex) {
        log.warn("release({}) failed during compensation: {}", id, ex.toString());
      }
    }
  }
}
