#!/usr/bin/env node
// One-shot: walks /api/products and POSTs an inventory_items row to inventory-service
// for any product missing one. Useful after a partial seed run that pre-dated the
// /api/inventory/items endpoint.

const API = process.env.API_URL || "http://localhost:8080";
const INVENTORY = process.env.INVENTORY_URL || "http://localhost:8084";

async function get(path) {
  const r = await fetch(API + path);
  if (!r.ok) throw new Error(`${path} → ${r.status}`);
  const j = await r.json();
  return j.data ?? j;
}

async function upsertInventory(productId, qty) {
  const r = await fetch(INVENTORY + "/api/inventory/items", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ productId, availableQty: qty }),
  });
  if (!r.ok) throw new Error(`upsert ${productId} → ${r.status}`);
}

async function main() {
  let page = 0;
  let done = false;
  let touched = 0;
  while (!done) {
    const data = await get(`/api/products?page=${page}&size=50`);
    for (const p of data.content) {
      try {
        await upsertInventory(p.id, p.stockQuantity);
        touched++;
      } catch (e) {
        console.warn("skip", p.id, e.message);
      }
    }
    page++;
    done = page >= data.totalPages;
  }
  console.log(`Inventory backfilled for ${touched} product(s).`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
