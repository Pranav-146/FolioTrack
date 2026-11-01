#!/usr/bin/env bash
# Manual rollback check: if the last write of an order fails, nothing from that order is saved.
#
# LOCAL DEV DB ONLY. For a few seconds this adds a trigger that makes EVERY insert into `transactions`
# fail, and always drops it again on exit (success, failure or Ctrl-C). Never run it against a shared or
# production database. Not run in CI.
#
# Prerequisites: the stack is running (docker compose up -d --build) from this repo with its .env,
#                bash (Git Bash on Windows), curl, docker.
# Usage:         scripts/rollback-check.sh [USER_ID] [SYMBOL]   (defaults: 2 KO; BASE_URL env overrides the API)
#
# How it works: OrderService deducts cash, updates the holding and saves the order before it saves the
# transaction row, all in one @Transactional method. The trigger makes that final insert fail.
# Expected result: the BUY gets a non-2xx response (the exact code depends on how the error is mapped),
# and the user's cash, holding and order count are exactly what they were before. The unchanged state is
# the proof that the earlier steps were rolled back. The trigger is gone afterwards.
set -euo pipefail

cd "$(dirname "$0")/.."
BASE_URL="${BASE_URL:-http://localhost:8080}"
USER_ID="${1:-2}"
SYMBOL="${2:-KO}"

# Read only the two values needed from .env instead of sourcing it, so nothing in .env gets executed
env_value() { grep -E "^$1=" .env | head -n1 | cut -d= -f2- | tr -d '\r'; }
PGUSER="$(env_value POSTGRES_USER)"
PGDB="$(env_value POSTGRES_DB)"
# client_min_messages hides the "does not exist, skipping" notices when the trap drops an already-dropped trigger
psql_exec() {
  docker compose exec -T -e PGOPTIONS='-c client_min_messages=warning' db \
    psql -v ON_ERROR_STOP=1 -qtA -U "$PGUSER" -d "$PGDB" -c "$1"
}

drop_trigger() {
  psql_exec "DROP TRIGGER IF EXISTS rollback_check_fail ON transactions;
             DROP FUNCTION IF EXISTS rollback_check_fail();" > /dev/null
}
# Set before the trigger exists, so it is removed however the script ends. INT/TERM exit, which runs EXIT.
trap drop_trigger EXIT
trap 'exit 130' INT TERM

portfolio() { curl -sf "$BASE_URL/api/users/$USER_ID/portfolio"; }
cash() { portfolio | grep -o '"cashBalance":[0-9.]*' | cut -d: -f2; }
holding_qty() {
  local q
  q=$(portfolio | grep -o "\"symbol\":\"$SYMBOL\"[^}]*" | grep -o '"quantity":[0-9]*' | cut -d: -f2 || true)
  echo "${q:-0}"
}
order_count() { curl -sf "$BASE_URL/api/users/$USER_ID/orders?size=1" | grep -o '"totalElements":[0-9]*' | cut -d: -f2; }

before_cash=$(cash)
before_qty=$(holding_qty)
before_orders=$(order_count)
echo "Before: cash $before_cash, $SYMBOL qty $before_qty, orders $before_orders"

psql_exec "CREATE FUNCTION rollback_check_fail() RETURNS trigger LANGUAGE plpgsql AS
             \$\$ BEGIN RAISE EXCEPTION 'rollback-check: simulated failure on transactions insert'; END \$\$;
           CREATE TRIGGER rollback_check_fail BEFORE INSERT ON transactions
             FOR EACH ROW EXECUTE FUNCTION rollback_check_fail();" > /dev/null
echo "Trigger installed; placing BUY 1 $SYMBOL (must be affordable so the order reaches the transaction insert)..."

code=$(curl -s -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -d "{\"symbol\":\"$SYMBOL\",\"side\":\"BUY\",\"quantity\":1}" "$BASE_URL/api/users/$USER_ID/orders")

drop_trigger
after_cash=$(cash)
after_qty=$(holding_qty)
after_orders=$(order_count)
triggers_left=$(psql_exec "SELECT count(*) FROM pg_trigger WHERE tgname = 'rollback_check_fail';")

failures=0
check() {
  if [[ "$2" == "$3" ]]; then echo "  ok   $1: $2"; else echo "  FAIL $1: got $2, expected $3"; failures=$((failures + 1)); fi
}
echo "Results (HTTP status $code):"
check "response is non-2xx" "$((code < 200 || code >= 300))" "1"
check "cash unchanged" "$after_cash" "$before_cash"
check "$SYMBOL holding unchanged" "$after_qty" "$before_qty"
check "order count unchanged" "$after_orders" "$before_orders"
check "trigger removed" "$triggers_left" "0"

if ((failures > 0)); then
  echo "FAIL: $failures check(s) failed"
  exit 1
fi
echo "PASS: failed order left no trace (HTTP $code)"
