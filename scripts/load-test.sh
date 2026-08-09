#!/usr/bin/env bash
# Measure InsureFlow API throughput/latency with hey.
# Usage: API_BASE=https://ims.EXAMPLE.sslip.io ./scripts/load-test.sh
set -euo pipefail

API_BASE="${API_BASE:-https://ims.144.24.50.163.sslip.io}"
DURATION="${DURATION:-60s}"
CONCURRENCY="${CONCURRENCY:-50}"
CUSTOMER_EMAIL="${CUSTOMER_EMAIL:-priya.sharma@example.com}"
CUSTOMER_PASSWORD="${CUSTOMER_PASSWORD:-}"

if ! command -v hey >/dev/null 2>&1; then
  echo "hey is required. Install with: brew install hey" >&2
  exit 1
fi

if [[ -z "$CUSTOMER_PASSWORD" ]]; then
  echo "Set CUSTOMER_PASSWORD for authenticated endpoint tests." >&2
  exit 1
fi

tmpdir="$(mktemp -d)"
trap 'rm -rf "$tmpdir"' EXIT

echo "==> Health check"
curl -fsS "$API_BASE/health" | tee "$tmpdir/health.json"
echo

echo "==> Login"
login_json="$(curl -fsS -X POST "$API_BASE/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$CUSTOMER_EMAIL\",\"password\":\"$CUSTOMER_PASSWORD\"}")"
echo "$login_json" | tee "$tmpdir/login.json" >/dev/null
token="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])' <<<"$login_json")"
printf 'Authorization: Bearer %s\n' "$token" > "$tmpdir/auth.hdr"

report() {
  local name="$1"
  shift
  echo
  echo "======== $name ========"
  "$@" | tee "$tmpdir/${name// /_}.txt"
}

report "GET /health (public)" \
  hey -z "$DURATION" -c "$CONCURRENCY" -disable-keepalive=false "$API_BASE/health"

report "GET /api/plans (public)" \
  hey -z "$DURATION" -c "$CONCURRENCY" "$API_BASE/api/plans"

report "GET /api/claims (JWT customer)" \
  hey -z "$DURATION" -c "$CONCURRENCY" -H @"$tmpdir/auth.hdr" "$API_BASE/api/claims"

report "GET /api/policies (JWT customer)" \
  hey -z "$DURATION" -c "$CONCURRENCY" -H @"$tmpdir/auth.hdr" "$API_BASE/api/policies"

echo
echo "Reports saved under $tmpdir"
ls -1 "$tmpdir"
