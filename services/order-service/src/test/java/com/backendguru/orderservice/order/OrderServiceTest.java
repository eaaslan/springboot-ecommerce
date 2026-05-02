package com.backendguru.orderservice.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.backendguru.common.dto.ApiResponse;
import com.backendguru.common.error.ValidationException;
import com.backendguru.orderservice.client.CartClient;
import com.backendguru.orderservice.client.InventoryClient;
import com.backendguru.orderservice.client.PaymentClient;
import com.backendguru.orderservice.client.dto.CartSnapshot;
import com.backendguru.orderservice.client.dto.CartSnapshot.CartItemSnapshot;
import com.backendguru.orderservice.client.dto.ChargeRequest.CardDetails;
import com.backendguru.orderservice.client.dto.PaymentSnapshot;
import com.backendguru.orderservice.client.dto.ReservationSnapshot;
import com.backendguru.orderservice.event.OrderEventPublisher;
import com.backendguru.orderservice.exception.InventoryUnavailableException;
import com.backendguru.orderservice.exception.PaymentFailedException;
import com.backendguru.orderservice.exception.SagaException;
import com.backendguru.orderservice.order.dto.PlaceOrderRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

  @Mock OrderRepository orderRepository;
  @Mock CartClient cartClient;
  @Mock InventoryClient inventoryClient;
  @Mock PaymentClient paymentClient;
  @Mock OrderEventPublisher eventPublisher;

  @InjectMocks OrderService orderService;

  private static final Long USER_ID = 42L;
  private static final CardDetails GOOD_CARD =
      new CardDetails("J. Doe", "4111111111111111", "12", "2030", "123");

  private PlaceOrderRequest req() {
    return new PlaceOrderRequest(GOOD_CARD);
  }

  private CartSnapshot oneItemCart() {
    return new CartSnapshot(
        USER_ID,
        List.of(
            new CartItemSnapshot(
                1L, "Widget", new BigDecimal("10.00"), "TRY", 2, new BigDecimal("20.00"))),
        2,
        new BigDecimal("20.00"));
  }

  private CartSnapshot twoItemCart() {
    return new CartSnapshot(
        USER_ID,
        List.of(
            new CartItemSnapshot(
                1L, "Widget", new BigDecimal("10.00"), "TRY", 2, new BigDecimal("20.00")),
            new CartItemSnapshot(
                2L, "Gadget", new BigDecimal("5.50"), "TRY", 4, new BigDecimal("22.00"))),
        6,
        new BigDecimal("42.00"));
  }

  @BeforeEach
  void stubSaveAssignsId() {
    AtomicLong seq = new AtomicLong(100);
    org.mockito.Mockito.lenient()
        .when(orderRepository.save(any(Order.class)))
        .thenAnswer(
            inv -> {
              Order o = inv.getArgument(0);
              if (o.getId() == null) o.setId(seq.incrementAndGet());
              return o;
            });
  }

  // ---------- happy path ----------

  @Test
  void placeOrderHappyPathConfirmsAndClearsCart() {
    when(cartClient.getCart(anyString())).thenReturn(ApiResponse.success(twoItemCart()));
    when(inventoryClient.reserve(any()))
        .thenReturn(ApiResponse.success(new ReservationSnapshot(501L, 1L, 2, "HELD")))
        .thenReturn(ApiResponse.success(new ReservationSnapshot(502L, 2L, 4, "HELD")));
    when(paymentClient.charge(any()))
        .thenReturn(
            ApiResponse.success(
                new PaymentSnapshot(
                    900L,
                    101L,
                    "SUCCEEDED",
                    new BigDecimal("42.00"),
                    "TRY",
                    "iyz-1",
                    null,
                    "1111")));

    var resp = orderService.placeOrder(USER_ID, req());

    assertThat(resp.status()).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(resp.paymentId()).isEqualTo(900L);
    assertThat(resp.items()).hasSize(2);
    verify(inventoryClient, times(2)).reserve(any());
    verify(inventoryClient).commit(501L);
    verify(inventoryClient).commit(502L);
    verify(paymentClient).charge(any());
    verify(cartClient).clearCart(String.valueOf(USER_ID));
    verify(eventPublisher).publishOrderConfirmed(any(Order.class));
    verify(inventoryClient, never()).release(anyLong());
    verify(paymentClient, never()).refund(anyLong());
  }

  // ---------- empty cart ----------

  @Test
  void emptyCartThrowsValidation() {
    when(cartClient.getCart(anyString()))
        .thenReturn(ApiResponse.success(new CartSnapshot(USER_ID, List.of(), 0, BigDecimal.ZERO)));

    assertThatThrownBy(() -> orderService.placeOrder(USER_ID, req()))
        .isInstanceOf(ValidationException.class);
    verify(orderRepository, never()).save(any());
    verify(inventoryClient, never()).reserve(any());
    verify(paymentClient, never()).charge(any());
  }

  // ---------- inventory fails on second item -> release first ----------

  @Test
  void reservationFailureReleasesPreviouslyReservedAndCancelsOrder() {
    when(cartClient.getCart(anyString())).thenReturn(ApiResponse.success(twoItemCart()));
    when(inventoryClient.reserve(any()))
        .thenReturn(ApiResponse.success(new ReservationSnapshot(501L, 1L, 2, "HELD")))
        .thenThrow(new RuntimeException("INSUFFICIENT_STOCK"));

    assertThatThrownBy(() -> orderService.placeOrder(USER_ID, req()))
        .isInstanceOfAny(InventoryUnavailableException.class, SagaException.class);

    verify(inventoryClient).release(501L);
    verify(paymentClient, never()).charge(any());
    ArgumentCaptor<Order> cap = ArgumentCaptor.forClass(Order.class);
    verify(orderRepository, atLeast(2)).save(cap.capture());
    Order last = cap.getValue();
    assertThat(last.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(last.getFailureReason()).contains("Reservation failed");
  }

  // ---------- payment declined -> release reservations + cancel ----------

  @Test
  void paymentFailureReleasesReservationsAndCancelsOrder() {
    when(cartClient.getCart(anyString())).thenReturn(ApiResponse.success(oneItemCart()));
    when(inventoryClient.reserve(any()))
        .thenReturn(ApiResponse.success(new ReservationSnapshot(501L, 1L, 2, "HELD")));
    when(paymentClient.charge(any())).thenThrow(new RuntimeException("CARD_DECLINED"));

    assertThatThrownBy(() -> orderService.placeOrder(USER_ID, req()))
        .isInstanceOfAny(PaymentFailedException.class, SagaException.class);

    verify(inventoryClient).release(501L);
    verify(paymentClient, never()).refund(anyLong());
    ArgumentCaptor<Order> cap = ArgumentCaptor.forClass(Order.class);
    verify(orderRepository, atLeastOnce()).save(cap.capture());
    Order last = cap.getValue();
    assertThat(last.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(last.getFailureReason()).contains("Payment failed");
  }

  // ---------- commit fails after charge -> refund + release + cancel ----------

  @Test
  void commitFailureTriggersRefundAndReleaseAndCancelsOrder() {
    when(cartClient.getCart(anyString())).thenReturn(ApiResponse.success(oneItemCart()));
    when(inventoryClient.reserve(any()))
        .thenReturn(ApiResponse.success(new ReservationSnapshot(501L, 1L, 2, "HELD")));
    when(paymentClient.charge(any()))
        .thenReturn(
            ApiResponse.success(
                new PaymentSnapshot(
                    900L,
                    101L,
                    "SUCCEEDED",
                    new BigDecimal("20.00"),
                    "TRY",
                    "iyz-1",
                    null,
                    "1111")));
    org.mockito.Mockito.doThrow(new RuntimeException("DB_DOWN")).when(inventoryClient).commit(501L);

    assertThatThrownBy(() -> orderService.placeOrder(USER_ID, req()))
        .isInstanceOf(SagaException.class);

    verify(paymentClient).refund(900L);
    verify(inventoryClient).release(501L);
    ArgumentCaptor<Order> cap = ArgumentCaptor.forClass(Order.class);
    verify(orderRepository, atLeastOnce()).save(cap.capture());
    Order last = cap.getValue();
    assertThat(last.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(last.getFailureReason()).contains("Inventory commit failed");
  }
}
