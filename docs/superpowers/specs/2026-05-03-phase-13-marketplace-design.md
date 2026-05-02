# Phase 13 — Marketplace Transformation (n11/Trendyol Model)

> Goal: turn the single-vendor platform into a **multi-seller marketplace** where independent sellers list their own offers against a shared product catalog. Same SKU sold by N sellers at different prices/stocks. Customer cart can mix sellers. Orders split per seller. Platform takes commission, settles payouts.
>
> **Estimated effort: 3-4 weeks** broken into 4 vertical slices (V1 → V4).

---

## 1. Why This Is Big

The current system is **single-vendor**. Every entity carries an implicit "the platform sells everything" assumption:

| Concept | Today (single-vendor) | Marketplace (n11) |
|---|---|---|
| Product | one row, one price, one stock | catalog row + N "listings" (one per seller) |
| Inventory | per `productId` | per `(productId, sellerId)` |
| Order | one order, one fulfillment | one order with N sub-orders (one per seller) |
| Payment | one charge → platform | one charge → escrow → split payout to sellers (minus commission) |
| Reviews | (we don't have yet) | per-product + per-seller |

Every read path stays roughly compatible (catalog still shows products), but **every write path** changes shape: cart add now picks a listing, order saga loops per seller, payment splits per seller.

**Core architectural decision: keep V1 scope small.** Most "advanced" marketplace features (returns, disputes, seller-funded ads, dynamic buy-box pricing) are deferred to V4+ or out-of-scope.

---

## 2. Domain Model

### Master product (existing, unchanged shape)

```
products (id, sku, name, description, category_id, image_url, enabled)
```

**Decision:** product is now **only catalog metadata** — no price, no stock anymore on this row (or kept as legacy default/fallback only). Sellers control price + stock via listings.

### NEW: sellers

```sql
CREATE TABLE sellers (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL UNIQUE REFERENCES users(id),    -- existing user gets seller role
    business_name   VARCHAR(200) NOT NULL,
    tax_id          VARCHAR(40),
    iban            VARCHAR(40),
    contact_email   VARCHAR(120),
    contact_phone   VARCHAR(40),
    commission_pct  NUMERIC(5,2) NOT NULL DEFAULT 8.00,    -- platform cut, default 8%
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',-- PENDING | ACTIVE | SUSPENDED
    rating          NUMERIC(3,2),                          -- denormalized rolling average
    rating_count    INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    approved_at     TIMESTAMPTZ
);
```

**Lifecycle:**
1. User registers normally (`POST /api/auth/register`)
2. User applies to become seller (`POST /api/sellers/apply`) — fills business info → status=PENDING
3. Admin reviews and approves (`PATCH /api/admin/sellers/{id}` → status=ACTIVE) — backend grants `ROLE_SELLER`
4. Now seller can create listings + see seller dashboard

### NEW: listings (the per-seller offer)

```sql
CREATE TABLE listings (
    id              BIGSERIAL PRIMARY KEY,
    product_id      BIGINT  NOT NULL REFERENCES products(id),
    seller_id       BIGINT  NOT NULL REFERENCES sellers(id),
    price_amount    NUMERIC(12,2) NOT NULL,
    price_currency  VARCHAR(3) NOT NULL DEFAULT 'TRY',
    condition       VARCHAR(20) NOT NULL DEFAULT 'NEW',     -- NEW | USED | REFURBISHED
    shipping_days   INT NOT NULL DEFAULT 2,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_listings UNIQUE (product_id, seller_id, condition)
);
```

**Constraint:** one (product, seller, condition) tuple — a seller can list "iPhone 15 — NEW" once but can also list it as "USED" separately.

### Modified: inventory_items

```sql
ALTER TABLE inventory_items ADD COLUMN seller_id BIGINT;
ALTER TABLE inventory_items DROP CONSTRAINT inventory_items_product_id_key;     -- remove old uniq
ALTER TABLE inventory_items ADD CONSTRAINT uq_inv_product_seller UNIQUE (product_id, seller_id);
-- Migrate existing rows: seller_id = NULL means "platform-owned legacy stock"
```

**Reservation now carries listingId** so the saga knows which seller's stock to deduct.

### Modified: orders + order_items (sub-order split)

Two ways to model split:

**Option A — sub_orders table (recommended)**
```sql
CREATE TABLE sub_orders (
    id              BIGSERIAL PRIMARY KEY,
    parent_order_id BIGINT NOT NULL REFERENCES orders(id),
    seller_id       BIGINT NOT NULL REFERENCES sellers(id),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING | CONFIRMED | SHIPPED | DELIVERED | CANCELLED
    subtotal        NUMERIC(12,2) NOT NULL,
    commission_pct  NUMERIC(5,2)  NOT NULL,                  -- snapshot at order time
    commission_amt  NUMERIC(10,2) NOT NULL,                  -- platform fee
    payout_amt      NUMERIC(12,2) NOT NULL,                  -- seller receives this
    payout_status   VARCHAR(20) NOT NULL DEFAULT 'HELD',     -- HELD | PAID | REVERSED
    paid_at         TIMESTAMPTZ
);
ALTER TABLE order_items ADD COLUMN sub_order_id BIGINT REFERENCES sub_orders(id);
ALTER TABLE order_items ADD COLUMN listing_id   BIGINT REFERENCES listings(id);
ALTER TABLE order_items ADD COLUMN seller_id    BIGINT REFERENCES sellers(id);
```

**Option B — orders.seller_id only (single-seller per order, force split into separate orders)**

Decision: **Option A**. Keeps the user-facing concept of "Order #123" intact, and supports per-seller status independently (one ships, another delays). This is how Trendyol displays "Order has 3 packages from 3 sellers."

### NEW: seller_payouts (V3+)

```sql
CREATE TABLE seller_payouts (
    id              BIGSERIAL PRIMARY KEY,
    seller_id       BIGINT NOT NULL,
    amount          NUMERIC(12,2) NOT NULL,
    currency        VARCHAR(3) NOT NULL,
    period_start    DATE NOT NULL,
    period_end      DATE NOT NULL,
    sub_order_count INT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING | TRANSFERRED | FAILED
    iban_snapshot   VARCHAR(40),
    transferred_at  TIMESTAMPTZ
);
```

Daily/weekly batch job aggregates `sub_orders.payout_amt` where `payout_status='HELD'` and creates a payout per seller.

### NEW: reviews (V4)

```sql
CREATE TABLE product_reviews (
    id              BIGSERIAL PRIMARY KEY,
    product_id      BIGINT NOT NULL,
    seller_id       BIGINT NOT NULL,                         -- which seller fulfilled?
    user_id         BIGINT NOT NULL,
    order_item_id   BIGINT NOT NULL UNIQUE,                  -- one review per purchased item
    rating          INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    title           VARCHAR(120),
    body            TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

Seller `rating` and `rating_count` columns updated by trigger or async job after each review.

---

## 3. Service Topology

### NEW: seller-service (port 8090)

Owns: seller CRUD, KYC review, listings CRUD, payout reports, seller dashboard endpoints.

**Why separate?** Seller business logic (commission, KYC, payout windows) is its own bounded context. Coupling it to product-service (catalog) or order-service (orders) blurs responsibilities.

**Endpoints:**
- `POST /api/sellers/apply` — user applies to become seller
- `GET /api/sellers/me` — current seller's profile
- `PATCH /api/sellers/me` — update business info
- `GET /api/sellers/{id}` — public seller storefront (rating, listing count)
- `GET /api/admin/sellers?status=PENDING` — admin queue
- `PATCH /api/admin/sellers/{id}` — approve/suspend
- `POST /api/listings` (seller-only) — create new listing for existing master product
- `GET /api/listings/me` — seller's own listings
- `PUT /api/listings/{id}` (seller-only) — update price/stock
- `DELETE /api/listings/{id}` — soft-delete
- `GET /api/products/{id}/listings` — public buy-box ("other sellers")
- `GET /api/sellers/me/payouts` — payout history
- `GET /api/sellers/me/sub-orders` — incoming sub-orders to fulfill

### Modified: product-service

- Catalog `GET /api/products` response augmented with `bestListing { sellerId, sellerName, price, shippingDays, listingId }` — the "buy box" winner
- New endpoint `GET /api/products/{id}/listings` returns ALL active listings (delegated to seller-service via Feign)
- Product creation by admin stays the same (master catalog)

### Modified: inventory-service

- `inventory_items.seller_id` column added
- `POST /api/inventory/reservations` request body gains `sellerId` — reserves the right pool
- All existing reservations migrated to `seller_id = NULL` (legacy / platform stock)

### Modified: order-service

- Saga step 1c: **after cart fetch, group items by seller** (each cart item now references a `listing_id` not just `product_id`)
- Saga step 3 (reserve): per-item reservation now sends `sellerId`
- Saga step 6 (CONFIRMED): create one `sub_orders` row per seller with their subtotal + commission split
- New endpoint `PATCH /api/sub-orders/{id}/status` (seller-only) — seller marks CONFIRMED → SHIPPED → DELIVERED
- Order detail returns the sub-order grid

### Modified: payment-service

- Charge stays single (one card swipe for the whole order)
- New concept: **escrow** — funds held by platform, not immediately released
- New endpoint `POST /api/payments/{id}/release` — moves money from escrow to seller payout pool when sub-order DELIVERED
- Refund logic now per sub-order

### Modified: cart-service

- Cart item shape changes: `{ listingId, productId, sellerId, ... }` instead of just `productId`
- Cart's "add" decides which listing to pick:
  - Frontend's product detail page passes a chosen `listingId`
  - "Add to cart" from catalog = best buy-box listing
- Multi-seller cart UI groups items visually but stores them flat

### NEW (frontend): seller-service-frontend pages

- `/seller/apply` — application form
- `/seller/dashboard` — overview (sales last 30d, pending sub-orders, payouts)
- `/seller/listings` — list/CRUD their listings
- `/seller/orders` — sub-orders to fulfill (mark shipped)
- `/seller/payouts` — payout history
- `/admin/sellers` — admin: pending applications, approve/suspend

---

## 4. Customer Flow Walkthrough

### Browse → Add to cart → Order

1. **Catalog** (`GET /api/products`)
   - Each product card shows the **buy-box winner**: `1799 TRY · sold by TechMart · ★ 4.5` plus `+3 other sellers from 1850 TRY`

2. **Product detail** (`GET /api/products/1`)
   - Buy box at top (lowest enabled listing in stock)
   - "Other sellers" table below: 4 rows with seller name, price, condition, shipping days, "Add to cart" each
   - Customer picks one → `POST /api/cart/items { listingId: 42, quantity: 1 }`

3. **Cart**
   - Items grouped by seller in UI (`Sold by TechMart: 2 items, 1799 TRY`)
   - Subtotal, shipping (per seller), grand total

4. **Checkout**
   - Single payment form, single card charge
   - Backend saga runs once, but reserves stock from N seller pools, creates N sub-orders, splits commission

5. **Order detail**
   - "Order #1234 — Total 4500 TRY"
   - Three boxes:
     - "From TechMart — CONFIRMED — Shipping in 2 days" (sub-order #1)
     - "From AudioPro — SHIPPED — tracking #ABC" (sub-order #2)
     - "From DigitalStore — DELIVERED" (sub-order #3)

### Seller flow

1. Existing user goes to `/seller/apply`, fills business info
2. Admin sees in `/admin/sellers?status=PENDING`, approves
3. User now has `ROLE_SELLER` claim → "Seller" link appears in header
4. Goes to `/seller/listings` → "+ New listing" → picks an existing master product → enters price + stock + shipping days
5. New listing visible in catalog buy-box if price wins
6. Order arrives → seller sees in `/seller/orders` → marks SHIPPED with tracking → marks DELIVERED
7. After delivery, sub-order goes to **PAYOUT_HELD** queue
8. Weekly batch creates a payout, transfers (mock) to IBAN
9. Seller sees `/seller/payouts` history

---

## 5. Buy-Box Algorithm (V1)

```
For each product, among active listings with stock > 0:
  score = price + (shipping_days × 5)        // 5 TRY/day shipping speed penalty
  winner = listing with min(score)           
  tie-breaker = highest seller.rating
```

V2 can add: prime sellers, ad-funded slots, customer location for shipping cost. For V1, deterministic and simple.

The buy-box winner is **denormalized into product list responses** to avoid N+1 — same pattern as live-stock enrichment from Phase 12.

---

## 6. Payment + Commission Math

Order total = sum over sub-orders. Per sub-order:

```
sub_order.subtotal      = Σ(item.price × item.qty)
sub_order.commission_amt = subtotal × seller.commission_pct / 100
sub_order.payout_amt     = subtotal − commission_amt
```

Customer charged once: total of all subtotals + shipping.
Platform holds funds. When sub-order DELIVERED + return window (e.g., 14 days) passes → payout released.

**Escrow lifecycle:**
- `payout_status = HELD` from CONFIRMED to DELIVERED+14d
- → `READY_FOR_PAYOUT` (next batch picks up)
- → `PAID`
- → `REVERSED` if customer requests refund within window

V1 implements the math + status machine but **mocks the actual bank transfer** (logs the IBAN + amount). Real bank API integration is out-of-scope.

---

## 7. Phased Rollout

| Phase | Goal | Duration | Acceptance |
|---|---|---|---|
| **V1 — Seller domain** | Sellers can register, get approved, create listings against master products. Catalog shows buy-box (single-seller). NO checkout integration yet. | ~1 week | Admin approves seller; seller creates 3 listings; catalog shows their listings; existing checkout flow unchanged |
| **V2 — Single-seller order flow** | Cart add picks a listing. Order saga still single-seller (or trivially split). Inventory reserved per seller. | ~1 week | Customer adds listing-A from seller-X to cart; checkout charges; sub_orders has one row; seller sees the sub-order in their dashboard |
| **V3 — Multi-seller order + commission** | Cart can mix sellers. Order auto-splits into N sub-orders. Commission math + escrow status machine. | ~1 week | Customer adds 3 listings from 2 sellers; order has 2 sub-orders; sellers see them; admin sees commission report; payouts mocked |
| **V4 — Reviews + payout batch + dispute stubs** | Product reviews, seller rating recompute, weekly payout batch, return/dispute manual flow. | ~1 week | After delivered order, customer leaves review; seller rating updates; weekly cron creates payout rows; admin can mark a sub-order REFUNDED |

Each phase is shippable on its own. After V1 the platform still works as a single-vendor for existing flows; the new seller domain is an add-on.

---

## 8. Backwards Compatibility

The existing customer flow MUST keep working through V1+V2:
- Master products stay sellable directly until V3 (legacy "platform-owned" listing auto-created via migration: every existing product gets a `listings` row owned by `seller_id = 0` representing the platform)
- Existing orders untouched
- Existing inventory reservations get `seller_id = 0` retroactively

**Fallback strategy:** if no seller listing exists for a product, the catalog falls back to the platform listing (admin maintained). This avoids "out of stock" cliff during onboarding.

---

## 9. Out of Scope (V1-V4)

| Feature | Why deferred |
|---|---|
| Seller-submitted product proposals (admin moderation) | Simpler V1: only admin curates master products. V5 adds seller proposals. |
| Real bank API integration | Iyzico Marketplace API exists but production-grade auth ⇒ separate sprint |
| Returns RMA + dispute resolution flow | Manual admin tickets in V4; automated in V5+ |
| Seller-funded ads / sponsored listings | Different domain |
| Multi-currency, multi-warehouse, drop-ship | Big refactors, V6+ |
| Variations (size/color) on listings | Phase X — touches every layer |
| Bulk listing import (CSV/Excel) | Phase X |
| Real-time stock sync from sellers' ERP | Phase X |

---

## 10. Risks & Trade-offs

| Risk | Mitigation |
|---|---|
| **Migration disruption** — adding seller_id everywhere mid-platform | Phased rollout + legacy "platform seller" row that owns existing data |
| **Saga complexity explosion** — N sub-orders, N reservations, N commits | Keep saga single-transactional within order-service; only inventory split per call |
| **Buy-box gaming** — sellers race to lowest price | V1 deterministic; V2+ add seller-rating tie-breakers and price-floor checks |
| **Payout fraud** | Manual admin review for first N payouts per seller; auto-payout after 50 successful orders |
| **Existing tests break** | All current ~80 tests rely on single-seller assumptions; expect 30% to need rewrites |
| **Frontend complexity** | Seller pages = new app section. Customer pages get only minor additions (buy-box widget, sub-order grid in order detail) |

---

## 11. Interview Talking Points

1. **Bounded contexts:** seller-service vs product-service vs order-service — why separate? Each has different change rate and team ownership in real org (catalog team, marketplace team, fulfillment team).
2. **Soft launch via legacy "platform seller":** zero-downtime migration of existing data into the new model.
3. **Buy-box algorithm:** simple ranking with cache-friendly recomputation. Real platforms use ML for personalization.
4. **Escrow + commission math:** classic two-sided marketplace problem. Explains why platforms hold funds and why payout windows exist (chargebacks, returns).
5. **Multi-seller saga:** order-service still single saga, but with seller-aware steps. Each Feign call to inventory carries `(productId, sellerId)`.
6. **Idempotency on payouts:** payout batch jobs must be re-runnable without double-paying. Use `period_start, period_end, seller_id` UNIQUE on `seller_payouts`.
7. **CAP/consistency:** sub_order status is per-seller eventual; customer order overall status is computed (any cancelled = "partial fulfillment", all delivered = "complete").
8. **Why master catalog + listings instead of duplicating products?** Search, recommendations, and reviews work on master products (same Wireless Headphones reviewed by anyone who bought from any seller). Pricing differentiation lives at the listing layer.

---

## 12. Acceptance Criteria — V1

1. `mvn clean verify` green for all 14+ modules (15th if seller-service split out).
2. Existing customer flow still works (catalog browse, login, add to cart, place order, see order history).
3. New user → applies as seller → admin approves → can create listings.
4. Catalog product detail shows "Other sellers (3)" panel with each listing's price.
5. Seed data: existing 20 products auto-migrated to "Platform" seller (id 1, owner = system user).
6. Tag `phase-13-v1-complete` pushed.

---

## 13. Open Questions (decisions you'd weigh in on)

These are the few questions where the answer materially changes implementation. I'm picking sensible defaults but flagging them:

| Question | Default I'd pick | Why |
|---|---|---|
| Sellers create master products themselves? | NO in V1 (admin only) | Simpler moderation; can add later |
| Listing condition (NEW/USED/REFURBISHED)? | YES — column exists, V1 only NEW supported | Cheap to add column now |
| Per-seller shipping cost? | NO in V1 (free shipping mock) | Real shipping API is a sprint of its own |
| Multi-currency listings? | NO — TRY only | Existing assumption, V1 keeps it |
| Allow customer to choose specific seller from product detail? | YES (n11 standard) | The whole point of marketplace |
| Seller can edit their listing price live? | YES | But cart prices are snapshotted at add-time |
| Returns/refunds in V1? | NO | V4 manual admin flow |

If you disagree on any of these, override them — otherwise I'll execute V1 with these defaults.

---

## 14. Mülakat Sorusu — Tek Cümlede

> "Single-vendor → marketplace dönüşümü mimari olarak nasıl yapılır?"

**Cevap:** Catalog'u (paylaşılan master) listings'ten (per-seller offer) ayır → inventory'i `(productId, sellerId)` per-pool yap → order'ı sub-order'lara böl ki her seller kendi statüsünü yürütsün → payment'ı escrow'a al, seller commission düşüp payout çıkışı yap. Mevcut veriyi "platform seller" olarak migrate et, zero-downtime.

---

## Sıradaki adım

**Otorite sende. V1'e mi başlıyoruz, yoksa farklı bir öncelik mi var?**

V1 backbone (~1 hafta full-time):
1. seller-service skeleton + sellers/listings tabloları
2. Apply/approve flow (user-service'le entegrasyon)
3. Catalog'a listing enrichment
4. Frontend: /seller/apply, /seller/dashboard, /admin/sellers
5. Mevcut data'yı platform-seller'a migrate
6. Smoke test + tag
