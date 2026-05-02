# Phase 6 — Notification Service Implementation Plan

**Goal:** Build async notification pipeline: Order Service → RabbitMQ → Notification Service.

**Architecture:** Topic exchange + DLQ + idempotent consumer (processed_events table). Best-effort publish.

**Tech Stack:** Spring Boot AMQP, RabbitMQ 3-management, Postgres + Flyway, JUnit 5 + Mockito.

---

## Tasks

### P6.T1 — RabbitMQ in docker-compose + init.sql user/db
- Files: `docker-compose.yml`, `docker/postgres/init.sql`
- Add `rabbitmq:3-management` service with healthcheck. Add `notification` PG user + `notificationdb`.
- Verify: `docker compose up -d` keeps both green; management UI at http://localhost:15672 (guest/guest).
- Commit: `chore(infra): add rabbitmq + notificationdb to local stack`

### P6.T2 — notification-service skeleton
- Files: `services/notification-service/pom.xml`, `NotificationServiceApplication.java`, `application.yml`, root `pom.xml` modules
- Deps: starter-amqp, starter-data-jpa, starter-actuator, cloud-config, eureka-client, flyway, postgresql, lombok, springdoc, common
- Compile gate: `mvn -pl services/notification-service compile`
- Commit: `feat(notification-service): module skeleton + amqp dependencies`

### P6.T3 — Shared OrderConfirmedEvent in common
- Files: `shared/common/src/main/java/com/backendguru/common/event/OrderConfirmedEvent.java`
- Record with eventId, orderId, userId, totalAmount, currency, occurredAt.
- Commit: `feat(common): OrderConfirmedEvent record for AMQP message contract`

### P6.T4 — Flyway V1 (notifications + processed_events) + entities + repos
- Files: `V1__init_notifications.sql`, `Notification.java`, `NotificationRepository.java`, `ProcessedEvent.java`, `ProcessedEventRepository.java`
- Tables per spec §5.
- Commit: `feat(notification-service): JPA entities + Flyway schema (notifications + processed_events)`

### P6.T5 — RabbitConfig
- File: `RabbitConfig.java`
- Beans: `TopicExchange order.events.exchange`, `TopicExchange order.events.dlx`, durable `notification.order-confirmed.queue` with `x-dead-letter-exchange`, `notification.order-confirmed.queue.dlq`, bindings, `Jackson2JsonMessageConverter`, `SimpleRabbitListenerContainerFactory` with `RetryTemplate` (3 attempts, exp backoff).
- Commit: `feat(notification-service): RabbitConfig with DLQ + retry policy`

### P6.T6 — NotificationConsumer + NotificationService
- Files: `NotificationConsumer.java`, `NotificationService.java`
- `@RabbitListener` invokes service.handle(event); idempotency dedup via processed_events; persist Notification row (status=SENT); catch DuplicateKeyException → ack noop.
- Commit: `feat(notification-service): consumer + service (idempotent, log-based send)`

### P6.T7 — GlobalExceptionHandler + OpenApiConfig + smoke test
- Files: `GlobalExceptionHandler.java`, `OpenApiConfig.java`, `NotificationServiceApplicationTests.java`, `NotificationServiceTest.java` (Mockito unit)
- Smoke test disables config-server/eureka, mocks RabbitTemplate.
- Unit test covers happy + duplicate + send-fail paths.
- Verify: `mvn -pl services/notification-service test`
- Commit: `test(notification-service): smoke + unit tests for happy/dup/fail paths`

### P6.T8 — Order Service publisher
- Files: `services/order-service/src/main/java/com/backendguru/orderservice/event/OrderEventPublisher.java`, modify `OrderService.placeOrder()`, add `spring-boot-starter-amqp` to order-service pom
- Inject RabbitTemplate; publish OrderConfirmedEvent after status=CONFIRMED save; wrap in try/catch (best-effort)
- Update tests: stub publisher in OrderServiceTest
- Commit: `feat(order-service): publish OrderConfirmedEvent to RabbitMQ after CONFIRMED (best-effort)`

### P6.T9 — Config Server entries
- Files: `infrastructure/config-server/src/main/resources/configs/notification-service.yml`, `notification-service-dev.yml`, modify `order-service.yml` (add rabbit config)
- Externalize: rabbit host/port/credentials, exchange/queue/routing-key.
- Commit: `config(rabbitmq): externalize broker + topology in config server`

### P6.T10 — Full verify + Spotless + README + Turkish notes + tag
- `mvn spotless:apply && mvn clean verify`
- README: add notification-service row to module table, update Phase 6 status to ✅, add `Try It` curl with notification trace
- `docs/learning/phase-6-notes.md` with: AMQP vs Kafka, at-least-once, idempotent consumer, DLQ purpose, best-effort vs Outbox, interview Q&A
- `git tag phase-6-complete && git push origin phase-6-complete`
- Commits: chore(spotless), docs(README + phase-6-notes)

## Verification

1. `mvn clean verify` → 11 modules SUCCESS
2. `docker compose up -d` → all services healthy (postgres, redis, rabbitmq)
3. End-to-end smoke (manual, optional): boot all services + place order → see notification log line
4. Tag `phase-6-complete` on GitHub
