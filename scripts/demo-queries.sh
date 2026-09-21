#!/usr/bin/env bash
set -euo pipefail

base_url="${BASE_URL:-http://localhost:8080}"

curl -fsS "$base_url/api/v1/members/TK123456789/tier"
echo
curl -fsS "$base_url/api/v1/members/TK123456789/mileage-ledger?from=2026-01-01&to=2026-12-31&limit=100"
echo
curl -fsS "$base_url/api/v1/members/TK123456789/tier-history?from=2026-01-01&to=2026-12-31&limit=100"
echo
