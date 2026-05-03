#!/usr/bin/env node
/* eslint-disable no-console */

// ---------------------------------------------------------------------------
// Demo seed for the Spring Boot e-commerce stack.
// Hits the gateway at $API (default http://localhost:8080) so every saga,
// commission lookup, sub-order split, etc. runs through the real services.
//
//   cd scripts/seed
//   npm install                       # one-time
//   node seed.js                      # default: 8 buyers, 5 sellers, 60 products
//   node seed.js --products 120 --buyers 20 --sellers 8
//   node seed.js --no-orders          # skip the slow order-placement step
//
// Idempotent on user creation (skips if the email already exists). Re-running
// will create *more* listings + reviews + orders unless the underlying DBs are
// wiped first.
// ---------------------------------------------------------------------------

import { faker } from "@faker-js/faker";

const API = process.env.API_URL || "http://localhost:8080";
// Inventory has no gateway route by default; seed talks to it directly.
const INVENTORY = process.env.INVENTORY_URL || "http://localhost:8084";

// CLI flags ---------------------------------------------------------------
const flags = parseFlags(process.argv.slice(2));
const N_BUYERS = flags.buyers ?? 8;
const N_SELLERS = flags.sellers ?? 5;
const N_PRODUCTS = flags.products ?? 60;
const PLACE_ORDERS = !flags["no-orders"];
const SEED_REVIEWS = !flags["no-reviews"];

// ---------------------------------------------------------------------------
// HTTP helper
// ---------------------------------------------------------------------------

async function call(method, path, body, token, baseOverride) {
  const headers = { "Content-Type": "application/json" };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (method === "POST" && path === "/api/orders") {
    headers["Idempotency-Key"] = crypto.randomUUID();
  }
  const base = baseOverride || API;
  const res = await fetch(base + path, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });
  if (res.status === 204) return null;
  let data = null;
  const ct = res.headers.get("content-type") || "";
  if (ct.includes("application/json")) data = await res.json();
  else data = await res.text();
  if (!res.ok) {
    const msg = data?.message || data?.code || `${res.status} ${res.statusText}`;
    const err = new Error(`${method} ${path} → ${msg}`);
    err.status = res.status;
    err.body = data;
    throw err;
  }
  return data?.data ?? data;
}

const get = (p, t) => call("GET", p, undefined, t);
const post = (p, b, t) => call("POST", p, b, t);
const patch = (p, b, t) => call("PATCH", p, b, t);

// ---------------------------------------------------------------------------
// Realistic product templates per category
// (name pool + image keyword for Picsum/Unsplash)
// ---------------------------------------------------------------------------

const TEMPLATES = {
  Electronics: [
    "Wireless Bluetooth Headphones",
    "Noise-Cancelling Earbuds",
    "Smart Watch Series 7",
    "Mechanical Gaming Keyboard",
    "4K Action Camera",
    "Portable SSD 1TB",
    "USB-C Hub 8-in-1",
    "Smart Home Hub",
    "Wireless Charging Pad",
    "Gaming Mouse RGB",
    "Bluetooth Speaker XL",
    "Smart Light Bulb Pack",
  ],
  Books: [
    "Atomic Habits",
    "The Lean Startup",
    "Sapiens — A Brief History of Humankind",
    "Clean Code",
    "Designing Data-Intensive Applications",
    "The Pragmatic Programmer",
    "Thinking, Fast and Slow",
    "Educated — A Memoir",
    "Project Hail Mary",
    "Dune",
  ],
  Fashion: [
    "Premium Cotton T-Shirt",
    "Slim-Fit Denim Jeans",
    "Leather Sneakers",
    "Wool Winter Jacket",
    "Casual Hoodie",
    "Running Shoes",
    "Polarized Sunglasses",
    "Leather Wallet",
    "Knit Beanie",
    "Canvas Backpack",
  ],
  Home: [
    "Air Purifier HEPA",
    "Robot Vacuum",
    "Espresso Machine",
    "Stainless Steel Cookware Set",
    "Memory Foam Pillow",
    "Smart Thermostat",
    "Cordless Stick Vacuum",
    "Bamboo Cutting Board",
    "Aromatherapy Diffuser",
    "Standing Desk Mat",
  ],
  Sports: [
    "Yoga Mat Premium",
    "Adjustable Dumbbell Set",
    "Cycling Helmet",
    "Running Hydration Vest",
    "Resistance Bands Kit",
    "Camping Tent 4-Person",
    "Fitness Tracker Band",
    "Football Pro",
    "Tennis Racket",
    "Hiking Backpack 50L",
  ],
};

const SELLER_NAMES = [
  "TechMart Elektronik",
  "Moda Home",
  "Kitap Dünyası",
  "Sport Center",
  "MegaShop",
  "Premium Outlet",
  "Anadolu Ticaret",
  "Star Mağaza",
];

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const pick = (arr) => arr[Math.floor(Math.random() * arr.length)];
const sample = (arr, n) => {
  const c = [...arr];
  for (let i = c.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [c[i], c[j]] = [c[j], c[i]];
  }
  return c.slice(0, Math.min(n, c.length));
};
const rand = (min, max) => Math.floor(Math.random() * (max - min + 1)) + min;
const round2 = (n) => Math.round(n * 100) / 100;

async function loginOrRegister(email, password, name) {
  try {
    const r = await post("/api/auth/login", { email, password });
    return r.accessToken;
  } catch (e) {
    if (e.status !== 401 && e.status !== 404) throw e;
  }
  await post("/api/auth/register", { email, password, name });
  const r = await post("/api/auth/login", { email, password });
  return r.accessToken;
}

function picsumUrl(seed) {
  return `https://picsum.photos/seed/${encodeURIComponent(seed)}/600/600`;
}

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------

async function main() {
  console.log(`▶ Seeding against ${API}`);
  console.log(
    `  buyers=${N_BUYERS} sellers=${N_SELLERS} products=${N_PRODUCTS} orders=${PLACE_ORDERS} reviews=${SEED_REVIEWS}`
  );

  // 1. Admin token (alice is the only ADMIN seeded by V1).
  let adminToken;
  try {
    adminToken = (await post("/api/auth/login", {
      email: "alice@example.com",
      password: "password123",
    })).accessToken;
  } catch (e) {
    console.error("Cannot log in as alice@example.com — make sure the user-service has its V1 seed.");
    throw e;
  }
  console.log("✓ admin token acquired");

  // 2. Categories (V1 seeds Electronics/Books/Fashion/Home/Sports).
  const categories = await get("/api/products/categories");
  const catByName = Object.fromEntries(categories.map((c) => [c.name, c]));
  console.log(`✓ ${categories.length} categories: ${categories.map((c) => c.name).join(", ")}`);

  // 3. Buyers (regular customers).
  const buyers = [];
  for (let i = 1; i <= N_BUYERS; i++) {
    const email = `buyer${i}@example.com`;
    const token = await loginOrRegister(email, "password123", faker.person.fullName());
    buyers.push({ email, token });
  }
  console.log(`✓ ${buyers.length} buyer account(s) ready`);

  // 4. Sellers (apply + admin approve + commission rate).
  const sellers = [];
  const wantSellers = SELLER_NAMES.slice(0, N_SELLERS);
  for (let i = 0; i < wantSellers.length; i++) {
    const businessName = wantSellers[i];
    const email = `seller${i + 1}@example.com`;
    const token = await loginOrRegister(email, "password123", businessName);

    // Apply (skip if already a seller).
    let me = null;
    try {
      me = await get("/api/sellers/me", token);
    } catch (e) {
      if (e.status !== 404) throw e;
    }
    if (!me) {
      await post(
        "/api/sellers/apply",
        {
          businessName,
          taxId: faker.string.numeric(10),
          iban: "TR" + faker.string.numeric(24),
          contactEmail: email,
          contactPhone: "+90 " + faker.string.numeric(10),
        },
        token
      );
      me = await get("/api/sellers/me", token);
    }

    // Approve to ACTIVE if not already, and set a custom commission so V4 lookup actually varies.
    if (me.status !== "ACTIVE") {
      await patch(
        `/api/sellers/admin/${me.id}`,
        { status: "ACTIVE", commissionPct: rand(5, 12) },
        adminToken
      );
    }
    me = await get("/api/sellers/me", token);
    sellers.push({ businessName, email, token, sellerId: me.id, commissionPct: me.commissionPct });
  }
  console.log(
    `✓ ${sellers.length} active seller(s): ` +
      sellers.map((s) => `${s.businessName}@${s.commissionPct}%`).join(", ")
  );

  // 5. Products (admin creates, with imageUrl pointing at Picsum seeded by SKU).
  const allProducts = [];
  let seqStart = Date.now() % 100000;
  for (let i = 0; i < N_PRODUCTS; i++) {
    const cat = pick(categories);
    const tplPool = TEMPLATES[cat.name] || ["Generic Product"];
    const baseName = pick(tplPool);
    const variant = pick(["Pro", "Plus", "Lite", "Mini", "Max", "Air", "X", "2024", "Edition"]);
    const name = `${baseName} ${variant}`;
    const sku = `SKU-${seqStart + i}`;
    const priceAmount = rand(150, 8000);

    let p;
    try {
      p = await post(
        "/api/products",
        {
          sku,
          name,
          description: faker.commerce.productDescription(),
          imageUrl: picsumUrl(sku),
          priceAmount,
          priceCurrency: "TRY",
          stockQuantity: rand(20, 200),
          categoryId: cat.id,
        },
        adminToken
      );
    } catch (e) {
      if (e.status === 409) continue; // duplicate SKU on re-run, skip
      throw e;
    }
    allProducts.push({ ...p, _category: cat.name });
    // Auto-create matching inventory row so the saga's reserve step succeeds.
    try {
      await call(
        "POST",
        "/api/inventory/items",
        { productId: p.id, availableQty: p.stockQuantity },
        undefined,
        INVENTORY
      );
    } catch (e) {
      console.warn(`  inventory upsert failed for product ${p.id}:`, e.message);
    }
    if ((i + 1) % 20 === 0) console.log(`  …${i + 1}/${N_PRODUCTS} products`);
  }
  console.log(`✓ ${allProducts.length} product(s) created`);

  // 6. Listings — each seller picks ~30-50% of products and creates a listing.
  let listingCount = 0;
  for (const seller of sellers) {
    const picked = sample(allProducts, Math.floor(allProducts.length * (0.3 + Math.random() * 0.25)));
    for (const p of picked) {
      // Per-seller price varies ±15% around the master price.
      const myPrice = round2(p.priceAmount * (0.85 + Math.random() * 0.3));
      try {
        await post(
          "/api/listings",
          {
            productId: p.id,
            priceAmount: myPrice,
            priceCurrency: "TRY",
            stockQuantity: rand(5, 80),
            condition: "NEW",
            shippingDays: rand(1, 5),
          },
          seller.token
        );
        listingCount++;
      } catch (e) {
        // 409 on duplicate (productId, sellerId, condition) — fine, just skip.
        if (e.status !== 409) console.warn("  listing failed:", e.message);
      }
    }
  }
  console.log(`✓ ${listingCount} listing(s) created`);

  // 7. Reviews — each buyer leaves ~3-6 reviews on random (seller, product) pairs.
  if (SEED_REVIEWS) {
    let reviewCount = 0;
    for (const buyer of buyers) {
      const n = rand(3, 6);
      for (let i = 0; i < n; i++) {
        const seller = pick(sellers);
        const product = pick(allProducts);
        const rating = rand(3, 5); // demo skew positive
        try {
          await post(
            "/api/reviews",
            {
              sellerId: seller.sellerId,
              productId: product.id,
              rating,
              body: faker.lorem.sentence({ min: 4, max: 14 }),
            },
            buyer.token
          );
          reviewCount++;
        } catch (e) {
          // 409 → already reviewed this (user, seller, product); skip.
          if (e.status !== 409) console.warn("  review failed:", e.message);
        }
      }
    }
    console.log(`✓ ${reviewCount} review(s) posted`);
  }

  // 8. Orders — first half of buyers each place 1 order with the buy-box listing.
  if (PLACE_ORDERS) {
    // If this run created no products (e.g. orders-only re-seed), pull a sample of
    // existing products from the catalog so the order step still has stock to pick from.
    let candidateProducts = allProducts;
    if (candidateProducts.length === 0) {
      const page = await get("/api/products?page=0&size=80");
      candidateProducts = page.content || [];
    }

    let orderCount = 0;
    const card = {
      holderName: "Demo User",
      number: "4111111111111111",
      expireMonth: "12",
      expireYear: "2030",
      cvc: "123",
    };
    const buyersPlacing = buyers.slice(0, Math.ceil(buyers.length / 2));
    for (const buyer of buyersPlacing) {
      const product = await findWithListing(candidateProducts);
      if (!product) {
        console.warn("  no product with bestListing found for", buyer.email);
        continue;
      }
      try {
        await call("DELETE", "/api/cart", undefined, buyer.token);
        await post(
          "/api/cart/items",
          { productId: product.id, quantity: rand(1, 3), listingId: product.bestListing?.id },
          buyer.token
        );
        await post("/api/orders", { card }, buyer.token);
        orderCount++;
      } catch (e) {
        console.warn("  order failed for", buyer.email, "→", e.message);
      }
    }
    console.log(`✓ ${orderCount} order(s) placed`);
  }

  console.log("\nDone. Open http://localhost:5173 to browse the seeded catalog.");
  console.log("Logins:");
  console.log("  admin → alice@example.com / password123");
  console.log("  buyers → buyer1..N@example.com / password123");
  console.log("  sellers → seller1..N@example.com / password123");
}

async function findWithListing(products) {
  // Re-fetch a sample so bestListing is populated post-listing-creation.
  const sampled = sample(products, 8);
  for (const p of sampled) {
    try {
      const fresh = await get(`/api/products/${p.id}`);
      if (fresh.bestListing?.id) return fresh;
    } catch {
      // ignore
    }
  }
  return null;
}

function parseFlags(argv) {
  const out = {};
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (!a.startsWith("--")) continue;
    const key = a.slice(2);
    const next = argv[i + 1];
    if (next && !next.startsWith("--")) {
      const num = Number(next);
      out[key] = Number.isFinite(num) ? num : next;
      i++;
    } else {
      out[key] = true;
    }
  }
  return out;
}

main().catch((e) => {
  console.error("\n✗ Seed failed:", e.message);
  if (e.body) console.error("  body:", JSON.stringify(e.body));
  process.exit(1);
});
