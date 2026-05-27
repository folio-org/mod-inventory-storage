#!/usr/bin/env bash
set -euo pipefail

require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "Missing required environment variable: ${name}" >&2
    exit 2
  fi
}

run_with_password() {
  local password="$1"
  shift
  if [[ -n "$password" ]]; then
    PGPASSWORD="$password" "$@"
  else
    "$@"
  fi
}

for cmd in pg_dump pg_restore psql createdb dropdb; do
  command -v "$cmd" >/dev/null || {
    echo "Required command not found: $cmd" >&2
    exit 2
  }
done

require_env REMOTE_PGHOST
require_env REMOTE_PGDATABASE
require_env REMOTE_PGUSER
require_env REMOTE_PGPASSWORD
require_env TENANT_SCHEMA

REMOTE_PGPORT="${REMOTE_PGPORT:-5432}"
LOCAL_PGHOST="${LOCAL_PGHOST:-localhost}"
LOCAL_PGPORT="${LOCAL_PGPORT:-5432}"
LOCAL_PGUSER="${LOCAL_PGUSER:-${USER}}"
LOCAL_PGPASSWORD="${LOCAL_PGPASSWORD:-}"
LOCAL_PGDATABASE="${LOCAL_PGDATABASE:-modinvstor_perf}"
PG_RESTORE_JOBS="${PG_RESTORE_JOBS:-4}"
RESET_LOCAL_DB="${RESET_LOCAL_DB:-false}"
EXCLUDE_AUDIT_TABLES="${EXCLUDE_AUDIT_TABLES:-true}"
DUMP_FILE="${DUMP_FILE:-/tmp/${TENANT_SCHEMA}_$(date +%Y%m%d_%H%M%S).dump}"

PG_DUMP_EXCLUDE_ARGS=()
if [[ "$EXCLUDE_AUDIT_TABLES" == "true" ]]; then
  PG_DUMP_EXCLUDE_ARGS+=(--exclude-table="${TENANT_SCHEMA}.audit_*")
fi

echo "Dumping schema ${TENANT_SCHEMA} from ${REMOTE_PGHOST}:${REMOTE_PGPORT}/${REMOTE_PGDATABASE}"
if [[ "$EXCLUDE_AUDIT_TABLES" == "true" ]]; then
  echo "Excluding audit tables matching ${TENANT_SCHEMA}.audit_*"
fi
run_with_password "$REMOTE_PGPASSWORD" \
  pg_dump \
    --host "$REMOTE_PGHOST" \
    --port "$REMOTE_PGPORT" \
    --username "$REMOTE_PGUSER" \
    --dbname "$REMOTE_PGDATABASE" \
    --schema "$TENANT_SCHEMA" \
    "${PG_DUMP_EXCLUDE_ARGS[@]}" \
    --format custom \
    --no-owner \
    --no-acl \
    --file "$DUMP_FILE"

db_exists() {
  run_with_password "$LOCAL_PGPASSWORD" \
    psql \
      --host "$LOCAL_PGHOST" \
      --port "$LOCAL_PGPORT" \
      --username "$LOCAL_PGUSER" \
      --dbname postgres \
      --set "db=${LOCAL_PGDATABASE}" \
      --tuples-only \
      --no-align \
      --quiet \
      --command "SELECT 1 FROM pg_database WHERE datname = :'db';" \
    | grep -qx "1"
}

if db_exists; then
  if [[ "$RESET_LOCAL_DB" != "true" ]]; then
    echo "Local database ${LOCAL_PGDATABASE} already exists." >&2
    echo "Set RESET_LOCAL_DB=true to drop and recreate it." >&2
    exit 2
  fi

  echo "Dropping local database ${LOCAL_PGDATABASE}"
  run_with_password "$LOCAL_PGPASSWORD" \
    dropdb --host "$LOCAL_PGHOST" --port "$LOCAL_PGPORT" --username "$LOCAL_PGUSER" "$LOCAL_PGDATABASE"
fi

echo "Creating local database ${LOCAL_PGDATABASE}"
run_with_password "$LOCAL_PGPASSWORD" \
  createdb --host "$LOCAL_PGHOST" --port "$LOCAL_PGPORT" --username "$LOCAL_PGUSER" "$LOCAL_PGDATABASE"

echo "Restoring ${DUMP_FILE} into ${LOCAL_PGHOST}:${LOCAL_PGPORT}/${LOCAL_PGDATABASE}"
run_with_password "$LOCAL_PGPASSWORD" \
  pg_restore \
    --host "$LOCAL_PGHOST" \
    --port "$LOCAL_PGPORT" \
    --username "$LOCAL_PGUSER" \
    --dbname "$LOCAL_PGDATABASE" \
    --jobs "$PG_RESTORE_JOBS" \
    --no-owner \
    --no-acl \
    "$DUMP_FILE"

echo "Restore complete."
echo "Dump file: ${DUMP_FILE}"
echo "Schema: ${TENANT_SCHEMA}"
echo "Local database: ${LOCAL_PGDATABASE}"
