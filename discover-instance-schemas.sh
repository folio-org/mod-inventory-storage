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
  discover-instance-schemas.sh <instance-id> [<instance-id> ...]

Required environment:
  REMOTE_PGHOST
  REMOTE_PGPORT
  REMOTE_PGDATABASE
  REMOTE_PGUSER
  REMOTE_PGPASSWORD

Optional environment:
  SCHEMA_PATTERN=%\_mod\_inventory\_storage
  DISCOVERY_SQL_FILE=/tmp/instance-schema-discovery.sql
  DISCOVERY_OUTPUT_DIR=/tmp

This searches tenant inventory-storage schemas for the supplied instance ids and
reports where the instance, holdings, items, and bound-with rows are found.
It does not modify the database.
USAGE
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

command -v psql >/dev/null || {
  echo "Required command not found: psql" >&2
  exit 2
}

require_env REMOTE_PGHOST
require_env REMOTE_PGPORT
require_env REMOTE_PGDATABASE
require_env REMOTE_PGUSER
require_env REMOTE_PGPASSWORD

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

SCHEMA_PATTERN="${SCHEMA_PATTERN:-%\\_mod\\_inventory\\_storage}"
DISCOVERY_SQL_FILE="${DISCOVERY_SQL_FILE:-/tmp/instance-schema-discovery.sql}"
DISCOVERY_OUTPUT_DIR="${DISCOVERY_OUTPUT_DIR:-/tmp}"
mkdir -p "$DISCOVERY_OUTPUT_DIR"

cat > "$DISCOVERY_SQL_FILE" <<'SQL'
\set ON_ERROR_STOP on
\pset pager off
\echo === Discovering schemas for instance :instance_id ===
WITH schemas AS (
  SELECT n.nspname AS schema_name
  FROM pg_namespace n
  WHERE n.nspname LIKE :'schema_pattern' ESCAPE '\'
    AND to_regclass(format('%I.instance', n.nspname)) IS NOT NULL
    AND to_regclass(format('%I.holdings_record', n.nspname)) IS NOT NULL
    AND to_regclass(format('%I.item', n.nspname)) IS NOT NULL
    AND to_regclass(format('%I.bound_with_part', n.nspname)) IS NOT NULL
),
generated AS (
  SELECT string_agg(
    format($fmt$
SELECT
  %L::text AS schema_name,
  EXISTS (
    SELECT 1
    FROM %I.instance
    WHERE id = %L::uuid
  ) AS instance_exists,
  (
    SELECT jsonb ->> 'source'
    FROM %I.instance
    WHERE id = %L::uuid
    LIMIT 1
  ) AS instance_source,
  (
    SELECT jsonb ->> 'title'
    FROM %I.instance
    WHERE id = %L::uuid
    LIMIT 1
  ) AS instance_title,
  (
    SELECT count(*)
    FROM %I.holdings_record
    WHERE instanceid = %L::uuid
  ) AS holdings_by_instanceid_column,
  (
    SELECT count(*)
    FROM %I.holdings_record
    WHERE jsonb ->> 'instanceId' = %L
  ) AS holdings_by_instanceid_json,
  (
    SELECT count(*)
    FROM %I.holdings_record
    WHERE instanceid = %L::uuid
      AND NOT COALESCE((jsonb ->> 'discoverySuppress')::boolean, false)
  ) AS visible_holdings_by_instanceid_column,
  (
    SELECT count(*)
    FROM %I.item item
      JOIN %I.holdings_record hr ON item.holdingsrecordid = hr.id
    WHERE hr.instanceid = %L::uuid
  ) AS direct_items_by_holdings_column,
  (
    SELECT count(*)
    FROM %I.item item
      JOIN %I.holdings_record hr ON item.holdingsrecordid = hr.id
    WHERE hr.jsonb ->> 'instanceId' = %L
  ) AS direct_items_by_holdings_json,
  (
    SELECT count(*)
    FROM %I.item item
      JOIN %I.holdings_record hr ON item.holdingsrecordid = hr.id
    WHERE hr.instanceid = %L::uuid
      AND NOT COALESCE((hr.jsonb ->> 'discoverySuppress')::boolean, false)
      AND NOT COALESCE((item.jsonb ->> 'discoverySuppress')::boolean, false)
  ) AS visible_direct_items_by_holdings_column,
  (
    SELECT count(*)
    FROM %I.bound_with_part bwp
      JOIN %I.holdings_record hr ON bwp.holdingsrecordid = hr.id
    WHERE hr.instanceid = %L::uuid
  ) AS bound_with_parts_by_holdings_column,
  (
    SELECT count(*)
    FROM %I.bound_with_part bwp
      JOIN %I.holdings_record hr ON bwp.holdingsrecordid = hr.id
      JOIN %I.item item ON item.id = bwp.itemid
    WHERE hr.instanceid = %L::uuid
  ) AS bound_with_items_by_holdings_column
$fmt$,
      schema_name,
      schema_name, :'instance_id',
      schema_name, :'instance_id',
      schema_name, :'instance_id',
      schema_name, :'instance_id',
      schema_name, :'instance_id',
      schema_name, :'instance_id',
      schema_name, schema_name, :'instance_id',
      schema_name, schema_name, :'instance_id',
      schema_name, schema_name, :'instance_id',
      schema_name, schema_name, :'instance_id',
      schema_name, schema_name, schema_name, :'instance_id'
    ),
    E'\nUNION ALL\n'
  ) AS sql
  FROM schemas
)
SELECT COALESCE(
  sql || E'\nORDER BY instance_exists DESC, holdings_by_instanceid_column DESC, direct_items_by_holdings_column DESC, bound_with_items_by_holdings_column DESC, schema_name;',
  'SELECT NULL::text AS schema_name, false AS instance_exists, NULL::text AS instance_source, NULL::text AS instance_title, 0::bigint AS holdings_by_instanceid_column, 0::bigint AS holdings_by_instanceid_json, 0::bigint AS visible_holdings_by_instanceid_column, 0::bigint AS direct_items_by_holdings_column, 0::bigint AS direct_items_by_holdings_json, 0::bigint AS visible_direct_items_by_holdings_column, 0::bigint AS bound_with_parts_by_holdings_column, 0::bigint AS bound_with_items_by_holdings_column WHERE false;'
)
FROM generated
\gexec
SQL

for instance_id in "$@"; do
  output_file="${DISCOVERY_OUTPUT_DIR}/instance-schema-discovery-${instance_id}.txt"
  echo "Writing schema discovery output to ${output_file}"
  PGCONNECT_TIMEOUT="${PGCONNECT_TIMEOUT:-5}" PGPASSWORD="$REMOTE_PGPASSWORD" \
    psql \
      --host "$REMOTE_PGHOST" \
      --port "$REMOTE_PGPORT" \
      --username "$REMOTE_PGUSER" \
      --dbname "$REMOTE_PGDATABASE" \
      --set "instance_id=${instance_id}" \
      --set "schema_pattern=${SCHEMA_PATTERN}" \
      --file "$DISCOVERY_SQL_FILE" \
      > "$output_file" 2>&1
done

echo "Discovery SQL file: ${DISCOVERY_SQL_FILE}"
