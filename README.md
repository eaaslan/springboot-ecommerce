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
| `services/cart-service` | Spring Boot | 8083 | In-memory cart, Feign client to product-service with Resilience4j |
| `shared/common` | Library JAR | — | Response envelopes, exception model, correlation filters |

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
docker compose up -d postgres
./mvnw -pl infrastructure/config-server spring-boot:run
./mvnw -pl infrastructure/discovery-server spring-boot:run
./mvnw -pl services/user-service spring-boot:run
./mvnw -pl services/product-service spring-boot:run
./mvnw -pl services/cart-service spring-boot:run
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
| 4 | Cart Service Redis backend (swap InMemoryCartStore → RedisCartStore) | upcoming |
| 5 | Order + Inventory + Payment (Saga, Iyzico) | upcoming |
| 6 | Notification Service (RabbitMQ) | upcoming |
| 7 | Event bus (Kafka, Outbox pattern) | upcoming |
| 8 | Observability (Prometheus, Grafana, Zipkin) | upcoming |
| 9 | Recommendation / MCP AI Server | upcoming |
| 10 | Reactive layer (WebFlux) | upcoming |
| 11 | Performance + production-readiness | upcoming |
| 12 | Production deployment (Oracle Cloud Ampere A1, Jib, Slack) | upcoming |
