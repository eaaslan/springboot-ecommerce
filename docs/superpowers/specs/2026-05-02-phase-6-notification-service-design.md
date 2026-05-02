# Phase 6 — Notification Service Design (RabbitMQ, async messaging)

## 1. Goal

Add an **asynchronous notification pipeline** triggered by `OrderConfirmedEvent`. Order Service publishes once an order reaches the `CONFIRMED` state; Notification Service consumes the event and "sends" an email/SMS (mocked — log-based for MVP).

This phase introduces:
- **Spring AMQP** (`spring-boot-starter-amqp`) on top of RabbitMQ 3.x
- **Message contract** in `shared/common` so producer + consumer share the schema
- **DLQ pattern** (Dead-Letter Queue + Dead-Letter Exchange) for poison-pill handling
- **Idempotent consumer** via a `processed_events` ledger table (eventId UNIQUE)
- **At-least-once delivery** semantics — duplicates can arrive; idempotency table makes them harmless
- **Best-effort publish** — Order saga is NOT extended into the publish step. If RabbitMQ is down, the order is still CONFIRMED; we log and move on. Phase 7 introduces the **Outbox pattern** to close that gap.

## 2. Architecture

```
                     ┌──────────────┐
   Order Service ───▶│   RabbitMQ   │───▶ Notification Service
   (publisher)       │              │     (consumer)
                     │ exchange:    │
                     │  order.events│
                     │ queue:       │
                     │  notification│        ──▶ "send" email/sms (logged)
                     │  .queue      │        ──▶ persist `notifications` row
                     │ dlq:         │        ──▶ insert `processed_events`
                     │  notification│
                     │  .queue.dlq  │
                     └──────────────┘
```

- **Exchange:** `order.events.exchange` (topic, durable)
- **Routing key:** `order.confirmed`
- **Queue:** `notification.order-confirmed.queue` (durable, x-dead-letter-exchange = `order.events.dlx`)
- **DLX:** `order.events.dlx` (topic) → `notification.order-confirmed.queue.dlq`

### Why DLQ + DLX?
RabbitMQ ack/nack model: if the consumer throws and we `requeue=false`, the message goes to the DLX. The DLX-bound DLQ stores the message for manual inspection. We DO NOT requeue automatically because a poison message would loop forever — instead, retry is bounded.

### Retry strategy
Spring AMQP `RetryTemplate` with `SimpleRetryPolicy` (3 attempts, exponential backoff 1s → 2s → 4s). On final failure → message rejected (no requeue) → routes to DLX → DLQ. Operator inspects DLQ.

## 3. Module Layout

```
services/notification-service/
├── pom.xml
└── src/main/java/com/backendguru/notificationservice/
    ├── NotificationServiceApplication.java
    ├── config/
    │   ├── RabbitConfig.java          # exchanges, queues, bindings, listener factory
    │   └── OpenApiConfig.java
    ├── notification/
    │   ├── Notification.java          # JPA entity (audit ledger)
    │   ├── NotificationRepository.java
    │   ├── ProcessedEvent.java        # idempotency dedup
    │   ├── ProcessedEventRepository.java
    │   ├── NotificationConsumer.java  # @RabbitListener entry point
    │   └── NotificationService.java   # business logic (idempotency + send)
    └── exception/GlobalExceptionHandler.java
└── src/main/resources/
    ├── application.yml
    └── db/migration/V1__init_notifications.sql
```

```
shared/common/src/main/java/com/backendguru/common/event/
└── OrderConfirmedEvent.java   # producer + consumer share schema
```

## 4. Message Contract

```java
public record OrderConfirmedEvent(
    String eventId,           // UUID — used for idempotency dedup
    Long orderId,
    Long userId,
    java.math.BigDecimal totalAmount,
    String currency,
    java.time.Instant occurredAt) {}
```

`eventId` is generated at the publisher (UUID.randomUUID()). The consumer's idempotency check uses it as the primary key in `processed_events`.

## 5. Database Schema

```sql
-- V1__init_notifications.sql

CREATE TABLE notifications (
    id              BIGSERIAL PRIMARY KEY,
    event_id        VARCHAR(36) NOT NULL,
    order_id        BIGINT NOT NULL,
    user_id         BIGINT NOT NULL,
    channel         VARCHAR(20) NOT NULL,    -- EMAIL | SMS (extension point)
    status          VARCHAR(20) NOT NULL,    -- SENT | FAILED
    payload         TEXT NOT NULL,
    failure_reason  VARCHAR(500),
    sent_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_notifications_event_channel UNIQUE (event_id, channel)
);

CREATE TABLE processed_events (
    event_id        VARCHAR(36) PRIMARY KEY,
    event_type      VARCHAR(80) NOT NULL,
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

Why two tables?
- `processed_events` is the **dedup gate** — quickly rejects duplicate deliveries.
- `notifications` is the **audit ledger** — what we did, when, and why (FAILED rows preserved for ops).

## 6. Idempotent Consumer Flow

```java
@RabbitListener(queues = "${app.rabbit.queue}")
public void handle(OrderConfirmedEvent event) {
  if (processedEventRepo.existsById(event.eventId())) {
    log.info("Skipping duplicate event {}", event.eventId());
    return;  // ack → message removed from queue
  }
  notificationService.send(event);          // does the work
  processedEventRepo.save(new ProcessedEvent(event.eventId(), "ORDER_CONFIRMED"));
}
```

**Note:** Insert into `processed_events` and `notifications` happen in the same `@Transactional` boundary. If `processed_events` fails (FK or PK violation from a true duplicate), nothing is logged twice.

**Race condition:** if the same event is delivered to two consumers in parallel and both pass the `existsById` check before either inserts, the second insert raises `DuplicateKeyException`. We catch it and ack — second consumer noops.

## 7. Publisher (Order Service)

```java
@Service
@RequiredArgsConstructor
public class OrderEventPublisher {
  private final RabbitTemplate rabbit;
  @Value("${app.rabbit.exchange}") String exchange;

  public void publishOrderConfirmed(Order order) {
    var event = new OrderConfirmedEvent(
        UUID.randomUUID().toString(),
        order.getId(),
        order.getUserId(),
        order.getTotalAmount(),
        order.getCurrency(),
        Instant.now());
    try {
      rabbit.convertAndSend(exchange, "order.confirmed", event);
    } catch (AmqpException ex) {
      log.error("Failed to publish OrderConfirmedEvent for order {}", order.getId(), ex);
      // best-effort: do NOT fail the order. Outbox pattern (Phase 7) closes this gap.
    }
  }
}
```

Wired into `OrderService.placeOrder()` immediately after `order.setStatus(CONFIRMED); orderRepository.save(order);` and before the cart-clear step.

## 8. Configuration

`application.yml` (notification-service):
```yaml
spring:
  rabbitmq:
    host: ${RABBIT_HOST:localhost}
    port: ${RABBIT_PORT:5672}
    username: ${RABBIT_USER:guest}
    password: ${RABBIT_PASS:guest}
  jackson:
    deserialization:
      fail-on-unknown-properties: false

app:
  rabbit:
    exchange: order.events.exchange
    queue: notification.order-confirmed.queue
    dlx: order.events.dlx
    dlq: notification.order-confirmed.queue.dlq
    routing-key: order.confirmed
```

## 9. Docker Compose

```yaml
rabbitmq:
  image: rabbitmq:3-management
  container_name: ecommerce-rabbitmq
  ports:
    - "5672:5672"     # AMQP
    - "15672:15672"   # management UI (guest/guest)
  healthcheck:
    test: ["CMD", "rabbitmq-diagnostics", "ping"]
    interval: 10s
    retries: 10
```

## 10. Testing

- **Unit (Mockito):** `NotificationServiceTest` — happy path, duplicate event, send failure persists FAILED.
- **Smoke:** Spring context loads with `@MockBean RabbitTemplate` (no live broker).
- **Integration (deferred):** Testcontainers RabbitMQ pushes a real event end-to-end. Stretch goal — only if time permits.

## 11. Trade-offs / Out-of-Scope

| Concern | Decision | Why |
|---|---|---|
| Outbox pattern | Deferred to Phase 7 | Phase 6 stays focused on AMQP basics; Phase 7 introduces Kafka + Outbox |
| Real email send | Out-of-scope | Mock with logger; SMTP/Iyzico-mailer adds noise |
| Multi-channel fan-out (email + SMS) | Out-of-scope | Single-channel (EMAIL) MVP; `channel` column is the extension point |
| Manual DLQ replay endpoint | Out-of-scope | Operator can re-publish via management UI; admin endpoint = future |
| Distributed tracing | Phase 8 | We propagate correlation-id headers; full tracing in Phase 8 |

## 12. Interview Talking Points

1. **At-least-once vs exactly-once.** RabbitMQ guarantees at-least-once. Exactly-once requires app-level idempotency — `processed_events` table.
2. **Why DLQ?** Bounded retry + poison-message isolation. Without DLX, a single bad message blocks the queue or causes infinite redelivery.
3. **Why not Kafka in Phase 6?** RabbitMQ is simpler, Spring Boot integration is excellent, and demonstrates the same async patterns. Kafka in Phase 7 adds partitioning, replay, and Outbox.
4. **Best-effort publish vs Outbox.** Today: order can be CONFIRMED but event lost (DB commits, RabbitMQ down). Outbox solves this by writing event to DB in same transaction, then a poller publishes — never lost.
5. **Manual ack vs auto-ack.** We use `AcknowledgeMode.AUTO` with retry — Spring acks on listener return, nacks on exception, retries via `RetryTemplate`. Manual ack is flexible but error-prone for a junior code review.

## 13. Acceptance Criteria

1. `docker compose up -d rabbitmq` healthchecks pass.
2. `mvn clean verify` succeeds for all 11 modules.
3. Place an order through the gateway → notification-service log shows "Notification sent for order X" and a row appears in `notifications` table.
4. Re-deliver same eventId (RabbitMQ management UI) → consumer logs "Skipping duplicate" and does not duplicate the row.
5. Throw inside the consumer 3 times → message lands in DLQ.
6. Tag `phase-6-complete` pushed to GitHub.
