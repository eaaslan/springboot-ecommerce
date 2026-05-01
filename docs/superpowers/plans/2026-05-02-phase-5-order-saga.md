# Phase 5 — Order Saga Implementation Plan

Spec: `docs/superpowers/specs/2026-05-02-phase-5-order-saga-design.md`

**Goal:** 3 new services (inventory, payment, order) with synchronous Saga orchestration via Feign+Resilience4j. Iyzico mocked.

## Tasks (compact, autonomous)

### Foundation
1. **Add `PAYMENT_FAILED` ErrorCode** (HTTP 402) to common; install common to local repo.
2. **Provision databases** in running postgres container: `inventorydb`, `paymentdb`, `orderdb` with respective users. Update `docker/postgres/init.sql` for fresh-volume reproducibility.
3. **Add inventory/payment/order modules to parent `pom.xml`** (will be created in tasks 4–6).

### Inventory Service (port 8084)
4. **Module skeleton + app + config + Flyway V1** (inventory_items + inventory_reservations) + V2 seed (rows for product 1..20 matching product seed stock).
5. **Entities + repositories + service + controller** — `InventoryItem`, `Reservation`, `ReservationStatus` enum; `reserve/commit/release/getByProductId`. `@Version` on InventoryItem.
6. **Tests**: smoke (H2) + repository + service unit + integration (Testcontainers postgres).
7. **Add to gateway-internal-only** (no public route — services reach this via Eureka).

### Payment Service (port 8085)
8. **Module skeleton + app + config + Flyway V1** (payments).
9. **Entity + repo + service (mock Iyzico) + controller** — `Payment`, `PaymentStatus` enum, `charge` mock with deterministic-fail card `4111-1111-1111-1115`, `refund`.
10. **Tests**: smoke + service unit + integration (Testcontainers).

### Order Service (port 8086) — Saga orchestration
11. **Module skeleton + app + config + Flyway V1** (orders + order_items).
12. **Entities + repos** — `Order`, `OrderItem`, `OrderStatus` enum.
13. **Feign clients**: `CartClient`, `InventoryClient`, `PaymentClient` with fallbacks throwing `BusinessException` subclasses.
14. **OrderSaga / OrderService** — `placeOrder(userId, cardDetails)` runs the 7-step flow with explicit compensations on each failure path.
15. **OrderController** — POST /api/orders, GET /api/orders/{id}, GET /api/orders.
16. **Tests**: unit OrderServiceTest (Mockito) for happy + 3 compensation paths; smoke; integration with WireMock stubbing all 3 downstream services.

### Wiring
17. **Config Server**: `inventory-service.yml`, `payment-service.yml`, `order-service.yml` (port + datasource + Resilience4j config) + dev overrides.
18. **API Gateway routes** in `api-gateway.yml`: `/api/orders/**` → order-service. Inventory + payment do NOT get gateway routes (internal services).

### Verify + ship
19. **Full reactor `mvn clean verify`** — 10 modules pass; Spotless clean.
20. **README + Turkish learning notes** (`docs/learning/phase-5-notes.md`): Saga pattern, orchestration vs choreography, 2PC, compensating transactions, idempotency, optimistic locking for stock, payment gateway patterns, mock interview Q&A.
21. **Tag `phase-5-complete`, push to main + tag**.
