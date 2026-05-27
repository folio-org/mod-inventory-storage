#!/usr/bin/env bash
set -euo pipefail

require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "Missing required environment variable: ${name}" >&2
    exit 2
  fi
}

usage() {
  cat <<'USAGE'
Usage:
  benchmark-instance-summary-remote.sh <instance-id> [<instance-id> ...]

Required environment:
  REMOTE_PGHOST
  REMOTE_PGPORT
  REMOTE_PGDATABASE
  REMOTE_PGUSER
  REMOTE_PGPASSWORD
  TENANT_SCHEMA

Optional environment:
  SKIP_SUPPRESSED_FROM_DISCOVERY_RECORDS=true
  STATEMENT_TIMEOUT=30s
  LOCK_TIMEOUT=1s
  IDLE_TIMEOUT=30s
  INCLUDE_RELATIONSHIP_COUNTS=true
  INCLUDE_ITEM_ELECTRONIC_ACCESS=false
  BENCHMARK_SQL_VERSION=004
  BENCHMARK_SQL_FILE=/tmp/instance-summary-benchmark-004.sql
  BENCHMARK_OUTPUT_DIR=/tmp
  SQL_FUNCTION_FILE=mod-inventory-storage-server/src/main/resources/templates/db_scripts/instance/createInstanceSummaryFunction.sql

This runs the summary SQL inline in a read-only transaction. It does not create
or replace any remote database objects. The generated SQL file is kept for
inspection and can be versioned with BENCHMARK_SQL_VERSION.
USAGE
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

for cmd in awk psql; do
  command -v "$cmd" >/dev/null || {
    echo "Required command not found: $cmd" >&2
    exit 2
  }
done

require_env REMOTE_PGHOST
require_env REMOTE_PGPORT
require_env REMOTE_PGDATABASE
require_env REMOTE_PGUSER
require_env REMOTE_PGPASSWORD
require_env TENANT_SCHEMA

if [[ ! "$TENANT_SCHEMA" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
  echo "TENANT_SCHEMA must be a simple PostgreSQL identifier: ${TENANT_SCHEMA}" >&2
  exit 2
fi

if [[ "$#" -eq 0 ]]; then
  echo "At least one instance id is required." >&2
  usage >&2
  exit 2
fi

for instance_id in "$@"; do
  if [[ ! "$instance_id" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$ ]]; then
    echo "Invalid UUID: ${instance_id}" >&2
    exit 2
  fi
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_FUNCTION_FILE="${SQL_FUNCTION_FILE:-${SCRIPT_DIR}/mod-inventory-storage-server/src/main/resources/templates/db_scripts/instance/createInstanceSummaryFunction.sql}"
SKIP_SUPPRESSED_FROM_DISCOVERY_RECORDS="${SKIP_SUPPRESSED_FROM_DISCOVERY_RECORDS:-true}"
INCLUDE_ITEM_ELECTRONIC_ACCESS="${INCLUDE_ITEM_ELECTRONIC_ACCESS:-false}"
STATEMENT_TIMEOUT="${STATEMENT_TIMEOUT:-30s}"
LOCK_TIMEOUT="${LOCK_TIMEOUT:-1s}"
IDLE_TIMEOUT="${IDLE_TIMEOUT:-30s}"
INCLUDE_RELATIONSHIP_COUNTS="${INCLUDE_RELATIONSHIP_COUNTS:-true}"
BENCHMARK_SQL_VERSION="${BENCHMARK_SQL_VERSION:-004}"
BENCHMARK_SQL_FILE="${BENCHMARK_SQL_FILE:-/tmp/instance-summary-benchmark-${BENCHMARK_SQL_VERSION}.sql}"
BENCHMARK_OUTPUT_DIR="${BENCHMARK_OUTPUT_DIR:-/tmp}"

if [[ ! -f "$SQL_FUNCTION_FILE" ]]; then
  echo "SQL function file not found: ${SQL_FUNCTION_FILE}" >&2
  exit 2
fi

if [[ "$SKIP_SUPPRESSED_FROM_DISCOVERY_RECORDS" != "true" && "$SKIP_SUPPRESSED_FROM_DISCOVERY_RECORDS" != "false" ]]; then
  echo "SKIP_SUPPRESSED_FROM_DISCOVERY_RECORDS must be true or false." >&2
  exit 2
fi

if [[ "$INCLUDE_ITEM_ELECTRONIC_ACCESS" != "true" && "$INCLUDE_ITEM_ELECTRONIC_ACCESS" != "false" ]]; then
  echo "INCLUDE_ITEM_ELECTRONIC_ACCESS must be true or false." >&2
  exit 2
fi

if [[ "$INCLUDE_RELATIONSHIP_COUNTS" != "true" && "$INCLUDE_RELATIONSHIP_COUNTS" != "false" ]]; then
  echo "INCLUDE_RELATIONSHIP_COUNTS must be true or false." >&2
  exit 2
fi

mkdir -p "$BENCHMARK_OUTPUT_DIR"

SUMMARY_QUERY="$(
  awk -v schema="$TENANT_SCHEMA" '
    /AS \$function\$/ { in_body = 1; next }
    /^\$function\$/ { exit }
    in_body {
      gsub(/\$\{myuniversity\}_\$\{mymodule\}/, schema)
      gsub(/_instance_id/, ":'\''instance_id'\''::uuid")
      gsub(/_skip_suppressed_from_discovery_records/, ":'\''skip_suppressed'\''::boolean")
      gsub(/_include_item_electronic_access/, ":'\''include_item_electronic_access'\''::boolean")
      print
    }
  ' "$SQL_FUNCTION_FILE" | awk '
    { lines[NR] = $0 }
    END {
      if (NR == 0) {
        exit 1
      }
      sub(/;[[:space:]]*$/, "", lines[NR])
      for (i = 1; i <= NR; i++) {
        print lines[i]
      }
    }
  '
)"

cat > "$BENCHMARK_SQL_FILE" <<SQL
\\set ON_ERROR_STOP on
\\echo === Instance :instance_id skipSuppressed=:skip_suppressed includeItemElectronicAccess=:include_item_electronic_access ===
BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout = :'statement_timeout';
SET LOCAL lock_timeout = :'lock_timeout';
SET LOCAL idle_in_transaction_session_timeout = :'idle_timeout';

SQL

if [[ "$INCLUDE_RELATIONSHIP_COUNTS" == "true" ]]; then
  cat >> "$BENCHMARK_SQL_FILE" <<SQL
\\echo --- RELATIONSHIP COUNTS ---
SELECT
  EXISTS (
    SELECT 1
    FROM ${TENANT_SCHEMA}.instance
    WHERE id = :'instance_id'::uuid
  ) AS instance_exists,
  (
    SELECT count(*)
    FROM ${TENANT_SCHEMA}.holdings_record
    WHERE instanceid = :'instance_id'::uuid
  ) AS holdings_by_instanceid_column,
  (
    SELECT count(*)
    FROM ${TENANT_SCHEMA}.holdings_record
    WHERE jsonb ->> 'instanceId' = :'instance_id'
  ) AS holdings_by_instanceid_json,
  (
    SELECT count(*)
    FROM ${TENANT_SCHEMA}.item item
      JOIN ${TENANT_SCHEMA}.holdings_record hr ON item.holdingsrecordid = hr.id
    WHERE hr.instanceid = :'instance_id'::uuid
  ) AS direct_items_by_holdings_column,
  (
    SELECT count(*)
    FROM ${TENANT_SCHEMA}.item item
      JOIN ${TENANT_SCHEMA}.holdings_record hr ON item.holdingsrecordid = hr.id
    WHERE hr.jsonb ->> 'instanceId' = :'instance_id'
  ) AS direct_items_by_holdings_json,
  (
    SELECT count(*)
    FROM ${TENANT_SCHEMA}.bound_with_part bwp
      JOIN ${TENANT_SCHEMA}.holdings_record hr ON bwp.holdingsrecordid = hr.id
    WHERE hr.instanceid = :'instance_id'::uuid
  ) AS bound_with_parts_by_holdings_column;

SQL
fi

cat >> "$BENCHMARK_SQL_FILE" <<SQL
\\echo --- EXPLAIN ANALYZE ---
EXPLAIN (ANALYZE, BUFFERS, SUMMARY, TIMING)
${SUMMARY_QUERY}
;

\\echo --- RESPONSE SHAPE ---
SELECT
  pg_column_size(summary) AS response_size_bytes,
  pg_size_pretty(pg_column_size(summary)::bigint) AS response_size,
  summary #>> '{visibility,instanceDiscoverySuppress}' AS instance_discovery_suppress,
  summary #>> '{visibility,hasAnyHoldings}' AS has_any_holdings,
  summary #>> '{visibility,hasVisibleHoldings}' AS has_visible_holdings,
  summary #>> '{visibility,hasAnyItems}' AS has_any_items,
  summary #>> '{visibility,hasVisibleItems}' AS has_visible_items,
  summary #>> '{visibility,allHoldingsSuppressed}' AS all_holdings_suppressed,
  jsonb_array_length(summary -> 'electronicAccess') AS electronic_access_count,
  jsonb_array_length(summary #> '{itemDerivedFields,materialTypes}') AS material_type_count
FROM (
${SUMMARY_QUERY}
) AS s(summary);

ROLLBACK;
SQL

for instance_id in "$@"; do
  output_file="${BENCHMARK_OUTPUT_DIR}/instance-summary-benchmark-${BENCHMARK_SQL_VERSION}-${instance_id}.txt"
  echo "Writing benchmark output to ${output_file}"
  PGCONNECT_TIMEOUT="${PGCONNECT_TIMEOUT:-5}" PGPASSWORD="$REMOTE_PGPASSWORD" \
    psql \
      --host "$REMOTE_PGHOST" \
      --port "$REMOTE_PGPORT" \
      --username "$REMOTE_PGUSER" \
      --dbname "$REMOTE_PGDATABASE" \
      --set "instance_id=${instance_id}" \
      --set "skip_suppressed=${SKIP_SUPPRESSED_FROM_DISCOVERY_RECORDS}" \
      --set "include_item_electronic_access=${INCLUDE_ITEM_ELECTRONIC_ACCESS}" \
      --set "statement_timeout=${STATEMENT_TIMEOUT}" \
      --set "lock_timeout=${LOCK_TIMEOUT}" \
      --set "idle_timeout=${IDLE_TIMEOUT}" \
      --file "$BENCHMARK_SQL_FILE" \
      > "$output_file" 2>&1
done

echo "Benchmark SQL file: ${BENCHMARK_SQL_FILE}"
