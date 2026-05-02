# Springboot E-Commerce — Microservices

Spring Boot 3 / Spring Cloud 2024.0 microservice e-commerce, designed to cover backend interview topics: persistence, caching, async messaging, observability, distributed transactions, and AI/MCP integration.

## Module Layout

| Module | Type | Port | Role |
|---|---|---:|---|
| `infrastructure/config-server` | Spring Boot | 8888 | Centralized config (native fs) |
| `infrastructure/discovery-server` | Spring Boot | 8761 | Eureka service registry |
| `infrastructure/api-gateway` | Spring Boot (reactive) | 8080 | Routing, CORS, JWT validation, correlation IDs |
| `services/user-service` | Spring Boot | 8081 | JWT auth, BCrypt, PostgreSQL |
| `services/product-service` | Spring Boot | 8082 | Catalog, pagination, Specification, admin CRUD |
| `services/cart-service` | Spring Boot | 8083 | Cart with Redis backend (in-memory under `test` profile), Feign + Resilience4j |
| `services/inventory-service` | Spring Boot | 8084 | Stock + reservations (HELD/COMMITTED/RELEASED), optimistic locking |
| `services/payment-service` | Spring Boot | 8085 | Iyzico-shaped payment mock, refund support, audit ledger |
| `services/order-service` | Spring Boot | 8086 | Saga orchestrator + Outbox publisher (atomic event write, scheduled Kafka relay) |
| `services/notification-service` | Spring Boot | 8087 | Async notification consumer (RabbitMQ + Kafka, idempotent dedup across both transports) |
| `shared/common` | Library JAR | — | Response envelopes, exception model, correlation filters, AMQP event records |

## Prerequisites

- JDK 21 (Temurin or Homebrew OpenJDK 21)
- Maven 3.9+
- Docker + Docker Compose (for PostgreSQL)

## Build

```bash
./mvnw clean verify
```

## Run

```bash
docker compose up -d postgres redis rabbitmq kafka prometheus grafana zipkin
./mvnw -pl infrastructure/config-server spring-boot:run
./mvnw -pl infrastructure/discovery-server spring-boot:run
./mvnw -pl services/user-service spring-boot:run
./mvnw -pl services/product-service spring-boot:run
./mvnw -pl services/cart-service spring-boot:run
./mvnw -pl services/inventory-service spring-boot:run
./mvnw -pl services/payment-service spring-boot:run
./mvnw -pl services/order-service spring-boot:run
./mvnw -pl services/notification-service spring-boot:run
./mvnw -pl infrastructure/api-gateway spring-boot:run
```

## Try It

```bash
# Public catalog
curl 'http://localhost:8080/api/products?page=0&size=5'

# Register + login
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"password123"}'

LOGIN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"password123"}')
ACCESS=$(echo "$LOGIN" | jq -r '.data.accessToken')

curl http://localhost:8080/api/users/me -H "Authorization: Bearer $ACCESS"

# Add to cart (requires Bearer)
curl -X POST http://localhost:8080/api/cart/items \
  -H "Authorization: Bearer $ACCESS" \
  -H "Content-Type: application/json" \
  -d '{"productId":1,"quantity":2}'

# Get current user's cart
curl http://localhost:8080/api/cart -H "Authorization: Bearer $ACCESS"

# Place order (Saga: reserve inventory → charge → commit → confirm → clear cart)
curl -X POST http://localhost:8080/api/orders \
  -H "Authorization: Bearer $ACCESS" \
  -H "Content-Type: application/json" \
  -d '{"card":{"holderName":"Alice","number":"4111111111111111","expireMonth":"12","expireYear":"2030","cvc":"123"}}'

# Use card 4111-1111-1111-1115 to force a payment decline (compensation kicks in)

# Outbox pattern: order-service writes outbox_events row in same TX as CONFIRMED.
# Scheduled OutboxRelay (every 1s) publishes PENDING rows to Kafka topic `order.confirmed`.
# notification-service consumes both AMQP and Kafka — idempotent processed_events table dedups.
# Watch its log: "Notification SENT — eventId=... orderId=... channel=EMAIL"
# RabbitMQ management UI: http://localhost:15672 (guest/guest)
# Kafka broker: localhost:9092 (KRaft single-node)

# Observability stack:
# - Prometheus: http://localhost:9090  (targets page shows all services UP)
# - Grafana:    http://localhost:3000  (admin/admin — provisioned dashboard "Microservices Overview")
# - Zipkin:     http://localhost:9411  (distributed traces; gateway → services → kafka)
# - Each service: /actuator/prometheus + /actuator/health
```

## Profiles

| Profile | Logging | Service URLs |
|---|---|---|
| `dev` (default) | Plain text | `localhost` |
| `docker` | JSON | Service-name DNS |
| `prod` | JSON | Production DNS |

## Roadmap

| Phase | Theme | Status |
|---|---|---|
| 0 | Microservice Foundation | ✅ |
| 1 | User Service + JWT auth + Swagger | ✅ |
| 2 | Product Service (PostgreSQL, pagination) | ✅ |
| 3 | Inter-service communication (Feign, Resilience4j) — Cart Service in-memory | ✅ |
| 4 | Cart Service Redis backend (profile-based store, 30-day TTL) | ✅ |
| 5 | Order + Inventory + Payment (Saga, Iyzico mock) | ✅ |
| 6 | Notification Service (RabbitMQ + DLQ + idempotent consumer) | ✅ |
| 7 | Event bus (Kafka + Outbox pattern, idempotent producer, parallel Kafka consumer) | ✅ |
| 8 | Observability (Prometheus + Grafana + Zipkin, Micrometer + OTel, RED metrics, business counters) | ✅ |
| 9 | Recommendation / MCP AI Server | upcoming |
| 10 | Reactive layer (WebFlux) | upcoming |
| 11 | Performance + production-readiness | upcoming |
| 12 | Production deployment (Oracle Cloud Ampere A1, Jib, Slack) | upcoming |
