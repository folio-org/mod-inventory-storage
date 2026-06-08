#!/usr/bin/env bash
#
# reindex-load-test.sh — drive the reindex merge-phase endpoints the way mod-search does:
# many parallel POSTs, each covering a sub-range of the record-id space. Reports wall-clock
# and requests/sec so you can A/B a fix (vert.x version, -instances, shared HttpClient, ...)
# in minutes instead of a full reindex.
#
# It partitions the UUID space into RANGES slices (by the first 32 bits) and fires them with
# CONCURRENCY parallel workers, mirroring the real merge-phase call pattern.
#
# Usage:
#   ./reindex-load-test.sh [publish|export] [RANGES] [CONCURRENCY]
#
# Env:
#   BASE_URL    module or Okapi base (default: http://localhost:8081)
#   TENANT      tenant id (default: test)
#   OKAPI_URL   value sent as X-Okapi-Url; required for the consortium lookup the endpoint
#               performs. Direct-to-module testing needs a stub returning {"userTenants":[]}
#               (see docker/perf/README.md). Default: ${BASE_URL}
#   TOKEN       optional X-Okapi-Token
#   RECORD_TYPE instance|item|holdings (default: instance)
#
# Examples:
#   BASE_URL=http://localhost:8081 TENANT=test ./reindex-load-test.sh publish 256 32
#   BASE_URL=https://okapi.my-test-env TENANT=diku TOKEN=$TOKEN ./reindex-load-test.sh export 256 32
#
set -euo pipefail

MODE="${1:-publish}"          # publish | export
RANGES="${2:-256}"            # number of id sub-ranges (≈ number of requests)
CONCURRENCY="${3:-32}"        # parallel in-flight requests
BASE_URL="${BASE_URL:-http://localhost:8081}"
TENANT="${TENANT:-test}"
OKAPI_URL="${OKAPI_URL:-$BASE_URL}"
TOKEN="${TOKEN:-}"
RECORD_TYPE="${RECORD_TYPE:-instance}"
ENDPOINT="${BASE_URL}/inventory-reindex-records/${MODE}"

echo ">> ${MODE} → ${ENDPOINT}"
echo ">> tenant=${TENANT} ranges=${RANGES} concurrency=${CONCURRENCY} recordType=${RECORD_TYPE}"

# uuidgen-free UUID v4-ish generator for the range 'id' field
gen_uuid() { printf '%08x-%04x-4%03x-%04x-%012x' \
  $((RANDOM*RANDOM & 0xffffffff)) $((RANDOM & 0xffff)) $((RANDOM & 0xfff)) \
  $(( (RANDOM & 0x3fff) | 0x8000 )) $((RANDOM*RANDOM*RANDOM & 0xffffffffffff)); }

STEP=$(( 4294967296 / RANGES ))   # 2^32 / RANGES

# Build the work list: one "from to" per line covering the full id space contiguously.
WORK="$(mktemp)"
for ((i=0; i<RANGES; i++)); do
  lo=$(( i * STEP ))
  hi=$(( (i+1) * STEP - 1 )); (( i == RANGES-1 )) && hi=4294967295
  printf '%08x-0000-0000-0000-000000000000 %08x-ffff-ffff-ffff-ffffffffffff\n' "$lo" "$hi"
done > "$WORK"

HDR=(-H "Content-Type: application/json" -H "X-Okapi-Tenant: ${TENANT}" -H "X-Okapi-Url: ${OKAPI_URL}")
[[ -n "$TOKEN" ]] && HDR+=(-H "X-Okapi-Token: ${TOKEN}")

export ENDPOINT RECORD_TYPE
printf '%s\n' "${HDR[@]}" >/dev/null  # (HDR captured below via function)

do_one() {
  local from="$1" to="$2"
  local id; id="$(gen_uuid)"
  local body
  body=$(printf '{"id":"%s","traceId":"%s","recordType":"%s","recordIdsRange":{"from":"%s","to":"%s"}}' \
    "$id" "$(gen_uuid)" "$RECORD_TYPE" "$from" "$to")
  curl -s -o /dev/null -w '%{http_code} %{time_total}\n' -X POST "$ENDPOINT" \
    -H "Content-Type: application/json" -H "X-Okapi-Tenant: ${TENANT}" -H "X-Okapi-Url: ${OKAPI_URL}" \
    ${TOKEN:+-H "X-Okapi-Token: ${TOKEN}"} \
    -d "$body"
}
export -f do_one gen_uuid
export TENANT OKAPI_URL TOKEN

RESULTS="$(mktemp)"
START=$(date +%s.%N)
# xargs -P drives CONCURRENCY parallel curl workers
xargs -P "$CONCURRENCY" -n 2 bash -c 'do_one "$@"' _ < "$WORK" > "$RESULTS"
END=$(date +%s.%N)

ELAPSED=$(awk "BEGIN{printf \"%.3f\", $END-$START}")
OK=$(awk '$1>=200 && $1<300' "$RESULTS" | wc -l | tr -d ' ')
BAD=$(awk '$1<200 || $1>=300' "$RESULTS" | wc -l | tr -d ' ')
AVG=$(awk '{s+=$2; n++} END{if(n)printf "%.4f", s/n}' "$RESULTS")
P95=$(awk '{print $2}' "$RESULTS" | sort -n | awk '{a[NR]=$1} END{if(NR)printf "%.4f", a[int(NR*0.95)]}')
RPS=$(awk "BEGIN{if($ELAPSED>0)printf \"%.1f\", $RANGES/$ELAPSED}")

echo "------------------------------------------------------------"
echo "requests:            ${RANGES}  (ok=${OK} non2xx=${BAD})"
echo "wall clock:          ${ELAPSED}s"
echo "throughput:          ${RPS} req/s   @ concurrency ${CONCURRENCY}"
echo "latency avg / p95:   ${AVG}s / ${P95}s"
echo "------------------------------------------------------------"
echo "non-2xx breakdown:"; awk '{print $1}' "$RESULTS" | sort | uniq -c | sort -rn | head
rm -f "$WORK" "$RESULTS"

