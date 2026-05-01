# Phase 2 — Product Service Design

**Status:** Implemented
**Phase:** 2 of 12

## 1. Overview

`services/product-service` (port 8082) — product catalog, paginated/filterable list, admin CRUD. Trusts gateway-supplied `X-User-Id` / `X-User-Role`. Demonstrates JPA Specification API + `@EntityGraph` for N+1 mitigation.

### Goals
- Paginated listing with filters (name LIKE, categoryId, price range, in-stock)
- Public read (`GET /api/products/**`), admin-only write (`POST/PUT/DELETE`)
- Soft-delete via `enabled=false`
- 5 categories + 20 products seeded via Flyway V2
- `Product → Category` lazy `@ManyToOne` with `@EntityGraph` override on `findAll(Specification, Pageable)` for one-query loading
- Swagger per-service

### Non-goals
- MongoDB hybrid (Phase 9 with search/AI)
- Inventory service (Phase 5)
- Image upload (out of scope; products carry `image_url` string)

## 2. Trust model

Gateway validates JWT once, forwards `X-User-Id` + `X-User-Role` via `HeaderAuthenticationFilter`. Product service does NOT re-parse JWT. Internal traffic is on a private network; gateway strips inbound `X-User-*` headers from public requests.

Defense-in-depth note: production-grade hardening uses service mesh mTLS (Istio) — not in scope here.

## 3. Domain model (DDL)

```sql
CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    slug VARCHAR(120) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    sku VARCHAR(60) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    image_url VARCHAR(500),
    price_amount NUMERIC(12,2) NOT NULL,
    price_currency VARCHAR(3) NOT NULL DEFAULT 'TRY',
    stock_quantity INTEGER NOT NULL DEFAULT 0,
    category_id BIGINT NOT NULL REFERENCES categories(id),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);
```

`price_currency` is `VARCHAR(3)` (not `CHAR(3)`) — Postgres returns CHAR as `bpchar` which fails Hibernate's varchar validation, and VARCHAR avoids space padding.

## 4. Endpoints

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/products` | Public | Paginated, filterable; default 20, max 100 |
| GET | `/api/products/{id}` | Public | Returns product + category eagerly loaded |
| GET | `/api/products/categories` | Public | All categories |
| POST | `/api/products` | ADMIN | Create |
| PUT | `/api/products/{id}` | ADMIN | Patch (only non-null fields) |
| DELETE | `/api/products/{id}` | ADMIN | Soft-delete (`enabled=false`) |

### Listing query parameters

`?name=phone&categoryId=1&minPrice=500&maxPrice=20000&inStock=true&page=0&size=20&sortBy=priceAmount&sortDir=asc`

- All filters optional; combined via Specification AND
- Sort columns whitelisted to `id, name, priceAmount, createdAt` (injection defense)
- Page size capped at 100
- Response uses custom `PageResponse<T>` envelope (not Spring's `Page` JSON)

## 5. Repository

`ProductRepository extends JpaSpecificationExecutor<Product>`. Overrides `findAll(Specification, Pageable)` with `@EntityGraph(attributePaths = "category")` so the listing path is a single SQL with LEFT JOIN — solves N+1 for the listing endpoint.

`findWithCategoryById(id)` similarly entity-graphed for detail page.

## 6. Security

- `HeaderAuthenticationFilter` reads `X-User-Id` + `X-User-Role`, builds `UsernamePasswordAuthenticationToken` with `ROLE_<role>`
- `SecurityConfig`: GET `/api/products/**` permitAll, others require auth, `@PreAuthorize("hasRole('ADMIN')")` on writes
- Custom 401 + 403 entrypoints return JSON in shared `ErrorResponse` shape

## 7. Acceptance criteria

- [x] `mvn clean verify` green for whole reactor (6 modules)
- [x] `GET /api/products?page=0&size=5` returns 5 items + paging metadata
- [x] `?categoryId=1` returns only that category
- [x] `GET /api/products/{id}` includes `category` populated (one SQL via entity graph, asserted in test)
- [x] `POST /api/products` without admin → 403; without auth → 401
- [x] `DELETE` soft-deletes (row stays, `enabled=false`)
- [x] Duplicate SKU → 409
- [x] Swagger UI on :8082

## 8. Interview topics unlocked

N+1 + `@EntityGraph` vs `JOIN FETCH`, Specification API vs Criteria vs `@Query`, pagination offset vs cursor, sort whitelist as injection defense, header-trust model vs JWT-everywhere, soft-delete patterns, database-per-service, `open-in-view: false` rationale, BigDecimal precision/scale, immutable records as DTOs.
