# Phase 7 — Kafka + Outbox Pattern Design

## 1. Goal

Close the dual-write gap from Phase 6 (`order CONFIRMED` committed but RabbitMQ publish lost). Introduce **Kafka** as a second event bus *alongside* RabbitMQ and route the order-confirmed flow through the **Transactional Outbox pattern** so we have an atomic guarantee:

> **If the order is CONFIRMED in the DB, the event will eventually reach the consumers — exactly never lost.**

## 2. Architecture Overview

```
                                            ┌─────────── Phase 7 ───────────┐
   ┌───────────────────────────────────────────┐                              │
   │  Order Service                            │                              │
   │                                           │   poll every 1s              │
   │  saga step 6:  ┌────────────────┐         │ ┌───────────────┐            │
   │  ──in same TX▶│ orders          │         │ │ OutboxRelay   │ ──publish──▶ Kafka topic
   │              ▶│ outbox_events   │         │ │ (@Scheduled)  │   order.confirmed
   │               │   PENDING       │  ────── │▶│  poll PENDING │
   │               └────────────────┘         │ │  → mark        │
   │                                          │ │   PUBLISHED    │
   │  step 6b (legacy): rabbit.publish (best- │ │   or FAILED+   │
   │   effort) — kept for AMQP consumers      │ │   attempts++   │
   └───────────────────────────────────────────┘ └───────────────┘
                                                        │
                                                        ▼
                                            ┌──────────────────────┐
                                            │ Notification Service │
                                            │ (NEW)                │
                                            │  @KafkaListener  ────────▶ same idempotent
                                            │  (group=notification)      handleOrderConfirmed
                                            └──────────────────────┘
                                                        ▲
                                            ┌──────────────────────┐
                                            │ Notification Service │
                                            │  @RabbitListener     │
                                            │  (existing AMQP path)│
                                            └──────────────────────┘
```

Both AMQP and Kafka paths terminate in the same `NotificationService.handleOrderConfirmed(event)`. Idempotency (`processed_events.event_id`) protects against double-delivery across both paths.

## 3. Outbox Table

```sql
-- V2__init_outbox.sql (order-service)

CREATE TABLE outbox_events (
    id              BIGSERIAL PRIMARY KEY,
    event_id        VARCHAR(36) NOT NULL UNIQUE,    -- propagated to broker payload
    aggregate_type  VARCHAR(80) NOT NULL,           -- 'ORDER'
    aggregate_id    VARCHAR(80) NOT NULL,           -- order id stringified
    event_type      VARCHAR(80) NOT NULL,           -- 'ORDER_CONFIRMED'
    payload         TEXT NOT NULL,                  -- JSON of OrderConfirmedEvent
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts        INT NOT NULL DEFAULT 0,
    last_error      VARCHAR(500),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_outbox_pending ON outbox_events(status, created_at)
    WHERE status = 'PENDING';
```

The partial index on `status='PENDING'` keeps the relay poll cheap as the table grows — only unpublished rows are scanned.

## 4. Producer Side — Same-Transaction Write

Inside `OrderService.placeOrder()`, after step 6 (`order.setStatus(CONFIRMED)`), we **insert an outbox row in the same `@Transactional` boundary** as the order save:

```java
order.setStatus(OrderStatus.CONFIRMED);
orderRepository.save(order);

// In the SAME transaction — atomicity guaranteed by DB
outboxEventRepository.save(OutboxEvent.builder()
    .eventId(UUID.randomUUID().toString())
    .aggregateType("ORDER")
    .aggregateId(String.valueOf(order.getId()))
    .eventType("ORDER_CONFIRMED")
    .payload(objectMapper.writeValueAsString(buildEvent(order)))
    .status(PENDING)
    .build());
```

If the transaction commits, **both** the order status update and the outbox row are durably persisted. If the transaction rolls back, **neither** is. Dual-write problem solved.

The legacy direct-RabbitMQ publish (Phase 6) stays as best-effort fallback for low-latency AMQP consumers; it's clearly secondary to the outbox path.

## 5. Outbox Relay — `@Scheduled` Poller

```java
@Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:1000}")
@Transactional
public void publishPending() {
  List<OutboxEvent> batch = repo.findTop50ByStatusOrderByIdAsc(PENDING);
  for (OutboxEvent ev : batch) {
    try {
      kafkaTemplate.send(topic, ev.getAggregateId(), ev.getPayload()).get(5, SECONDS);
      ev.setStatus(PUBLISHED);
      ev.setPublishedAt(OffsetDateTime.now());
    } catch (Exception ex) {
      ev.setStatus(FAILED);
      ev.setAttempts(ev.getAttempts() + 1);
      ev.setLastError(truncate(ex.getMessage(), 500));
    }
  }
}
```

- Batch size 50 keeps polls bounded.
- `kafkaTemplate.send(...).get(5, SECONDS)` makes the publish synchronous *within* the relay so we know whether to mark PUBLISHED.
- FAILED rows can be retried by a separate "retry FAILED" job (out of scope here — operator can manually `UPDATE outbox_events SET status='PENDING' WHERE status='FAILED' AND attempts < 5`).

**Trade-off:** simple polling vs. CDC (Debezium). Polling is good enough for a learning project and demonstrates the pattern; Debezium reads the WAL and is the production-grade approach (mentioned in interview notes).

### Concurrency
Single instance of order-service for the MVP. With multiple instances, polling races would create duplicate sends — solutions:
- `SELECT ... FOR UPDATE SKIP LOCKED` row-level lock during fetch (Postgres 9.5+)
- Leader election (ShedLock)
- Single relay node by deployment design

We document this without implementing — interview talking point.

## 6. Consumer Side — Kafka Listener

`notification-service` adds a Kafka listener parallel to the existing `@RabbitListener`. Both call `NotificationService.handleOrderConfirmed(event)`. Idempotency table dedups across both transports.

```java
@KafkaListener(topics = "${app.kafka.topics.order-confirmed}", groupId = "notification-service")
public void onOrderConfirmedFromKafka(OrderConfirmedEvent event) {
  service.handleOrderConfirmed(event);
}
```

Kafka delivery semantics: at-least-once with consumer group offsets. Duplicate consumption protected by `processed_events`.

## 7. Topology

| Component | Detail |
|---|---|
| Kafka image | `bitnami/kafka:3.7` (KRaft mode — no Zookeeper) |
| Bootstrap | `localhost:9092` (host) / `kafka:9092` (in-network) |
| Topic | `order.confirmed` (partitions=3, replication=1 for dev) |
| Producer key | order id (preserves per-order ordering) |
| Consumer group | `notification-service` |
| Auto-create topics | enabled in dev |
| kafka-ui | optional, http://localhost:8089 |

## 8. Testing

### Unit (Mockito)
- `OutboxRelayTest`: PENDING → PUBLISHED on success; PENDING → FAILED + attempts++ on KafkaException
- `OrderServiceTest` (existing) updated: stub `OutboxEventRepository`, verify outbox row save in saga happy path
- `KafkaNotificationConsumerTest`: delegates to `NotificationService.handleOrderConfirmed`

### Smoke
- order-service smoke: `@MockBean KafkaTemplate` to avoid bootstrapping kafka
- notification-service smoke: kafka listener auto-startup=false

## 9. Trade-offs & Out-of-Scope

| Concern | Decision | Why |
|---|---|---|
| Debezium CDC | Out-of-scope | Polling teaches the pattern; Debezium adds operational complexity unsuitable for MVP |
| Schema registry (Avro/Protobuf) | Out-of-scope | JSON for simplicity; mention as production hardening |
| Multi-instance relay coordination | Documented only | Single-instance MVP; mentioned for interview discussion |
| Kafka Streams / Connect | Out-of-scope | Phase 9 (analytics) territory |
| Replay tooling | Out-of-scope | Note: replay = re-publish PUBLISHED rows or re-set offset; manual via kafka-ui |

## 10. Interview Talking Points

1. **Dual-write problem** — Why writing to DB + broker in two separate calls is unsafe (broker down between commits = lost event).
2. **Outbox vs. CDC** — Polling outbox table vs. reading WAL with Debezium. Trade-offs.
3. **At-least-once + idempotency = effectively-once** — same as Phase 6, now reinforced because both AMQP and Kafka can re-deliver.
4. **Kafka vs. RabbitMQ** — log vs. queue, replay, partitioning, ordering guarantees per partition.
5. **Outbox relay scaling** — single relay node, leader election, or `FOR UPDATE SKIP LOCKED`.
6. **Why both AMQP + Kafka?** — Educational. Real systems pick one; we keep both to compare and let interview cover the choice.

## 11. Acceptance Criteria

1. `docker compose up -d` brings up postgres, redis, rabbitmq, kafka — all healthy.
2. `mvn clean verify` succeeds for all 12 modules.
3. Place an order → outbox row appears as PENDING → within 1–2s flips to PUBLISHED → notification-service Kafka log shows "Notification SENT".
4. Kill Kafka, place an order: order is still CONFIRMED, outbox row stays PENDING; bring Kafka back → relay catches up.
5. Tag `phase-7-complete` pushed.
