#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
DURATION="${DURATION:-60s}"
VUS_LIST="${VUS_LIST:-50 100 200}"
COUNT_LIST="${COUNT_LIST:-100000 1000000}"
DISTRIBUTION_LIST="${DISTRIBUTION_LIST:-uniform hot}"
TARGET_LIST="${TARGET_LIST:-mysql mysql-jdbc redis-string redis-hash}"
HOT_RATIO="${HOT_RATIO:-0.8}"
HOT_PERCENT="${HOT_PERCENT:-0.01}"
SKIP_SEED="${SKIP_SEED:-false}"
HTML_REPORTS="${HTML_REPORTS:-true}"
WEB_DASHBOARD_PERIOD="${WEB_DASHBOARD_PERIOD:-5s}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESULT_DIR="${RESULT_DIR:-$SCRIPT_DIR/results/$(date +%Y%m%d-%H%M%S)}"

mkdir -p "$RESULT_DIR"

echo "Benchmark result dir: $RESULT_DIR"
echo "BASE_URL=$BASE_URL"
echo "DURATION=$DURATION"
echo "VUS_LIST=$VUS_LIST"
echo "COUNT_LIST=$COUNT_LIST"
echo "DISTRIBUTION_LIST=$DISTRIBUTION_LIST"
echo "TARGET_LIST=$TARGET_LIST"
echo "HOT_RATIO=$HOT_RATIO"
echo "HOT_PERCENT=$HOT_PERCENT"
echo "SKIP_SEED=$SKIP_SEED"
echo "HTML_REPORTS=$HTML_REPORTS"
echo "WEB_DASHBOARD_PERIOD=$WEB_DASHBOARD_PERIOD"

for count in $COUNT_LIST; do
  echo

  if [[ "$SKIP_SEED" == "true" ]]; then
    echo "== Skipping seed for $count records =="
  else
    BASE_URL="$BASE_URL" "$SCRIPT_DIR/seed.sh" "$count" | tee "$RESULT_DIR/seed-$count.txt"
  fi

  for distribution in $DISTRIBUTION_LIST; do
    for vus in $VUS_LIST; do
      for target in $TARGET_LIST; do
        output="$RESULT_DIR/${count}-${distribution}-${vus}vu-${target}.json"
        html_output="$RESULT_DIR/${count}-${distribution}-${vus}vu-${target}.html"

        echo
        echo "== Running count=$count distribution=$distribution vus=$vus target=$target =="
        if [[ "$HTML_REPORTS" == "true" ]]; then
          K6_WEB_DASHBOARD=true \
            K6_WEB_DASHBOARD_PORT=-1 \
            K6_WEB_DASHBOARD_PERIOD="$WEB_DASHBOARD_PERIOD" \
            K6_WEB_DASHBOARD_EXPORT="$html_output" \
            k6 run \
              -e BASE_URL="$BASE_URL" \
              -e COUNT="$count" \
              -e DISTRIBUTION="$distribution" \
              -e HOT_RATIO="$HOT_RATIO" \
              -e HOT_PERCENT="$HOT_PERCENT" \
              -e VUS="$vus" \
              -e DURATION="$DURATION" \
              --summary-export "$output" \
              "$SCRIPT_DIR/k6/$target.js"
        else
          k6 run \
            -e BASE_URL="$BASE_URL" \
            -e COUNT="$count" \
            -e DISTRIBUTION="$distribution" \
            -e HOT_RATIO="$HOT_RATIO" \
            -e HOT_PERCENT="$HOT_PERCENT" \
            -e VUS="$vus" \
            -e DURATION="$DURATION" \
            --summary-export "$output" \
            "$SCRIPT_DIR/k6/$target.js"
        fi
      done
    done
  done
done
