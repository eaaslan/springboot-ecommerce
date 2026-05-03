# Springboot E-Commerce — Microservices

A production-grade Spring Boot 3 / Spring Cloud 2024 microservice e-commerce platform with a multi-seller marketplace, an order saga, an outbox-published event bus, an AI/MCP recommendation server, full observability, and a React + Vite storefront.

**Status:** 13 deployable services, 14 Maven modules, ~80 tests, full CI/CD via GitHub Actions, single-command deployment via `docker compose`.

> **Frontend repository:** [`eaaslan/springboot-ecommerce-frontend`](https://github.com/eaaslan/springboot-ecommerce-frontend) — React 18 + Vite, plain CSS. The compose stack here pulls and builds it automatically; you don't need to clone it.

---

## TL;DR — try it in one command

Every dependency lives inside containers. From a fresh clone:

```bash
git clone https://github.com/eaaslan/springboot-ecommerce.git
cd springboot-ecommerce
docker compose up --build
```

That's it. The first run builds 13 backend services and the React frontend
(~6–10 minutes on a fast laptop) and brings up Postgres, Redis, RabbitMQ,
Kafka, Prometheus, Grafana, and Zipkin. When everything reports healthy, open:

- **`http://localhost`** — the storefront (nginx serves the SPA + reverse-proxies `/api`)
- `http://localhost:8080/api/products` — gateway directly
- `http://localhost:8761` — Eureka registry
- `http://localhost:9090` / `http://localhost:3000` (admin/admin) / `http://localhost:9411` — Prometheus / Grafana / Zipkin

To populate the catalog with realistic demo data (60 products with images, 5 sellers, reviews, orders):

```bash
cd scripts/seed && npm install && node seed.js
```

Then log in at `http://localhost` as **`alice@example.com`** / **`password123`** (admin) or any seeded `buyer1..8@example.com` / `password123`.

**Stop:** `docker compose down`   **Stop and wipe data:** `docker compose down -v`

---

## Table of contents

- [What this project covers](#what-this-project-covers)
- [Architecture at a glance](#architecture-at-a-glance)
- [Quick start](#quick-start)
  - [Option A — One-command Docker stack (recommended)](#option-a--one-command-docker-stack-recommended)
  - [Option B — Hybrid: infra in Docker, services on host](#option-b--hybrid-infra-in-docker-services-on-host)
- [Service catalog](#service-catalog)
- [API gateway routes](#api-gateway-routes)
- [Marketplace (V1–V4)](#marketplace-v1v4)
- [Authentication flow](#authentication-flow)
- [Order saga walkthrough](#order-saga-walkthrough)
- [Demo data seeding](#demo-data-seeding)
- [Observability](#observability)
- [Configuration & profiles](#configuration--profiles)
- [Testing](#testing)
- [CI / CD](#ci--cd)
- [Production deployment notes](#production-deployment-notes)
- [Project structure](#project-structure)
- [Troubleshooting](#troubleshooting)
- [Roadmap](#roadmap)

---

## What this project covers

The platform is built to demonstrate end-to-end backend competence for an interview-grade or portfolio-grade project:

- **Bounded contexts as services** — one Spring Boot app per business capability, each with its own PostgreSQL or Redis store, all wired via Eureka + Spring Cloud Gateway
- **Distributed transaction (saga)** — the order saga reserves inventory, charges payment (Iyzico-shaped mock), commits the reservation, splits into per-seller sub-orders, and publishes an outbox event
- **Outbox + Kafka** — `outbox_events` is written in the same DB transaction as `OrderStatus.CONFIRMED`; a scheduled relay forwards rows to Kafka. `notification-service` consumes Kafka **and** RabbitMQ with idempotent dedup across both transports
- **Idempotency** — `Idempotency-Key` header replays cached responses for `POST /api/orders` so a double-tapped checkout never double-charges
- **Caching** — Caffeine on `product-service`, Redis-backed cart, automated cache eviction on writes
- **Marketplace** — multi-seller domain (V1) + listing-aware cart and orders (V2) + per-seller sub-orders with commission ledger (V3) + reviews, payouts, returns, and seller storefront (V4)
- **AI / MCP** — `recommendation-service` exposes Spring AI's MCP server over SSE so Claude / Cursor / Claude Code can call `searchProducts`, `similarProducts`, `recommendForUser` as tools
- **Reactive read facade** — `catalog-stream-service` (WebFlux + R2DBC + SSE) serves catalog reads + a streaming product feed
- **Observability** — Micrometer + Prometheus + Grafana for metrics, OpenTelemetry + Zipkin for traces, structured JSON logs with correlation IDs in `docker` profile
- **Production hardening** — JWT secret env injection, per-service health probes (liveness + readiness), graceful shutdown, gateway rate limiting (Redis token bucket), Spring Cloud Bus refresh hooks
- **Containerisation** — Jib for backend images (no Dockerfile required), multi-stage Docker for the frontend, GHCR-backed CD pipeline, single-command `docker-compose.prod.yml`

---

## Architecture at a glance

```
                                      Browser (http://localhost)
                                              │
                              ┌───────────────┴───────────────┐
                              │   nginx (frontend container)  │
                              │   serves /  → SPA static       │
                              │   proxies /api/*, /sse → 8080 │
                              └───────────────┬───────────────┘
                                              │
                                ┌─────────────▼─────────────┐
                                │  api-gateway (8080)       │
                                │  JWT validate, rate-limit │
                                │  inject X-User-Id/Role    │
                                └──────────────┬─────────────┘
                                               │   Eureka-resolved load balancing
        ┌────────────────────┬──────────┬──────┴──────┬──────────────┬──────────────────────┐
        ▼                    ▼          ▼             ▼              ▼                      ▼
┌──────────────┐    ┌──────────────┐  ┌──────┐  ┌─────────────┐  ┌──────────────┐    ┌──────────────────┐
│ user-service │    │ product-svc  │  │ cart │  │ order-svc   │  │ seller-svc   │    │ recommendation   │
│ 8081 / userdb│    │ 8082 / prodb │  │ 8083 │  │ 8086 /orderdb│  │ 8090 / sellerdb│  │ 8088 (in-mem)    │
│ JWT, BCrypt  │    │ Catalog +    │  │ Redis│  │ Saga, sub-  │  │ Sellers,     │    │ Content-based +  │
│              │    │ live stock + │  │      │  │ orders,     │  │ listings,    │    │ MCP/SSE server   │
│              │    │ buy-box enr. │  │      │  │ payouts,    │  │ reviews,     │    │                  │
│              │    │              │  │      │  │ returns     │  │ rating       │    │                  │
└──────────────┘    └──────┬───────┘  └──────┘  └──────┬──────┘  └──────────────┘    └──────────────────┘
                           │ Feign+R4j                  │ saga
                           ▼                            ▼
                    ┌──────────────┐             ┌──────────────┐
                    │ inventory    │  ◀─reserve──┤ payment      │  ◀ Iyzico mock
                    │ 8084 / invdb │      │      │ 8085 / paydb │
                    │ holds + cmts │      │      │ ledger       │
                    └──────────────┘      │      └──────────────┘
                                          │
                  ┌───────────────────────┴────────────────────────┐
                  │  Outbox row written in same TX as CONFIRMED    │
                  │  Scheduled relay → Kafka topic order.confirmed │
                  └───────────────────────┬────────────────────────┘
                                          ▼
                                   ┌────────────────┐
                                   │ notification   │
                                   │ 8087           │
                                   │ Kafka + Rabbit │
                                   │ idempotent     │
                                   └────────────────┘
```

Cross-cutting infrastructure: **config-server (8888)**, **discovery-server (Eureka, 8761)**, **postgres (5432)**, **redis (6379)**, **rabbitmq (5672/15672)**, **kafka (9092 KRaft)**, **prometheus (9090)**, **grafana (3000)**, **zipkin (9411)**.

---

## Running the stack

### Prerequisites

| Tool | When you need it |
|---|---|
| Docker 24+ with Compose v2 | Always |
| JDK 21, Maven 3.9+ | Only for the IDE / hot-reload dev loop |
| Node.js 20+ | Only to run the demo data seed |

That's it. Docker handles everything else (the Maven build runs *inside* the build container, the frontend repo is cloned by Buildx automatically).

### Default mode — full stack from one compose file

`docker-compose.yml` is self-contained. It builds every backend service from this repo via the root `Dockerfile`, and clones + builds the frontend straight from GitHub via Buildx git-context. No GHCR login, no sibling repo, no Maven on your machine.

```bash
git clone https://github.com/eaaslan/springboot-ecommerce.git
cd springboot-ecommerce

# First time: ~6–10 minutes (Maven dependency download + 13-service build).
docker compose up --build

# Open http://localhost when the logs settle (~60 s after the build finishes).
```

What's in there: 7 infrastructure containers (Postgres, Redis, RabbitMQ, Kafka, Prometheus, Grafana, Zipkin) + 13 Spring Boot services + the React/nginx frontend. Subsequent runs reuse layer cache and start in seconds.

```bash
docker compose down       # stop, keep data
docker compose down -v    # stop and wipe Postgres + Redis volumes
docker compose up         # restart (no rebuild needed if code unchanged)
docker compose up --build # restart and rebuild after code changes
```

To populate realistic demo data after the stack is up:

```bash
cd scripts/seed && npm install && node seed.js
# 60 products with images, 5 sellers, ~120 listings, ~30 reviews, ~4 orders
```

### Production deployment — pull pre-built images instead

`docker-compose.prod.yml` is the same topology but pulls images from GHCR instead of building locally — this is what you run on a real server once CI has published the images.

```bash
docker login ghcr.io -u <user> -p <PAT-with-read:packages>   # only if private
docker compose -f docker-compose.prod.yml up -d
```

### IDE / hot-reload mode — just the infra in Docker

`docker-compose.infra.yml` brings up only Postgres, Redis, Kafka, RabbitMQ, Prometheus, Grafana, Zipkin. Run the Spring services from your IDE for fast inner-loop development:

```bash
docker compose -f docker-compose.infra.yml up -d

./mvnw -pl infrastructure/config-server     spring-boot:run
./mvnw -pl infrastructure/discovery-server  spring-boot:run
./mvnw -pl services/user-service            spring-boot:run
# … and the rest, in any order

# Frontend in a separate terminal (separate repo, must be cloned)
cd ../springboot-ecommerce-frontend && npm install && npm run dev
# http://localhost:5173 with hot reload
```

---

## Service catalog

| Module | Port | DB / Store | Key responsibilities |
|---|---:|---|---|
| `infrastructure/config-server` | 8888 | classpath YAMLs | Centralised config; native filesystem backend, no Git required |
| `infrastructure/discovery-server` | 8761 | — | Eureka service registry |
| `infrastructure/api-gateway` | 8080 | Redis (rate limiter) | Spring Cloud Gateway, JWT validation, CORS, correlation IDs, public-route bypass |
| `services/user-service` | 8081 | `userdb` | Registration, login, refresh-token rotation, BCrypt hashing |
| `services/product-service` | 8082 | `productdb` | Catalog CRUD, pagination, Specification filters, **buy-box enrichment** via Feign to seller-service, Caffeine cache |
| `services/cart-service` | 8083 | Redis | Cart with 30-day TTL, **listing-aware add** (V2): if `listingId` is supplied, line locks to seller offer |
| `services/inventory-service` | 8084 | `inventorydb` | Stock + reservation state machine (`HELD`→`COMMITTED`/`RELEASED`), optimistic locking, **idempotent upsert endpoint** for new products |
| `services/payment-service` | 8085 | `paymentdb` | Iyzico-shaped charge / refund mock, audit ledger, decline simulation via card `4111-1111-1111-1115` |
| `services/order-service` | 8086 | `orderdb` | Saga orchestrator, **sub-order splitter** (V3) with per-seller commission, **payout batch + returns flow** (V4), outbox publisher |
| `services/notification-service` | 8087 | `notificationdb` | Async consumer over RabbitMQ + Kafka, dedup via `processed_events`, optional Slack webhook |
| `services/recommendation-service` | 8088 | in-memory + similarity matrix | Content-based scoring, MCP/SSE server (`searchProducts`, `similarProducts`, `recommendForUser`) |
| `services/catalog-stream-service` | 8089 | `productdb` (R2DBC) | WebFlux + R2DBC + SSE: reactive read API + streaming feed |
| `services/seller-service` | 8090 | `sellerdb` | **Marketplace domain**: seller applications + admin approval, listings (per-seller offers), reviews + recomputed rating |
| `shared/common` | — | — | API response envelope, correlation filter, AMQP event records, error model |

---

## API gateway routes

Every external call comes through `http://localhost:8080` (via the gateway) or `http://localhost` (via nginx → gateway). Internal Feign calls bypass the gateway and resolve via Eureka.

### Public — no auth (gateway forwards anonymously)

| Path | Service |
|---|---|
| `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/refresh` | user-service |
| `GET /api/products/**` | product-service |
| `GET /api/products/*/listings`, `GET /api/products/*/reviews` | seller-service |
| `GET /api/sellers/*/public`, `GET /api/sellers/*/listings`, `GET /api/sellers/*/reviews` | seller-service |
| `GET /api/listings/best` | seller-service |
| `GET /api/recommendations/**` | recommendation-service |
| `/sse/**` (MCP) | recommendation-service |
| `GET /api/catalog/**` | catalog-stream-service |
| `/actuator/**`, `/v3/api-docs/**`, `/swagger-ui/**` | each service |

### Authenticated (require `Authorization: Bearer <jwt>`)

| Path | Service |
|---|---|
| `GET/POST /api/cart/**` | cart-service |
| `POST /api/orders`, `GET /api/orders/**`, `POST /api/orders/sub-orders/*/return-request` | order-service |
| `GET /api/seller-orders/me`, `POST /api/seller-orders/*/return-decision` | order-service |
| `POST /api/sellers/apply`, `GET /api/sellers/me`, `PATCH /api/sellers/me` | seller-service |
| `POST /api/listings`, `GET /api/listings/me`, `PUT /api/listings/{id}`, `DELETE /api/listings/{id}` | seller-service |
| `POST /api/reviews` | seller-service |
| `POST /api/coupons/validate` | order-service |
| `GET /api/users/me` | user-service |

### Admin — require `role=ADMIN` claim

| Path | Service |
|---|---|
| `POST/PUT/DELETE /api/products/**` | product-service |
| `POST/PUT/DELETE /api/coupons/**` | order-service |
| `GET /api/sellers/admin?status=…`, `PATCH /api/sellers/admin/{id}` | seller-service |
| `POST /api/admin/payouts/run`, `GET /api/admin/payouts`, `POST /api/admin/payouts/*/mark-paid` | order-service |

---

## Marketplace (V1–V4)

The platform progressed through four marketplace milestones, each tagged in git:

### V1 — Seller domain (`phase-13-v1-complete`)

- `sellerdb` schema with `sellers` (status PENDING/ACTIVE/SUSPENDED, commission %, IBAN) and `listings` (per-seller offer, condition, shipping days)
- Apply → admin approve workflow
- **Buy-box algorithm:** `score = priceAmount + (shippingDays × 5)`, lower wins
- `product-service` enriches catalog responses with `bestListing` via Feign batch call to seller-service (graceful fallback to empty map if seller-service is down)

### V2 — Listing-aware cart + order (`phase-13-v2-complete`)

- `cart_items` and `order_items` carry nullable `listing_id`, `seller_id`, `seller_name`
- Cart locks the line to a seller when `listingId` is supplied; backwards compatible when null
- `seller-service` exposes public `GET /api/listings/{id}` for cart-service Feign lookup

### V3 — Sub-orders + commission ledger (`phase-13-v3-complete`)

- Flyway V7: `sub_orders(order_id, seller_id, subtotal_amount, commission_pct, commission_amount, payout_amount, status, …)` + `order_items.sub_order_id` FK
- `SubOrderSplitter` runs inside the saga **before** the final `CONFIRMED` save so `sub_order_id` cascades onto each line in the same flush
- Per-seller commission with platform default 8%, 0% for the platform bucket
- `GET /api/seller-orders/me` lets a seller list their incoming sub-orders

### V4 — Reviews, payouts, returns, storefront (`phase-13-v4-complete`)

- **Real per-seller commission lookup** — `SellerCommissionClient` Feign + `SellerPublicResponse` exposes `commissionPct`; falls back to default 8% if unreachable
- **Public seller storefront** — `GET /api/sellers/{id}/listings` powers `/sellers/:id` page
- **Reviews** — Flyway V2 in `sellerdb`, upsert on `UNIQUE(user_id, seller_id, product_id)`, seller `rating` recomputed Java-side after every review
- **Payouts** — Flyway V8 in `orderdb` adds `seller_payouts` (UNIQUE on `seller_id, period_start, period_end` for run-idempotency); admin endpoints aggregate `PENDING` sub_orders into a single payout row, mark sub_orders as paid via `payout_id` FK
- **Returns** — status-only state machine on `sub_orders.status`: `PENDING → RETURN_REQUESTED → REFUNDED | RETURN_REJECTED`; blocked once `payout_id` is set

---

## Authentication flow

1. Client `POST /api/auth/login` → user-service verifies BCrypt hash → returns `{accessToken, refreshToken}`
2. Client stores both in localStorage; every request sends `Authorization: Bearer <accessToken>`
3. **Gateway validates** the JWT signature, parses `sub` (user id) + `role` claims, injects `X-User-Id` / `X-User-Role` headers, strips the `Authorization` header (defence-in-depth: downstream services can't forge tokens)
4. Each downstream service has a `HeaderAuthenticationFilter` that reads `X-User-Id` and builds a Spring Security `Authentication` with `(Long userId, role)` as the principal
5. **Refresh-token rotation** (frontend `client.js`): on 401, automatically `POST /api/auth/refresh` with the refresh token, save the new tokens, retry the original request. Single-flight: parallel 401s share the same refresh promise.
6. The gateway's `UserHeaderForwardingInterceptor` ensures Feign calls between services carry `X-User-Id` so downstream services see the *original* user.

---

## Order saga walkthrough

`POST /api/orders` triggers `OrderService.placeOrderInternal`:

```
1.  Fetch cart from cart-service (Feign)        ─ fails → "Cart is empty"
2.  Validate coupon (if any) — fail-fast        ─ fails → 400, no order written
3.  Persist Order PENDING                       ─ failure_reason on cancel
4.  Reserve inventory for each line             ─ fail → release any HELD, mark CANCELLED
5.  Charge payment (Iyzico mock)                ─ fail → release HELD + mark CANCELLED
6.  Commit reservations                         ─ fail → refund payment + release + mark CANCELLED
7.  Split into sub-orders (V3)                  ─ commission lookup → SubOrder rows
8.  Mark CONFIRMED + write outbox row           ─ same TX as the order save
9.  Direct AMQP publish (best-effort)           ─ low-latency Phase 6 path
10. Clear cart                                   ─ best-effort, logs on failure
```

The outbox event is later picked up by a scheduled `OutboxRelay` (every 1 s) and pushed to the `order.confirmed` Kafka topic. `notification-service` consumes both AMQP and Kafka with idempotent dedup so an event is only delivered once even if both transports fire.

---

## Demo data seeding

To populate the DB with realistic users, products, images, listings, reviews, and orders:

```bash
cd scripts/seed
npm install                                     # one-time

# Default — 8 buyers, 5 sellers, 60 products with images, ~120 listings, ~30 reviews, ~4 orders
node seed.js

# Larger demo
node seed.js --products 120 --buyers 20 --sellers 8

# Skip the slow order-placement step
node seed.js --no-orders
```

What it creates:

| Resource | Count (default) | Notes |
|---|---|---|
| Buyer accounts | 8 | `buyer1..8@example.com` / `password123` |
| Seller accounts | 5 | `seller1..5@example.com`, applied + auto-approved by alice, **per-seller commission 5–12 %** |
| Master products | 60 | spread across the 5 categories, names from realistic templates, descriptions via Faker |
| Product images | 60 | `https://picsum.photos/seed/<SKU>/600/600` — stable per SKU, varied across products |
| Listings | ~80–130 | each seller lists 30–55 % of products at ±15 % of master price |
| Reviews | ~24–48 | each buyer drops 3–6 reviews; seller rating recomputed by the service |
| Orders | half of buyers | each places one order via the full saga |
| Inventory rows | matching | auto-upserted via `POST /api/inventory/items` for every new product |

After a partial run or to add images to V1 seed products:

```bash
node backfill-images.js     # picsum URL for any product missing imageUrl
node backfill-inventory.js  # inventory row for any product missing one
```

---

## Observability

After Option A or B is up:

| URL | What |
|---|---|
| `http://localhost:9090` | Prometheus targets page (every service should be UP) |
| `http://localhost:3000` (admin/admin) | Grafana, dashboard "Microservices Overview" provisioned automatically |
| `http://localhost:9411` | Zipkin distributed traces (gateway → Feign → Kafka spans) |
| `http://localhost:15672` (guest/guest) | RabbitMQ management UI |
| `http://localhost:8761` | Eureka registry — see every service's heartbeat |
| `http://localhost:8888/<service-name>/<profile>` | Config-server raw YAML response |
| `http://<service>/actuator/prometheus` | Per-service Prometheus scrape endpoint |
| `http://<service>/actuator/health` | Liveness + readiness composite |

Each service emits the standard Micrometer suite plus a few business-level counters (`orders.placed`, `orders.cancelled` with reason tag, `coupon.redeemed`, etc.).

---

## Configuration & profiles

Three Spring profiles are baked in:

| Profile | Logging | Database / cache hosts | Use |
|---|---|---|---|
| `dev` (default) | Plain text, debug-leaning | `localhost` | Hybrid Option B |
| `docker` | JSON, info-leaning | container DNS (`postgres`, `redis`, `kafka`, …) | Option A (compose) |
| `prod` | JSON, warn-leaning | from env / config-server | Real deployment |

YAML lives in `infrastructure/config-server/src/main/resources/configs/<service-name>[-profile].yml`. Each Spring service has `spring.config.import=optional:configserver:http://localhost:8888` so it pulls config on boot.

Override secrets at the host level:

```bash
JWT_SECRET=$(openssl rand -hex 32) \
SPRING_PROFILES_ACTIVE=docker \
  docker compose -f docker-compose.prod.yml up -d
```

---

## Testing

```bash
./mvnw test                                          # all unit + integration tests
./mvnw -pl services/order-service test               # one service
./mvnw verify                                        # CI gate (Spotless + tests)
./mvnw -DskipTests verify                            # just compile + Spotless
./scripts/smoke-test.sh                              # end-to-end smoke against a running stack
```

Unit tests use `@MockitoExtension`; integration tests stand up a real Postgres via Testcontainers when needed. The CI workflow at `.github/workflows/ci.yml` runs `mvn verify` on every push / PR.

---

## CI / CD

- `.github/workflows/ci.yml` — every push / PR runs `mvn verify` (Spotless gate inside)
- `.github/workflows/cd.yml` — on push to `main`, builds and pushes all backend images to GHCR via Jib (`ghcr.io/<owner>/ecommerce-<artifact-id>:{sha,latest}`); uses `GITHUB_TOKEN`, no PAT required

The frontend repository has its own CD workflow that builds the multi-stage Dockerfile and pushes `ghcr.io/<owner>/ecommerce-frontend:{sha,latest}`.

After both CI runs complete on `main`, **`docker compose -f docker-compose.prod.yml up -d` pulls everything from GHCR**.

---

## Production deployment notes

For an actual VM deploy (Oracle Cloud Ampere A1, AWS EC2, Hetzner, …):

1. **VM:** at least 4 GB RAM (8 GB comfortable for the full stack), 30 GB disk, Ubuntu 22.04. Ampere ARM64 works because every image is multi-arch.
2. **Install Docker + Compose v2**: `curl -fsSL https://get.docker.com | sh; sudo usermod -aG docker $USER`
3. **Clone the repo and bring up the stack** with one command (the same one you'd run locally):
   ```bash
   git clone https://github.com/eaaslan/springboot-ecommerce.git
   cd springboot-ecommerce
   echo "JWT_SECRET=$(openssl rand -hex 32)" > .env
   docker compose --env-file .env up -d --build
   ```
4. Or **pull pre-built images** from GHCR (faster on the server, requires `docker login ghcr.io`):
   ```bash
   docker compose -f docker-compose.prod.yml --env-file .env up -d
   ```
5. **TLS:** put Caddy in front of the in-stack nginx (port 80) for automatic Let's Encrypt:
   ```caddyfile
   shop.example.com { reverse_proxy localhost:80 }
   ```
6. **Backups:** cron a daily `docker exec ... pg_dumpall` on the `pg-data` volume.
7. **Optional:** `SLACK_ENABLED=true SLACK_WEBHOOK_URL=...` to wire order-confirmation notifications.

The full operator checklist lives in [`docs/production-hardening.md`](docs/production-hardening.md): secrets, JWT rotation, dependency scanning, observability rules, runbooks.

### AWS Elastic Beanstalk

A ready-to-deploy bundle lives under [`aws/`](aws/) — `docker-compose.yml` against
RDS + ElastiCache, `.ebextensions/` hook to bootstrap per-service databases,
end-to-end walkthrough in [`aws/AWS-DEPLOY.md`](aws/AWS-DEPLOY.md):

```bash
cd aws
eb init ecommerce --platform "Docker running on 64bit Amazon Linux 2023"
eb create ecommerce-prod --instance-type t3.large --single
eb setenv POSTGRES_HOST=… REDIS_HOST=… JWT_SECRET=… IMAGE_TAG=latest
eb deploy
```

### Slack notifications

Set `SLACK_WEBHOOK_URL` repository secret and every successful or failed CD run
posts a notification with commit sha, author, and a link to the run. Quiet
when the secret is unset — no error, just skips.

---

## Project structure

```
springboot-ecommerce/
├── docker-compose.yml              # Default: build everything from source (one command)
├── docker-compose.prod.yml         # Same topology but pulls images from GHCR
├── docker-compose.infra.yml        # Just infra (for IDE / hot-reload dev loop)
├── Dockerfile                      # Generic backend image template — MODULE arg picks the service
├── pom.xml                         # Multi-module Maven root
├── docker/
│   ├── postgres/init.sql           # Per-service DB + user creation
│   ├── prometheus/prometheus.yml   # Scrape config
│   └── grafana/                    # Provisioned dashboard + datasource
├── infrastructure/
│   ├── api-gateway/                # Spring Cloud Gateway (reactive)
│   ├── config-server/              # Native fs config server
│   └── discovery-server/           # Eureka
├── services/
│   ├── user-service/               # JWT auth, BCrypt
│   ├── product-service/            # Catalog + buy-box enrichment
│   ├── cart-service/               # Redis cart + listing lookup
│   ├── inventory-service/          # Stock + reservations
│   ├── payment-service/            # Iyzico-shaped mock
│   ├── order-service/              # Saga + outbox + sub-orders + payouts + returns
│   ├── notification-service/       # AMQP + Kafka consumer, idempotent
│   ├── recommendation-service/     # Content-based + MCP / SSE
│   ├── catalog-stream-service/     # WebFlux + R2DBC + SSE
│   └── seller-service/             # Marketplace: sellers, listings, reviews
├── shared/common/                  # Library JAR (response envelope, errors, etc.)
├── scripts/
│   ├── smoke-test.sh               # End-to-end smoke against running stack
│   └── seed/                       # Demo-data seed (Node + Faker + Picsum)
├── docs/
│   └── superpowers/                # Phase specs + plans
└── .github/workflows/              # CI + CD
```

Frontend lives in a separate repository as documented at the top of this file.

---

## Troubleshooting

**`docker compose up` fails to pull `ghcr.io/eaaslan/ecommerce-seller-service:latest`**
The marketplace branch hasn't been merged to `main` yet, so CD hasn't published that image. Build it locally with Jib (see Option A.1) or merge the branch.

**Frontend boots but every API call returns 502**
Check `docker compose ps`: if `api-gateway` is `unhealthy`, look at the gateway's `/actuator/health/readiness`. The health probe gates the frontend's `depends_on`, so the SPA won't start serving until the gateway is ready.

**Postgres rejects new database / user**
The `docker/postgres/init.sql` script only runs on **first volume initialization**. If you've already booted Postgres, run `docker compose -f docker-compose.prod.yml down -v` to drop the `pg-data` volume so init runs again. (You will lose all data.)

**`bestListing` is null on every product**
Either seller-service is down (check `http://localhost:8090/actuator/health`) or no listings exist for the products yet — run `node scripts/seed/seed.js` to populate.

**Eureka takes ~30 s after a service restart before the gateway routes to it**
This is the default registry refresh interval. Be patient or set `eureka.client.registry-fetch-interval-seconds: 5` in `api-gateway-dev.yml` for faster local dev.

**Kafka image pull fails**
The old `bitnami/kafka:3.7` was removed from Docker Hub. Both compose files now use `apache/kafka:3.8.0` in KRaft mode. Pull errors usually mean a stale local compose: `docker compose pull` to refresh.

**Login works in the SPA but every authenticated request 401s**
The gateway issues a CORS-aware response only for `OPTIONS` preflight; cookies / JWT in `Authorization` flow on the actual GET / POST. Make sure the SPA was built with the right `VITE_API_URL` (or with an empty value, in which case nginx proxying must be in front of it).

---

## Roadmap

| Phase | Theme | Status |
|---|---|---|
| 0 | Microservice Foundation | ✅ |
| 1 | User Service + JWT auth + Swagger | ✅ |
| 2 | Product Service (PostgreSQL, pagination) | ✅ |
| 3 | Inter-service communication (Feign, Resilience4j) | ✅ |
| 4 | Cart Service Redis backend (profile-based store, 30-day TTL) | ✅ |
| 5 | Order + Inventory + Payment (Saga, Iyzico mock) | ✅ |
| 6 | Notification Service (RabbitMQ + DLQ + idempotent consumer) | ✅ |
| 7 | Event bus (Kafka + Outbox pattern, idempotent producer) | ✅ |
| 8 | Observability (Prometheus + Grafana + Zipkin, Micrometer + OTel) | ✅ |
| 9 | Recommendation Service + MCP AI Server (Spring AI) | ✅ |
| 10 | Reactive layer (WebFlux + R2DBC + SSE) | ✅ |
| 11 | Idempotency-Key, Caffeine cache, gateway rate limit, K8s probes | ✅ |
| 12 | Production deployment (Jib multi-arch, GitHub Actions CI/CD → GHCR) | ✅ |
| 13.V1 | Marketplace V1 — seller domain + buy-box | ✅ |
| 13.V2 | Marketplace V2 — listing-aware cart + order | ✅ |
| 13.V3 | Marketplace V3 — sub-orders + commission ledger | ✅ |
| 13.V4 | Marketplace V4 — reviews, payouts, returns, seller storefront | ✅ |
| 14 (next) | Bank API integration for real payouts; dispute escalation | ⏳ |
