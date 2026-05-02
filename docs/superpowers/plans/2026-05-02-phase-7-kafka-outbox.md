# Phase 7 — Kafka + Outbox Implementation Plan

**Goal:** Atomic event publishing via Outbox pattern + Kafka consumer in notification-service.

**Architecture:** Order saga writes `outbox_events` row in same TX as CONFIRMED. Scheduled relay polls PENDING rows, publishes to Kafka, marks PUBLISHED. Notification-service KafkaListener delegates to same idempotent service.

**Tech Stack:** Spring Boot Kafka, bitnami/kafka 3.7 KRaft mode, Postgres + Flyway V2, JUnit + Mockito.

---

## Tasks

### P7.T1 — Spec + plan + docker-compose Kafka
- Files: spec, plan, `docker-compose.yml`
- Add bitnami/kafka KRaft single-node, optional kafka-ui
- Commit: `chore(infra): add kafka (KRaft) to local stack + phase-7 spec/plan`

### P7.T2 — Outbox migration + entity + repo
- `services/order-service/src/main/resources/db/migration/V2__init_outbox.sql`
- `services/order-service/src/main/java/com/backendguru/orderservice/outbox/OutboxEvent.java`, `OutboxStatus.java`, `OutboxEventRepository.java`
- Repo method: `findTop50ByStatusOrderByIdAsc(OutboxStatus status)`
- Commit: `feat(order-service): outbox_events schema + JPA entity + repo`

### P7.T3 — Saga writes outbox row in same TX as CONFIRMED
- Modify `OrderService.placeOrder()` step 6 — call new helper `appendOutboxOrderConfirmed(order)` before publishing AMQP fallback
- Add ObjectMapper bean (Spring Boot autoconfigures one usually; reuse it)
- Commit: `feat(order-service): write outbox row atomically in same TX as CONFIRMED (closes dual-write gap)`

### P7.T4 — KafkaConfig + OutboxRelay
- `services/order-service/src/main/java/com/backendguru/orderservice/config/KafkaConfig.java` — ProducerFactory, KafkaTemplate
- `services/order-service/src/main/java/com/backendguru/orderservice/outbox/OutboxRelay.java` — @Scheduled, @Transactional, batch 50
- Add `spring-kafka` to order-service pom
- `@EnableScheduling` on a config class
- Commit: `feat(order-service): KafkaConfig + scheduled OutboxRelay (batch 50, sync send)`

### P7.T5 — Kafka consumer in notification-service
- Add `spring-kafka` dep
- `KafkaConfig.java` (ConsumerFactory, ConcurrentKafkaListenerContainerFactory, JsonDeserializer trusted packages)
- `KafkaNotificationConsumer.java` (@KafkaListener)
- Commit: `feat(notification-service): Kafka consumer parallel to AMQP (idempotent dedup applies to both)`

### P7.T6 — Order-service tests
- `OutboxRelayTest` — happy + Kafka send fail
- Update `OrderServiceTest` — stub OutboxEventRepository, verify outbox.save in happy path
- Update `OrderServiceApplicationTests` — exclude Kafka auto-config or @MockBean KafkaTemplate
- Commit: `test(order-service): OutboxRelay unit tests + saga test verifies outbox row creation`

### P7.T7 — Notification-service Kafka consumer test
- `KafkaNotificationConsumerTest` (Mockito)
- Update smoke test to disable kafka listener
- Commit: `test(notification-service): KafkaNotificationConsumer unit test`

### P7.T8 — Config Server entries
- `order-service.yml`: kafka bootstrap-servers, producer config, outbox poll interval, topic name
- `notification-service.yml`: kafka bootstrap-servers, consumer config, group-id, topic
- Commit: `config(kafka): externalize bootstrap, topics, outbox poll interval`

### P7.T9 — Spotless + verify + README + Turkish notes + tag
- `mvn spotless:apply && mvn clean verify`
- README: Phase 7 ✅, run section adds kafka, try-it adds note about outbox flow
- `docs/learning/phase-7-notes.md`: dual-write problem, Outbox pattern, polling vs CDC, Kafka semantics, replay, interview Q&A
- Tag `phase-7-complete`, push
- Commits: chore(spotless), docs

## Verification

1. `mvn clean verify` → 12 modules SUCCESS
2. Tag pushed: `phase-7-complete`
3. Outbox row created in saga → relay publishes → kafka consumer logs Notification SENT (smoke optional, full e2e doc'd)
