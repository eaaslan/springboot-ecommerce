# Quick Start — Run the System Locally

End-to-end "from clone to working order" walkthrough. ~10 minutes if Docker images are cold, ~3 minutes warm.

## Prerequisites

```bash
java --version    # 21
mvn --version     # 3.9+
docker --version  # 28+
jq --version      # any
```

## 1. Bring up infrastructure (Docker)

```bash
cd springboot-ecommerce
docker compose up -d postgres redis rabbitmq kafka prometheus grafana zipkin
docker compose ps
```

Wait until all containers show `(healthy)`:

```bash
docker compose ps --format 'table {{.Name}}\t{{.Status}}'
```

If postgres or redis is already running from a previous session and complains about port conflict, that's fine — `docker compose up -d` is idempotent.

## 2. Build everything once

```bash
mvn -B -DskipTests clean install
```

Takes ~1 minute on warm Maven cache. Verifies all 14 modules compile and produces the JARs.

## 3. Start the Spring services in order

Order matters: config-server → discovery-server → app services → api-gateway last.

Open **8 separate terminal tabs** (or use `tmux`), one per command:

```bash
# Tab 1 — Config Server (:8888)
mvn -pl infrastructure/config-server spring-boot:run

# Tab 2 — Discovery Server / Eureka (:8761)
mvn -pl infrastructure/discovery-server spring-boot:run

# Wait ~10s for the above two to register with each other...

# Tab 3 — User Service (:8081)
mvn -pl services/user-service spring-boot:run

# Tab 4 — Product Service (:8082)
mvn -pl services/product-service spring-boot:run

# Tab 5 — Cart Service (:8083)
mvn -pl services/cart-service spring-boot:run

# Tab 6 — Inventory + Payment + Order + Notification (run as separate tabs)
mvn -pl services/inventory-service spring-boot:run    # :8084
mvn -pl services/payment-service spring-boot:run      # :8085
mvn -pl services/order-service spring-boot:run        # :8086
mvn -pl services/notification-service spring-boot:run # :8087

# Tab 7 — Recommendation + Catalog Stream (optional)
mvn -pl services/recommendation-service spring-boot:run   # :8088
mvn -pl services/catalog-stream-service spring-boot:run   # :8089

# Tab 8 — API Gateway (:8080) — start this LAST
mvn -pl infrastructure/api-gateway spring-boot:run
```

> **Tip:** Spring Boot's `mvn spring-boot:run` is verbose. To get just the relevant service log, follow it after starting:
> ```bash
> tail -f services/order-service/target/spring.log
> ```

### Don't want to open 12 tabs?

Run only the **minimum subset** for the flow you care about:

| Goal | Required services |
|---|---|
| Login + browse catalog | config-server, discovery-server, user-service, product-service, api-gateway |
| Add to cart | + cart-service |
| Place order (full saga) | + inventory-service, payment-service, order-service |
| See notifications | + notification-service |
| Try recommendations / MCP | + recommendation-service |
| Try reactive endpoints | + catalog-stream-service |

## 4. Verify everything is up

```bash
curl http://localhost:8080/actuator/health/readiness
# → {"status":"UP"}

# Eureka registry (browser): http://localhost:8761
# Should list every running service
```

## 5. Run the smoke test

```bash
./scripts/smoke-test.sh
```

Expected output:
```
== 1. Health checks (gateway) ==
✓ gateway liveness (HTTP 200)
✓ gateway readiness (HTTP 200)

== 2. Auth: register (idempotent) + login ==
✓ register dispatched (smoke+1714652460@example.com)
✓ login → accessToken received (len=327)

== 3. Catalog (public) ==
✓ catalog list (HTTP 200)
✓ catalog by id (HTTP 200)

== 4. Cart ==
✓ cart add (HTTP 200)
✓ cart get → 1 item(s)

== 5. Orders — happy path ==
✓ place order (HTTP 201)
✓ order 42 is CONFIRMED

== 5b. Idempotency replay (same key, same body) ==
✓ replay returned SAME orderId (42) — Idempotency-Key works

...

  PASS: 16
  FAIL: 0
```

If anything fails, the script prints which step and the HTTP code received — `docker compose logs <container>` and the relevant service's stdout will tell you why.

## 6. Try Postman

```bash
open docs/postman/ecommerce.postman_collection.json
open docs/postman/ecommerce.postman_environment.json
```

Or in Postman: **Import** → drop both files → select "Springboot E-Commerce — Local" environment in the top-right dropdown. Run requests in folder order (0 → 1 → 2 → ...).

## 7. Open the dashboards (optional)

```bash
# Eureka registry — what services are alive?
open http://localhost:8761

# Prometheus — scrape targets, raw metrics
open http://localhost:9090/targets

# Grafana — Microservices Overview dashboard (admin/admin)
open http://localhost:3000

# Zipkin — distributed traces
open http://localhost:9411

# RabbitMQ management (guest/guest) — see queue depth + DLQ
open http://localhost:15672

# Swagger UI per service
open http://localhost:8081/swagger-ui.html  # user-service
open http://localhost:8082/swagger-ui.html  # product-service
# ... etc
```

## 8. Tear down

```bash
# Stop the spring services: Ctrl+C in each terminal tab
# Stop infrastructure:
docker compose down

# Wipe Postgres data too (rare; only if migrations are broken):
docker compose down -v
```

## Troubleshooting

| Symptom | Fix |
|---|---|
| Service won't register with Eureka | discovery-server not started or not yet ready — wait 30s and retry |
| `Connection refused` on port 8888 | config-server crashed; check logs in that tab |
| `bad credentials` on login | password wrong — register fresh user with smoke-test or use unique email |
| `429 Too Many Requests` | gateway rate limit (50 r/s, burst 100) — Redis must be running |
| Order stays PENDING forever | inventory or payment service down — check Eureka |
| `outbox_events` row stuck PENDING | Kafka not running — `docker compose up -d kafka` |
| Service `mvn spring-boot:run` slow first start | Maven downloading deps — second run is much faster |
| `Address already in use 808x` | Port collision — `lsof -i :808x` and kill, or change config |

## Useful one-liners

```bash
# All actuator/health/readiness in parallel
for p in 8080 8081 8082 8083 8084 8085 8086 8087 8088 8089 8761 8888; do
  printf '%5d %s\n' "$p" "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:$p/actuator/health/readiness 2>/dev/null || echo 'down')"
done

# Tail every service log at once (services must be running via spring-boot:run)
ls services/*/target/spring.log infrastructure/*/target/spring.log 2>/dev/null

# Force re-run the migrations (drop + recreate volume)
docker compose down -v && docker compose up -d postgres
```
