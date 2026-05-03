#!/usr/bin/env node
// One-shot: assign a Picsum image URL to every product that's missing one.
// Stable per-SKU so the same product always renders the same image.

const API = process.env.API_URL || "http://localhost:8080";

async function main() {
  const adminToken = (
    await postJson("/api/auth/login", {
      email: "alice@example.com",
      password: "password123",
    })
  ).accessToken;

  let page = 0;
  let totalPages = 1;
  let updated = 0;
  let skipped = 0;

  while (page < totalPages) {
    const r = await fetch(`${API}/api/products?page=${page}&size=50`);
    const data = (await r.json()).data;
    totalPages = data.totalPages;

    for (const p of data.content) {
      if (p.imageUrl) {
        skipped++;
        continue;
      }
      const url = `https://picsum.photos/seed/${encodeURIComponent(p.sku)}/600/600`;
      const res = await fetch(`${API}/api/products/${p.id}`, {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${adminToken}`,
        },
        body: JSON.stringify({ imageUrl: url }),
      });
      if (!res.ok) {
        console.warn(`  failed for product ${p.id}: ${res.status}`);
        continue;
      }
      updated++;
    }
    page++;
  }

  console.log(`Updated ${updated} product(s) with images, skipped ${skipped} (already had one).`);
}

async function postJson(path, body) {
  const r = await fetch(API + path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!r.ok) throw new Error(`${path} → ${r.status}`);
  return (await r.json()).data;
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
