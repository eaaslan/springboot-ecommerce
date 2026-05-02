# Postman Collection

End-to-end Postman collection covering all 12 services through the API Gateway.

## Files

- `ecommerce.postman_collection.json` — 30+ requests, JWT auto-save, idempotency tests
- `ecommerce.postman_environment.json` — `baseUrl`, credentials, token storage

## Import

Postman → top-left **Import** → drop both JSON files. Select the "Springboot E-Commerce — Local" environment in the top-right dropdown.

## Run order

1. **0 — Health & Observability** → liveness/readiness should be 200
2. **1 — Auth** → Register (idempotent) → Login (saves token) → Me
3. **2 — Catalog** → list/by-id/categories
4. **3 — Cart** → Add item (qty 2) → Get cart
5. **4 — Orders** →
   - Place order (good card) → status CONFIRMED, saves `lastOrderId`
   - Idempotency replay → returns same `orderId`
   - Declining card (4111-1111-1111-**1115**) → CANCELLED, saga compensates
6. **5–7** — Inventory / Recommendations / MCP / Reactive catalog (public read)

## How auth works

- `Login` saves `accessToken` and `refreshToken` to environment via test script
- Every other request inherits collection-level Bearer auth (`{{accessToken}}`)
- Public endpoints (catalog, recommendations, MCP, reactive catalog) skip auth via the pre-request hook (matched by request name)

## Idempotency demo

The "Place order" request uses `{{$guid}}` for `Idempotency-Key` (Postman auto-generates a fresh UUID). Test script saves it as `lastIdempotencyKey`. The next request "Idempotency replay" reuses that key and asserts the response carries the **same `orderId`** — no double-charge.

## SSE caveat

The catalog stream endpoint (`/api/catalog/products/stream`) is Server-Sent Events. Postman doesn't render SSE natively — you'll get one chunk and close. For a proper demo:

```bash
curl -N 'http://localhost:8080/api/catalog/products/stream?intervalSeconds=2'
```

## Troubleshooting

- **Login 502/connection refused** → user-service or gateway not running. Check `docker compose ps` and service logs.
- **Login 401** → password wrong. Default seed user: `alice@example.com` / `password123` (created on register; idempotent on conflict).
- **Order 503** → cart-service / inventory-service / payment-service down. Check Eureka registry: <http://localhost:8761>.
- **429 Too Many Requests** → gateway rate limiter triggered (50 r/s, burst 100). Wait 2s and retry.
