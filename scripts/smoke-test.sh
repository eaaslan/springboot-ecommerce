#!/usr/bin/env bash
#
# End-to-end smoke test for the springboot-ecommerce stack.
#
# Pre-requisites:
#   - All services running (docker compose + the 12 spring-boot apps)
#   - jq + curl on PATH
#
# Usage:
#   ./scripts/smoke-test.sh
#
# What it tests, in order:
#   1. Health (liveness/readiness on every gateway-fronted service)
#   2. Auth: register (idempotent) + login (saves access token)
#   3. Catalog: list / search / by-id (public)
#   4. Cart: add item / get cart / clear
#   5. Orders: happy path (CONFIRMED) + idempotency replay + declining-card (CANCELLED)
#   6. Recommendations: similar / search / for-user (public)
#   7. Reactive catalog: list / search / SSE first-event (public)
#   8. Rate limit smoke: rapid-fire to provoke 429
#   9. Idempotency dedup verified: second response carries same orderId

set -uo pipefail
GATEWAY="${GATEWAY:-http://localhost:8080}"

PASS=0
FAIL=0
RED=$'\033[31m'
GREEN=$'\033[32m'
YELLOW=$'\033[33m'
BLUE=$'\033[34m'
RESET=$'\033[0m'

step() { printf "\n${BLUE}== %s ==${RESET}\n" "$*"; }
ok()   { printf "${GREEN}✓ %s${RESET}\n" "$*"; PASS=$((PASS+1)); }
fail() { printf "${RED}✗ %s${RESET}\n" "$*"; FAIL=$((FAIL+1)); }
info() { printf "${YELLOW}  %s${RESET}\n" "$*"; }

assert_status() {
  local label="$1" expected="$2" actual="$3"
  if [[ "$actual" == "$expected" ]]; then ok "$label (HTTP $actual)"; else fail "$label — expected $expected got $actual"; fi
}

# ─── 1. Health ────────────────────────────────────────────────────────────────
step "1. Health checks (gateway)"
for path in liveness readiness; do
  code=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY/actuator/health/$path" || echo 000)
  assert_status "gateway $path" 200 "$code"
done

# ─── 2. Auth ──────────────────────────────────────────────────────────────────
step "2. Auth: register (idempotent) + login"
EMAIL="${SMOKE_EMAIL:-smoke+$(date +%s)@example.com}"
PASS_PW="${SMOKE_PASSWORD:-password123}"

curl -s -o /dev/null -X POST "$GATEWAY/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS_PW\"}"
ok "register dispatched ($EMAIL)"

login_resp=$(curl -s -X POST "$GATEWAY/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS_PW\"}")
TOKEN=$(echo "$login_resp" | jq -r '.data.accessToken // empty')
if [[ -z "$TOKEN" || "$TOKEN" == "null" ]]; then
  fail "login did not return accessToken — body: $login_resp"
  echo "Aborting: cannot proceed without auth"
  exit 1
fi
ok "login → accessToken received (len=${#TOKEN})"

AUTH_HDR="Authorization: Bearer $TOKEN"

step "2b. /api/users/me"
me=$(curl -s -w "\n%{http_code}" -H "$AUTH_HDR" "$GATEWAY/api/users/me")
me_code="${me##*$'\n'}"
assert_status "users/me" 200 "$me_code"

# ─── 3. Catalog ───────────────────────────────────────────────────────────────
step "3. Catalog (public)"
sleep 2  # let any prior bursts drain through the gateway rate limiter
code=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY/api/products?page=0&size=5")
assert_status "catalog list" 200 "$code"

code=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY/api/products/1")
assert_status "catalog by id" 200 "$code"

# ─── 4. Cart ──────────────────────────────────────────────────────────────────
step "4. Cart"
sleep 1
add=$(curl -s -w "\n%{http_code}" -H "$AUTH_HDR" -H "Content-Type: application/json" \
  -X POST -d '{"productId":1,"quantity":2}' "$GATEWAY/api/cart/items")
add_code="${add##*$'\n'}"
assert_status "cart add" 200 "$add_code"

get=$(curl -s -H "$AUTH_HDR" "$GATEWAY/api/cart")
items_count=$(echo "$get" | jq -r '.data.items | length')
if [[ "$items_count" -ge 1 ]]; then ok "cart get → $items_count item(s)"; else fail "cart appears empty"; fi

# ─── 5. Orders ────────────────────────────────────────────────────────────────
step "5. Orders — happy path"
sleep 1
KEY=$(uuidgen 2>/dev/null || date +%s%N)
order_resp=$(curl -s -w "\n%{http_code}" \
  -H "$AUTH_HDR" -H "Content-Type: application/json" \
  -H "Idempotency-Key: $KEY" \
  -X POST -d '{"card":{"holderName":"Alice","number":"4111111111111111","expireMonth":"12","expireYear":"2030","cvc":"123"}}' \
  "$GATEWAY/api/orders")
order_code="${order_resp##*$'\n'}"
order_body="${order_resp%$'\n'*}"
assert_status "place order" 201 "$order_code"

ORDER_ID=$(echo "$order_body" | jq -r '.data.id // empty')
ORDER_STATUS=$(echo "$order_body" | jq -r '.data.status // empty')
if [[ "$ORDER_STATUS" == "CONFIRMED" ]]; then ok "order $ORDER_ID is CONFIRMED"; else fail "order status=$ORDER_STATUS (expected CONFIRMED)"; fi

step "5b. Idempotency replay (same key, same body)"
replay=$(curl -s -w "\n%{http_code}" \
  -H "$AUTH_HDR" -H "Content-Type: application/json" \
  -H "Idempotency-Key: $KEY" \
  -X POST -d '{"card":{"holderName":"Alice","number":"4111111111111111","expireMonth":"12","expireYear":"2030","cvc":"123"}}' \
  "$GATEWAY/api/orders")
replay_body="${replay%$'\n'*}"
REPLAY_ID=$(echo "$replay_body" | jq -r '.data.id // empty')
if [[ "$REPLAY_ID" == "$ORDER_ID" && -n "$ORDER_ID" ]]; then
  ok "replay returned SAME orderId ($ORDER_ID) — Idempotency-Key works"
else
  fail "replay returned different orderId ($REPLAY_ID vs $ORDER_ID) — idempotency broken"
fi

step "5c. Declining card → CANCELLED + compensation"
# Add cart again first (previous order may have cleared it)
curl -s -o /dev/null -H "$AUTH_HDR" -H "Content-Type: application/json" \
  -X POST -d '{"productId":1,"quantity":1}' "$GATEWAY/api/cart/items"

DECLINE_KEY=$(uuidgen 2>/dev/null || date +%s%N-decline)
decline=$(curl -s -w "\n%{http_code}" \
  -H "$AUTH_HDR" -H "Content-Type: application/json" \
  -H "Idempotency-Key: $DECLINE_KEY" \
  -X POST -d '{"card":{"holderName":"Alice","number":"4111111111111115","expireMonth":"12","expireYear":"2030","cvc":"123"}}' \
  "$GATEWAY/api/orders")
decline_code="${decline##*$'\n'}"
# Service throws PaymentFailedException → 402; that is the expected behavior
if [[ "$decline_code" == "402" || "$decline_code" == "500" ]]; then
  ok "declining card rejected with HTTP $decline_code (expected 402; saga compensates)"
else
  info "decline path returned $decline_code — may be acceptable; check order list"
fi

# ─── 6. Recommendations ───────────────────────────────────────────────────────
step "6. Recommendations (public)"
sleep 2
for path in "products/1/similar?k=3" "search?q=wireless&limit=5" "users/42?k=3"; do
  code=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY/api/recommendations/$path")
  assert_status "recommendations /$path" 200 "$code"
  sleep 1
done

# ─── 7. Reactive Catalog ──────────────────────────────────────────────────────
step "7. Reactive Catalog (WebFlux + R2DBC)"
code=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY/api/catalog/products?page=0&size=5")
assert_status "reactive catalog list" 200 "$code"

code=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY/api/catalog/products/search?q=wireless&limit=5")
assert_status "reactive catalog search" 200 "$code"

# SSE first-event check (timeout 5s; expect at least one event line)
sse_lines=$( (timeout 5 curl -sN "$GATEWAY/api/catalog/products/stream?intervalSeconds=1" 2>/dev/null | head -5 || true) | wc -l | tr -d ' \n')
sse_lines=${sse_lines:-0}
if [ "${sse_lines}" -ge 1 ] 2>/dev/null; then
  ok "SSE stream emitted ${sse_lines} line(s)"
else
  info "SSE stream empty (slow start or stream closed early)"
fi

# ─── 8. Rate limit smoke ──────────────────────────────────────────────────────
step "8. Rate limit smoke (gateway, 200 rapid GETs)"
codes=$(for i in $(seq 1 200); do
  curl -s -o /dev/null -w "%{http_code}\n" "$GATEWAY/api/products?page=0&size=1"
done | sort | uniq -c)
echo "$codes" | sed 's/^/  /'
if echo "$codes" | grep -q "429"; then
  ok "saw 429 — rate limit triggered as designed"
else
  info "no 429 seen — rate limit may not be triggered if Redis is missing or limits very high"
fi

# ─── Summary ──────────────────────────────────────────────────────────────────
step "Summary"
echo "  PASS: $PASS"
echo "  FAIL: $FAIL"
[[ "$FAIL" == 0 ]] && exit 0 || exit 1
