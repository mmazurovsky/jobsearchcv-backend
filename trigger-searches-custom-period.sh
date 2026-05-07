#!/usr/bin/env bash

# ===== CONFIGURE TIME PERIOD HERE =====
TIME_PERIOD="1 month"
# Allowed: 5 minutes, 10 minutes, 15 minutes, 20 minutes, 30 minutes,
#          1 hour, 4 hours, 24 hours, 1 week, 1 month
# ======================================

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JSON_FILE="$SCRIPT_DIR/local-dev/job-searches.json"

if [ ! -f "$JSON_FILE" ]; then
  echo "Error: $JSON_FILE not found"
  exit 1
fi

COUNT=$(jq length "$JSON_FILE")
BODY_FILE="/tmp/trigger-search-body.$$"
trap 'rm -f "$BODY_FILE"' EXIT

# Seconds to wait between consecutive triggers
INTERVAL_SECONDS=600

FAILED=0
SUCCEEDED=0

for i in $(seq 0 $((COUNT - 1))); do
  PAYLOAD=$(jq -c ".[$i] | . + {\"user_id\": \"local-user-001\", \"time_period\": \"$TIME_PERIOD\"}" "$JSON_FILE")
  TITLE=$(echo "$PAYLOAD" | jq -r '.job_title')

  echo "Triggering search $((i+1))/$COUNT: $TITLE ..."

  HTTP_CODE=$(curl -sS -o "$BODY_FILE" -w '%{http_code}' \
    -X POST http://localhost:8080/api/custom-search/trigger \
    -H "Content-Type: application/json" \
    -d "$PAYLOAD")
  CURL_EXIT=$?

  if [ $CURL_EXIT -ne 0 ]; then
    echo "  ERROR: curl failed (exit $CURL_EXIT) — is the backend running on http://localhost:8080?"
    FAILED=$((FAILED + 1))
  elif [ "$HTTP_CODE" -lt 200 ] || [ "$HTTP_CODE" -ge 300 ]; then
    echo "  ERROR: HTTP $HTTP_CODE"
    if [ -s "$BODY_FILE" ]; then
      cat "$BODY_FILE"
      echo
    fi
    FAILED=$((FAILED + 1))
  else
    if [ -s "$BODY_FILE" ]; then
      cat "$BODY_FILE"
      echo
    fi
    SUCCEEDED=$((SUCCEEDED + 1))
  fi

  if [ $((i + 1)) -lt $COUNT ]; then
    echo "  Waiting ${INTERVAL_SECONDS}s before next trigger..."
    sleep "$INTERVAL_SECONDS"
  fi
done

echo
echo "Done. $SUCCEEDED/$COUNT succeeded, $FAILED failed (time_period='$TIME_PERIOD')."

[ $FAILED -gt 0 ] && exit 1
exit 0
