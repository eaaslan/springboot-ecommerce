#!/usr/bin/env bash
# ===============================================================
# 10dk demo videosu öncesi tek komutluk hazırlık.
#
# ./docs/demo/prep-demo.sh
#
# Yaptıkları:
#   1. Stack'in hepsi healthy mi diye kontrol eder
#   2. alice'i ADMIN'e yükseltir (default register USER veriyor)
#   3. seed.js çalıştırır → 8 buyer + 5 seller + 60 product + listings + reviews
#   4. V1 seed'in görselsiz 20 ürününe Picsum imageUrl yazar
#   5. Inventory backfill yapar (yeni ürünler için stok satırı)
#   6. Buyer1 ile bir test sipariş verir → sub_order yaratır
#   7. Admin payout batch'i bugün için tetikler → /admin/payouts dolar
#   8. Final smoke test
#
# Hata olursa script renkli mesaj basar ve neden çıktığını söyler.
# ===============================================================

set -euo pipefail
cd "$(dirname "$0")/../.."

API="http://localhost"
INV="http://localhost:8084"
GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'

step() { echo -e "\n${GREEN}▶ $1${NC}"; }
warn() { echo -e "${YELLOW}  $1${NC}"; }
fail() { echo -e "${RED}✗ $1${NC}"; exit 1; }

# 0. Stack ayakta mı?
step "Stack health check"
if ! curl -fs -m 3 "$API/" >/dev/null 2>&1; then
  fail "http://localhost erişilebilir değil. Önce: docker compose up --build -d"
fi
if ! curl -fs -m 3 "$API/api/products?page=0&size=1" >/dev/null 2>&1; then
  fail "Gateway → product-service routable değil. Birkaç saniye bekleyip tekrar dene."
fi
echo "  ✓ frontend + gateway + product-service healthy"

# 1. Ensure alice exists + ADMIN role
step "alice@example.com → register + promote to ADMIN"
# Register if missing (409 = already exists, fine)
curl -s -X POST -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"password123","name":"Alice Admin"}' \
  "$API/api/auth/register" >/dev/null || true
docker compose exec -T postgres psql -U user -d userdb -c \
  "UPDATE users SET role='ADMIN' WHERE email='alice@example.com';" >/dev/null
echo "  ✓ alice registered + promoted"

# 2. Seed
step "Seed çalıştırılıyor (varsa skip eder, idempotent)"
if [ ! -d scripts/seed/node_modules ]; then
  warn "node_modules yok, npm install yapılıyor…"
  (cd scripts/seed && npm install --silent)
fi
API_URL="$API" INVENTORY_URL="$INV" node scripts/seed/seed.js --no-orders 2>&1 | tail -8

# 3. V1 backfill images
step "V1 seed ürünlerine Picsum görseli atanıyor"
API_URL="$API" node scripts/seed/backfill-images.js 2>&1 | tail -2

# 4. Inventory backfill (V1 + seed olanların hepsi için)
step "Inventory backfill (eksikleri tamamlıyor)"
API_URL="$API" INVENTORY_URL="$INV" node scripts/seed/backfill-inventory.js 2>&1 | tail -2

# 5. Test orders — her seed satıcısı için en az bir sipariş
#    (videoda "demo paneli'nden satıcıya geç → sipariş gözüksün" akışı için)
step "Test orders yerleştiriliyor (her satıcı için en az 1 sipariş)"
TOKEN=$(curl -s -X POST -H "Content-Type: application/json" \
  -d '{"email":"buyer1@example.com","password":"password123"}' \
  "$API/api/auth/login" | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['accessToken'])")

# Her satıcı için ilk listing'ini seç → o ürünü siparişe ekle
SELLER_LISTINGS=$(node -e '
async function main() {
  const r = await fetch("'$API'/api/products?page=0&size=200");
  const products = (await r.json()).data.content;
  const seenSellers = new Set();
  for (const p of products) {
    // Her ürün için bestListing tek bir satıcıyı veriyor — bestListing.id ve sellerId
    if (p.bestListing && !seenSellers.has(p.bestListing.sellerId)) {
      seenSellers.add(p.bestListing.sellerId);
      console.log(p.id + "," + p.bestListing.id + "," + p.bestListing.sellerName);
    }
  }
}
main();
')
ORDER_COUNT=0
echo "$SELLER_LISTINGS" | while IFS=, read -r PID LID NAME; do
  [ -z "$PID" ] && continue
  curl -s -X DELETE -H "Authorization: Bearer $TOKEN" "$API/api/cart" >/dev/null
  curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
    -d "{\"productId\":$PID,\"quantity\":1,\"listingId\":$LID}" \
    "$API/api/cart/items" >/dev/null
  RESULT=$(curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
    -H "Idempotency-Key: $(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)" \
    -d '{"card":{"holderName":"Demo","number":"4111111111111111","expireMonth":"12","expireYear":"2030","cvc":"123"}}' \
    "$API/api/orders" | python3 -c "import json,sys;d=json.load(sys.stdin);print(d['data']['id'] if d.get('success') else 0)" 2>/dev/null)
  if [ "${RESULT:-0}" -gt 0 ]; then
    echo "  ✓ order #$RESULT → $NAME"
  fi
done

# 6. Admin payout batch
step "Admin payout batch (bugün için)"
ADMIN=$(curl -s -X POST -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"password123"}' \
  "$API/api/auth/login" | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['accessToken'])")

START=$(date -u -v-1d +%Y-%m-%d 2>/dev/null || date -u -d 'yesterday' +%Y-%m-%d)
END=$(date -u -v+1d +%Y-%m-%d 2>/dev/null || date -u -d 'tomorrow' +%Y-%m-%d)
PAYOUT_COUNT=$(curl -s -X POST -H "Authorization: Bearer $ADMIN" -H "Content-Type: application/json" \
  -d "{\"periodStart\":\"$START\",\"periodEnd\":\"$END\"}" \
  "$API/api/admin/payouts/run" | python3 -c "
import json,sys
r=json.load(sys.stdin)
print(len(r['data']) if r.get('success') else 0)")
echo "  ✓ $PAYOUT_COUNT payout(s) created"

# 7. Final smoke
step "Final smoke"
TOTAL_PRODUCTS=$(curl -s "$API/api/products?page=0&size=1" | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['totalElements'])")
ACTIVE_SELLERS=$(curl -s -H "Authorization: Bearer $ADMIN" "$API/api/sellers/admin?status=ACTIVE" | python3 -c "import json,sys;print(len(json.load(sys.stdin)['data']))")

echo "  total products  : $TOTAL_PRODUCTS"
echo "  active sellers  : $ACTIVE_SELLERS"
echo "  payouts         : $PAYOUT_COUNT"

echo
echo -e "${GREEN}=========================================${NC}"
echo -e "${GREEN} Demo hazır. Tarayıcıyı aç:${NC}"
echo "   http://localhost          (storefront)"
echo "   http://localhost/demo     (one-click login switcher)"
echo "   http://localhost:3000     (Grafana — admin/admin)"
echo "   http://localhost:9411     (Zipkin)"
echo
echo -e "${GREEN} Login:${NC} alice / seller1..5 / buyer1..8 — hepsi password123"
echo -e "${GREEN}=========================================${NC}"
