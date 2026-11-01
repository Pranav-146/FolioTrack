#!/usr/bin/env bash
# Manual concurrency check: 20 BUY orders for the same user, sent in parallel, must never overspend.
#
# LOCAL DEV DB ONLY. This places real orders: it spends the user's cash and adds to their holdings.
# Never point it at a shared or production environment. Not run in CI.
#
# Prerequisites: the stack is running (docker compose up -d --build), bash (Git Bash on Windows), curl.
# Usage:         scripts/concurrency-check.sh [USER_ID] [SYMBOL]   (defaults: 2 KO; BASE_URL env overrides the API)
#
# The order size is chosen from the user's live cash so only 14 of the 20 orders can be paid for.
# Expected result: every request returns 201, exactly 14 orders are FILLED and 6 REJECTED, final cash is
# start - 14 x order cost (never negative), the holding grows by 14 x quantity, 20 new orders and
# 14 new transactions. Without the row lock on the user, parallel orders could read the same balance
# and overspend.
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
USER_ID="${1:-2}"
SYMBOL="${2:-KO}"
ORDERS=20
AFFORDABLE=14

# Money arrives as JSON numbers with 2 decimals (NUMERIC(15,2)); bash only does integers, so work in cents.
to_cents() { local v="${1/./}"; echo $((10#$v)); }
fmt() { printf '%d.%02d' $(($1 / 100)) $(($1 % 100)); }

cash_cents() {
  to_cents "$(curl -sf "$BASE_URL/api/users/$USER_ID/portfolio" | grep -o '"cashBalance":[0-9.]*' | cut -d: -f2)"
}
holding_qty() {
  local q
  q=$(curl -sf "$BASE_URL/api/users/$USER_ID/portfolio" \
      | grep -o "\"symbol\":\"$SYMBOL\"[^}]*" | grep -o '"quantity":[0-9]*' | cut -d: -f2 || true)
  echo "${q:-0}"
}
total_elements() {
  curl -sf "$BASE_URL/api/users/$USER_ID/$1?size=1" | grep -o '"totalElements":[0-9]*' | cut -d: -f2
}

price=$(to_cents "$(curl -sf "$BASE_URL/api/instruments" \
  | grep -o "\"symbol\":\"$SYMBOL\"[^}]*" | grep -o '"currentPrice":[0-9.]*' | cut -d: -f2)")
start_cash=$(cash_cents)
start_qty=$(holding_qty)
start_orders=$(total_elements orders)
start_txns=$(total_elements transactions)

qty=$((start_cash / (price * AFFORDABLE)))
if ((qty < 1)); then
  echo "User $USER_ID has too little cash ($(fmt "$start_cash")) for this check; pass another USER_ID." >&2
  exit 1
fi
cost=$((price * qty))
expected_filled=$((start_cash / cost))
((expected_filled > ORDERS)) && expected_filled=$ORDERS

echo "User $USER_ID: cash $(fmt "$start_cash"), $SYMBOL at $(fmt "$price")"
echo "Sending $ORDERS parallel BUY $qty $SYMBOL orders ($(fmt "$cost") each)..."

out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT
for i in $(seq 1 "$ORDERS"); do
  curl -s -o "$out/$i.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -d "{\"symbol\":\"$SYMBOL\",\"side\":\"BUY\",\"quantity\":$qty}" \
    "$BASE_URL/api/users/$USER_ID/orders" > "$out/$i.code" &
done
wait

# Count files, not lines: curl's -w output has no trailing newline, so concatenating them would merge lines
created=$(grep -lx 201 "$out"/*.code | wc -l || true)
filled=$(cat "$out"/*.json | grep -o '"status":"FILLED"' | wc -l)
rejected=$(cat "$out"/*.json | grep -o '"status":"REJECTED"' | wc -l)
end_cash=$(cash_cents)
end_qty=$(holding_qty)
new_orders=$(($(total_elements orders) - start_orders))
new_txns=$(($(total_elements transactions) - start_txns))

failures=0
check() {
  if [[ "$2" == "$3" ]]; then echo "  ok   $1: $2"; else echo "  FAIL $1: got $2, expected $3"; failures=$((failures + 1)); fi
}
echo "Results:"
check "HTTP 201 responses" "$created" "$ORDERS"
check "FILLED orders" "$filled" "$expected_filled"
check "REJECTED orders" "$rejected" "$((ORDERS - expected_filled))"
check "final cash" "$(fmt "$end_cash")" "$(fmt $((start_cash - expected_filled * cost)))"
check "cash is not negative" "$((end_cash >= 0))" "1"
check "$SYMBOL holding quantity" "$end_qty" "$((start_qty + expected_filled * qty))"
check "new order rows" "$new_orders" "$ORDERS"
check "new transaction rows (one per fill)" "$new_txns" "$expected_filled"

if ((failures > 0)); then
  echo "FAIL: $failures check(s) failed"
  exit 1
fi
echo "PASS: $filled filled, $rejected rejected, no overspend"
