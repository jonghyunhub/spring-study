#!/usr/bin/env bash
# 데이터 적재 전용 스크립트. 실험(run-scenarios.sh)과 분리되어 있으므로
# 필요할 때만 독립적으로 실행한다.
#
# Usage:
#   ./seed.sh 100000
#   BASE_URL=http://localhost:8080 ./seed.sh 1000000
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
COUNT="${1:?Usage: seed.sh <count>}"

echo "== Seeding $COUNT records via $BASE_URL/benchmark/seed =="
curl -fsS -X POST "$BASE_URL/benchmark/seed?count=$COUNT"
echo
