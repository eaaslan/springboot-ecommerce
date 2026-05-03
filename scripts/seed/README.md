# Demo seed

Populates the running stack with realistic-looking users, sellers, products,
listings, reviews, and a handful of orders so the storefront has something to
browse.

## Run

```bash
cd scripts/seed
npm install          # one-time

# default — 8 buyers, 5 sellers, 60 products
node seed.js

# bigger demo
node seed.js --products 120 --buyers 20 --sellers 8

# skip the slow order-placement step
node seed.js --no-orders
```

Set a different gateway with `API_URL=http://...`.

## What it creates

| Resource | Count (default) | Notes |
|---|---|---|
| Buyer users | 8 | `buyer1..8@example.com` / `password123` |
| Seller users | 5 | `seller1..5@example.com` / `password123`, applied + auto-approved by alice, each with a per-seller commission rate (5–12 %) so the V4 lookup actually varies |
| Master products | 60 | spread across the 5 seed categories, names from realistic templates, descriptions via Faker |
| Product images | 60 | `https://picsum.photos/seed/{SKU}/600/600` — stable per SKU, varied across products |
| Listings | ~80–120 | each seller lists 30–55 % of products at ±15 % of the master price, random stock + 1–5 d shipping |
| Reviews | ~24–48 | each buyer drops 3–6 reviews; seller rating is recomputed by the service |
| Orders | ~half of buyers | each places one order with the current buy-box listing → exercises the saga + sub-order split |

Re-running is safe: existing accounts are detected and skipped, duplicate
listings/reviews return 409 and are ignored.

## Why a script and not Flyway

Most resources require multi-service flows — applying as a seller, admin
approval, listing creation as the seller, order saga, sub-order split. Doing
that in pure SQL would bypass all the business logic and leave the system in an
inconsistent state. The script is slower but the data behaves like real data.

## Reset

To start clean, drop the relevant Postgres databases (`userdb`, `productdb`,
`cartdb`, `orderdb`, `sellerdb`) and let Flyway re-run, then re-seed.
