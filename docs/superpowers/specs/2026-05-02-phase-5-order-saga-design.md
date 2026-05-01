# Phase 5 — Order Saga + Inventory + Payment Design

**Status:** Approved (autonomous)
**Date:** 2026-05-02
**Phase:** 5 of 12
**Depends on:** Phase 0–4 (foundation, JWT, Product, Cart with Redis)

---

## 1. Overview

Add three services that together implement the **checkout flow** as a **Saga orchestration**:

- `services/inventory-service` (port 8084) — stock per product, atomic reserve/release with optimistic locking
- `services/payment-service` (port 8085) — Iyzico-shaped mock; SUCCESS/FAIL by card number
- `services/order-service` (port 8086) — orchestrates the saga: cart → order draft → reserve inventory → init+confirm payment → commit inventory → finalize order → clear cart. On any failure, runs compensations.

### 1.1 Goals

- Each new service is a standalone Spring Boot module with its own PostgreSQL database (database-per-service)
- All inter-service calls go through OpenFeign + Resilience4j (CB + Retry + TimeLimiter), reusing Phase 3 patterns
- `OrderService.placeOrder()` runs the saga synchronously; success returns the persisted order; failure runs the matching compensations and throws a domain exception
- Inventory reservations are best-effort short-term holds (committed at saga end, released on failure). No background expiration job in this phase.
- Iyzico-shaped `PaymentClient`: request body matches Iyzico iyzipay-java structure (`cardHolderName`, `cardNumber`, `expireMonth`, `expireYear`, `cvc`, `price`, `currency`, `paymentChannel`). Mock returns SUCCESS unless card is the documented test-fail number `4111-1111-1111-1115`.
- Saga happy path + at least 2 failure paths covered by integration tests
- Order Service exposes `POST /api/orders` (place order from current user's cart) and `GET /api/orders/{id}`
- Gateway routes `/api/orders/**` to order-service; inventory + payment internal-only (no gateway route)

### 1.2 Non-goals

- Real Iyzico SDK integration (deferred — needs sandbox API keys; mock proves saga semantics)
- Kafka event-driven choreography (Phase 7)
- Reservation expiration via scheduled job (Phase 8 or with Spring Schedule once needed)
- Refund flows
- Order modification, cancellation by user (only saga compensation cancels)
- Shipping, tax, promotions, coupons, gift cards
- Order history pagination + filtering (basic list only)

---

## 2. Saga flow

```
POST /api/orders  (X-User-Id: 42, Authorization: Bearer ...)
   │
   ▼
┌─────────────────────────────────────────────────────────────┐
│ Order Service — placeOrder(userId)                           │
│                                                              │
│ 1. cart = cartClient.getCart(userId)              ─[Feign]──→ Cart Service     │
│    if empty → ValidationException 400                                  │
│                                                                        │
│ 2. order = save(Order { userId, items, total, status=PENDING })       │
│    (orderdb.orders + order_items)                                      │
│                                                                        │
│ 3. for each item: inventoryClient.reserve(productId, qty, orderId)  ──→ Inventory Service │
│    on first failure: release prior reservations + order.status=CANCELLED + throw                     │
│                                                                        │
│ 4. payment = paymentClient.charge(card, amount, orderId)         ──→ Payment Service      │
│    on FAIL: release all reservations + order.status=CANCELLED + throw                                │
│                                                                        │
│ 5. for each item: inventoryClient.commit(reservationId)         ──→ Inventory Service     │
│    on failure: paymentClient.refund(paymentId) + release remaining + order.status=CANCELLED         │
│                                                                        │
│ 6. order.status = CONFIRMED, save                                      │
│                                                                        │
│ 7. cartClient.clear(userId)                                  ──→ Cart Service             │
│    (best effort; failure doesn't undo confirmed order — log warning)                                 │
│                                                                        │
│ return OrderResponse                                                   │
└─────────────────────────────────────────────────────────────┘
```

### 2.1 Compensation table

| Step that fails | Compensations to run | Final order status |
|---|---|---|
| 1 cart fetch | none (no state changed yet) | not created |
| 3 reserve | release prior reservations | CANCELLED |
| 4 payment | release all reservations | CANCELLED |
| 5 commit inventory | refund payment + release remaining reservations | CANCELLED |
| 6 finalize | (saga successfully through 5; rare — DB write fail) refund + release | CANCELLED |
| 7 clear cart | log warning; order stays CONFIRMED | CONFIRMED |

### 2.2 Why orchestration, not choreography?

| Style | Pros | Cons |
|---|---|---|
| **Orchestration** (this phase) | Single source of truth (order-service), easier to reason about, sync API works without message broker | Tight coupling, all services must be up |
| **Choreography** (Phase 7) | Loose coupling, scales horizontally | Distributed state, harder to debug, eventual consistency |

For Phase 5, orchestration via Feign+Resilience4j is the right step. Phase 7 will introduce Kafka outbox + event-driven choreography on top of this.

### 2.3 Why synchronous Feign instead of async?

The saga is inherently linear (each step depends on previous). Async Feign or Kafka commands would add complexity without throughput gain at this scale. Resilience4j keeps each step bounded (2s timeout, 3 retries, CB).

---

## 3. Inventory Service

### 3.1 Schema

```sql
-- inventorydb
CREATE TABLE inventory_items (
    id              BIGSERIAL    PRIMARY KEY,
    product_id      BIGINT       NOT NULL UNIQUE,
    available_qty   INTEGER      NOT NULL,
    reserved_qty    INTEGER      NOT NULL DEFAULT 0,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_inventory_items_product_id ON inventory_items(product_id);

CREATE TABLE inventory_reservations (
    id              BIGSERIAL    PRIMARY KEY,
    inventory_id    BIGINT       NOT NULL REFERENCES inventory_items(id),
    order_id        BIGINT       NOT NULL,
    quantity        INTEGER      NOT NULL,
    status          VARCHAR(20)  NOT NULL,  -- HELD, COMMITTED, RELEASED
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_inv_reservations_order_id ON inventory_reservations(order_id);
CREATE INDEX idx_inv_reservations_status ON inventory_reservations(status);
```

Seed (V2): create one `inventory_items` row for each Phase 2 product (id 1..20) with stock matching `stock_quantity` from product seed.

### 3.2 Endpoints (internal)

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/api/inventory/reservations` | `{productId, quantity, orderId}` | `{reservationId, productId, quantity, status:HELD}` |
| POST | `/api/inventory/reservations/{id}/commit` | — | `204` |
| POST | `/api/inventory/reservations/{id}/release` | — | `204` |
| GET | `/api/inventory/{productId}` | — | `{productId, availableQty, reservedQty}` |

### 3.3 Reserve logic (with optimistic lock)

```java
@Transactional
public Reservation reserve(Long productId, int qty, Long orderId) {
  InventoryItem item = repo.findByProductId(productId)
      .orElseThrow(() -> new ResourceNotFoundException(...));
  if (item.getAvailableQty() < qty) {
    throw new ValidationException("Insufficient stock");
  }
  item.setAvailableQty(item.getAvailableQty() - qty);
  item.setReservedQty(item.getReservedQty() + qty);
  // @Version on InventoryItem → OptimisticLockException on concurrent update → caller retries
  Reservation res = Reservation.builder()
      .inventoryId(item.getId())
      .orderId(orderId)
      .quantity(qty)
      .status(ReservationStatus.HELD)
      .build();
  return reservationRepo.save(res);
}
```

`commit()`: reservation HELD → COMMITTED, item.reservedQty -= qty (the units leave the system). `release()`: HELD → RELEASED, item.availableQty += qty, item.reservedQty -= qty (units return to available pool).

---

## 4. Payment Service

### 4.1 Schema

```sql
-- paymentdb
CREATE TABLE payments (
    id               BIGSERIAL    PRIMARY KEY,
    order_id         BIGINT       NOT NULL,
    amount           NUMERIC(12,2) NOT NULL,
    currency         VARCHAR(3)   NOT NULL,
    card_last_four   VARCHAR(4)   NOT NULL,
    status           VARCHAR(20)  NOT NULL,  -- AUTHORIZED, FAILED, REFUNDED
    iyzico_payment_id VARCHAR(64),           -- mocked
    failure_reason   VARCHAR(255),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_payments_order_id ON payments(order_id);
```

### 4.2 Endpoints (internal)

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/api/payments` | `{orderId, amount, currency, card:{holderName, number, expireMonth, expireYear, cvc}}` | `{paymentId, status, iyzicoPaymentId, failureReason?}` |
| POST | `/api/payments/{id}/refund` | — | `{paymentId, status:REFUNDED}` |
| GET | `/api/payments/{id}` | — | `PaymentResponse` |

### 4.3 Iyzico-shaped mock logic

```java
public PaymentResponse charge(ChargeRequest req) {
  String number = req.card().number().replaceAll("-", "");
  Payment p = Payment.builder()
      .orderId(req.orderId())
      .amount(req.amount())
      .currency(req.currency())
      .cardLastFour(number.substring(number.length() - 4))
      .build();

  if ("4111111111111115".equals(number)) {
    p.setStatus(PaymentStatus.FAILED);
    p.setFailureReason("Card declined (mock)");
    paymentRepo.save(p);
    return PaymentResponse.from(p);
  }

  p.setStatus(PaymentStatus.AUTHORIZED);
  p.setIyzicoPaymentId("MOCK-" + UUID.randomUUID());
  paymentRepo.save(p);
  return PaymentResponse.from(p);
}
```

The card number `4111-1111-1111-1115` is documented as the deterministic-fail number; everything else succeeds. Refund flips AUTHORIZED → REFUNDED, idempotent (already-refunded is a no-op).

### 4.4 Iyzico real integration — deferred

The shape is `iyzipay-java` SDK compatible (`PaymentChannel`, `Locale`, `BasketItem`). When the user obtains sandbox API keys, only the mock body is replaced; the controller and DTO layer stay identical.

---

## 5. Order Service

### 5.1 Schema

```sql
-- orderdb
CREATE TABLE orders (
    id               BIGSERIAL     PRIMARY KEY,
    user_id          BIGINT        NOT NULL,
    status           VARCHAR(20)   NOT NULL,  -- PENDING, CONFIRMED, CANCELLED
    total_amount     NUMERIC(12,2) NOT NULL,
    currency         VARCHAR(3)    NOT NULL,
    payment_id       BIGINT,
    failure_reason   VARCHAR(255),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version          BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(status);

CREATE TABLE order_items (
    id              BIGSERIAL     PRIMARY KEY,
    order_id        BIGINT        NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id      BIGINT        NOT NULL,
    product_name    VARCHAR(200)  NOT NULL,
    price_amount    NUMERIC(12,2) NOT NULL,
    price_currency  VARCHAR(3)    NOT NULL,
    quantity        INTEGER       NOT NULL,
    reservation_id  BIGINT
);
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
```

### 5.2 Endpoints

| Method | Path | Auth | Body | Response |
|---|---|---|---|---|
| POST | `/api/orders` | Bearer | `{card: {...}}` | `OrderResponse` (201 if confirmed, 409 if saga compensated) |
| GET | `/api/orders/{id}` | Bearer | — | `OrderResponse` (only if order belongs to current user) |
| GET | `/api/orders` | Bearer | — | `List<OrderResponse>` (current user's orders) |

`POST /api/orders` body carries the card details (paid through gateway → order-service → payment-service Feign). User passes card data only in this single call; not stored beyond the masked last-four in the payment row.

### 5.3 Failure responses

- Cart empty → 400 `VALIDATION_FAILED`
- Inventory insufficient on any product → 409 `CONFLICT` with `failureReason="Insufficient stock for product X"`
- Payment declined → 402 (Payment Required) with `failureReason="Card declined"` (we add a new `ErrorCode.PAYMENT_FAILED` mapping to 402)
- Service unreachable (CB open after retries) → 503 `SERVICE_UNAVAILABLE`

`ErrorCode.PAYMENT_FAILED` is added to `common`.

---

## 6. Database provisioning

Update `docker/postgres/init.sql`:

```sql
-- existing
CREATE USER product WITH PASSWORD 'pass';
CREATE DATABASE productdb OWNER product;
GRANT ALL PRIVILEGES ON DATABASE productdb TO product;

-- Phase 5 additions
CREATE USER inventory WITH PASSWORD 'pass';
CREATE DATABASE inventorydb OWNER inventory;
GRANT ALL PRIVILEGES ON DATABASE inventorydb TO inventory;

CREATE USER payment WITH PASSWORD 'pass';
CREATE DATABASE paymentdb OWNER payment;
GRANT ALL PRIVILEGES ON DATABASE paymentdb TO payment;

CREATE USER orderusr WITH PASSWORD 'pass';
CREATE DATABASE orderdb OWNER orderusr;
GRANT ALL PRIVILEGES ON DATABASE orderdb TO orderusr;
```

Existing volumes: manually run the new database creates against the running postgres container (init.sql only runs on fresh volume).

---

## 7. Resilience4j configuration

For each Feign client (`cartClient`, `inventoryClient`, `paymentClient`):

```yaml
resilience4j:
  circuitbreaker.instances.<clientName>:
    sliding-window-size: 10
    minimum-number-of-calls: 5
    failure-rate-threshold: 50
    wait-duration-in-open-state: 10s
  retry.instances.<clientName>:
    max-attempts: 3
    wait-duration: 200ms
    exponential-backoff-multiplier: 2
    retry-exceptions:
      - feign.RetryableException
      - java.io.IOException
  timelimiter.instances.<clientName>:
    timeout-duration: 2s
```

Same numbers across all clients (Phase 3 pattern). Tuneable per service in production.

---

## 8. Acceptance criteria

- [ ] `mvn clean verify` BUILD SUCCESS for whole reactor (10 modules — common + 3 infra + user + product + cart + inventory + payment + order)
- [ ] Each new service starts on its own port and registers with Eureka
- [ ] `inventorydb`, `paymentdb`, `orderdb` provisioned (via running container psql)
- [ ] Saga happy path: register → login → add 2 items to cart → POST /api/orders with `card{number:"4111-1111-1111-1111"...}` → 201 `CONFIRMED` order, cart empty, inventory committed, payment AUTHORIZED
- [ ] Saga payment failure: same setup, card `4111-1111-1111-1115` → 402, order CANCELLED, all reservations RELEASED, no payment AUTHORIZED in DB
- [ ] Saga inventory failure: cart with quantity > stock → 409, order CANCELLED, prior reservations released
- [ ] `GET /api/orders/{id}` returns 404 if order belongs to a different user
- [ ] All saga failure paths covered by integration tests (Testcontainers + WireMock for cross-service isolation)

---

## 9. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Saga step takes longer than 2s timeout under load → false negatives | Production tuning; for Phase 5 acceptable; revisit in Phase 11 performance |
| Compensation itself fails (e.g., release fails after payment fail) | Log + alert; manual reconciliation; Phase 7 outbox pattern provides retries |
| Order service holds DB transaction across remote calls (long-running tx) | We do NOT wrap saga in a single `@Transactional` — each saga step is its own short tx. Saga state persisted progressively (PENDING → CONFIRMED/CANCELLED). |
| Eureka discovery cache lag on startup → first saga may 503 | Lower cache TTL or add manual delay before first call; covered by Resilience4j retry |
| Concurrent place-order on low-stock product creates over-reservation | `@Version` optimistic lock on `inventory_items`; concurrent requests → second one gets `OptimisticLockException` → translated to `409 CONFLICT` |
| Card number logging | Card never logged; only last-four stored. Mask in any error messages. |

---

## 10. Interview topics unlocked

Saga pattern (orchestration vs choreography), 2PC vs Saga, compensating transactions, idempotency for saga steps, optimistic locking with `@Version` for stock contention, eventual consistency in distributed systems, request idempotency keys (introduced concept; full impl Phase 7), payment gateway integration patterns, PCI DSS scope (we don't store full PAN), iyzipay vs Stripe vs PayU API surface differences, atomic vs eventual reservation models, why not 2PC across services (SPOF, network failure complexity).
