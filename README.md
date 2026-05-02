# Springboot E-Commerce — Microservices

Spring Boot 3 / Spring Cloud 2024.0 microservice e-commerce, designed to cover backend interview topics: persistence, caching, async messaging, observability, distributed transactions, and AI/MCP integration.

**Status: 12/12 phases complete** ✅ — 14 Maven modules, 12 deployable services, ~80 tests, full CI/CD via GitHub Actions, ready to deploy on Oracle Cloud Ampere A1 free-tier (4 OCPU + 24 GB RAM ARM64).

> **Production deployment:** see [`docs/production-hardening.md`](docs/production-hardening.md) for the full operator checklist (secrets, TLS, JWT rotation, dependency scanning, Oracle Cloud walkthrough, backup, observability rules, runbooks).

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
| `services/recommendation-service` | Spring Boot | 8088 | Content-based recommender + MCP server (Spring AI) for AI agents |
| `services/catalog-stream-service` | Spring Boot | 8089 | Reactive read facade (WebFlux + R2DBC + SSE) over productdb |
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
./mvnw -pl services/recommendation-service spring-boot:run
./mvnw -pl services/catalog-stream-service spring-boot:run
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

# Idempotency-Key replay (Phase 11): same UUID + same payload = same response, no double-charge
KEY=$(uuidgen)
curl -X POST http://localhost:8080/api/orders -H "Authorization: Bearer $ACCESS" \
  -H "Idempotency-Key: $KEY" -H "Content-Type: application/json" \
  -d '{"card":{"holderName":"Alice","number":"4111111111111111","expireMonth":"12","expireYear":"2030","cvc":"123"}}'
# Re-running with same KEY returns the cached response (verify same orderId)

# Rate limiting: rapid-fire 200 reqs → some 429 Too Many Requests
for i in {1..200}; do curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/products; done | sort | uniq -c

# Liveness vs readiness probes (Phase 11)
curl http://localhost:8086/actuator/health/liveness
curl http://localhost:8086/actuator/health/readiness

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

# Recommendations (public read paths — JWT bypassed at gateway)
curl 'http://localhost:8080/api/recommendations/products/1/similar?k=3'
curl 'http://localhost:8080/api/recommendations/search?q=wireless&limit=5'

# MCP server — wire up as Claude Desktop / Cursor / Claude Code tool
# claude_desktop_config.json:
# { "mcpServers": { "ecommerce": { "url": "http://localhost:8088/mcp" } } }
# Tools auto-listed: similarProducts, recommendForUser, searchProducts

# Reactive read facade (WebFlux + R2DBC) — public read paths
curl 'http://localhost:8080/api/catalog/products?page=0&size=5'
curl 'http://localhost:8080/api/catalog/products/search?q=wireless'
# Server-Sent Events (keeps connection open, emits products every 2s)
curl -N 'http://localhost:8080/api/catalog/products/stream?intervalSeconds=2'
```

## Deploy (Production-like)

### Build images locally (Jib, no Dockerfile)

```bash
# Build one service into local Docker daemon
mvn -DskipTests -pl services/order-service compile jib:dockerBuild

# Build all images and push to a registry (CI does this)
mvn -DskipTests -Djib.to.auth.username=$USER -Djib.to.auth.password=$TOKEN compile jib:build
```

### Run the production-like stack from GHCR

```bash
# Once CD has pushed images to ghcr.io/eaaslan/ecommerce-*:latest
docker login ghcr.io -u <your-github-user> -p <PAT-with-read:packages>
docker compose -f docker-compose.prod.yml up -d
```

### CI/CD (GitHub Actions)

- `.github/workflows/ci.yml` — every push/PR runs `mvn verify` (Spotless gate included)
- `.github/workflows/cd.yml` — main branch builds + pushes images to GHCR via Jib (uses `GITHUB_TOKEN`, no PAT needed)

### Slack notifications (opt-in)

```bash
SLACK_ENABLED=true SLACK_WEBHOOK_URL=https://hooks.slack.com/services/... \
  docker compose -f docker-compose.prod.yml up -d notification-service
# Now order CONFIRMED events post to your Slack channel.
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
| 9 | Recommendation Service + MCP AI Server (Spring AI, content-based scoring) | ✅ |
| 10 | Reactive layer (WebFlux + R2DBC + SSE — `catalog-stream-service`) | ✅ |
| 11 | Performance + production-readiness (Idempotency-Key, Caffeine cache, gateway rate limit, graceful shutdown, K8s probes) | ✅ |
| 12 | Production deployment (Jib multi-arch, GitHub Actions CI/CD → GHCR, docker-compose.prod, Slack webhook, Oracle Cloud walkthrough) | ✅ |
