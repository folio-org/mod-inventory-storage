#!/usr/bin/env bash
#
# fill-instances.sh — bulk-load minimal Instance rows for reindex throughput testing.
#
# The reindex read path (InstanceRepository.buildReindexInstancesSql) only needs rows in
# the `instance` table selected by `id >= from AND id <= to`, with `jsonb->>'source'`
# present (used by the NOT LIKE 'CONSORTIUM-%' filter). It LEFT JOINs bound_with/holdings,
# so those can stay empty. We therefore insert (id, jsonb) directly and bypass triggers/FKs
# with session_replication_role=replica for speed. This is TEST tooling, not production.
#
# Usage:
#   ./fill-instances.sh [COUNT] [TENANT]
# Env (override as needed):
#   PG_SERVICE  docker compose service name for postgres (default: postgres)
#   COMPOSE     compose file (default: infra-docker-compose.yml)
#   DB_DATABASE / DB_USERNAME  (defaults match docker/.env: folio / folio_admin)
#
# Example:
#   ./fill-instances.sh 2000000 test
#
set -euo pipefail

COUNT="${1:-1000000}"
TENANT="${2:-test}"
PG_SERVICE="${PG_SERVICE:-postgres}"
COMPOSE="${COMPOSE:-infra-docker-compose.yml}"
DB_DATABASE="${DB_DATABASE:-folio}"
DB_USERNAME="${DB_USERNAME:-folio_admin}"
SCHEMA="${TENANT}_mod_inventory_storage"

echo ">> Inserting ${COUNT} instances into ${SCHEMA}.instance (db=${DB_DATABASE}) ..."

# NOTE: column list is the RMB default (id, jsonb). If your tenant's table has extra
# NOT NULL columns without defaults, add them here or inspect with:  \d ${SCHEMA}.instance
docker compose -f "${COMPOSE}" exec -T "${PG_SERVICE}" \
  psql -v ON_ERROR_STOP=1 -U "${DB_USERNAME}" -d "${DB_DATABASE}" <<SQL
SET session_replication_role = replica;  -- skip triggers & FK checks for fast bulk load
INSERT INTO ${SCHEMA}.instance (id, jsonb)
SELECT gen_random_uuid(),
       jsonb_build_object(
         'source', 'FOLIO',
         'title', 'perf-' || g,
         'instanceTypeId', '30fcc8e7-a019-43f4-b642-2edc389f4501'
       )
FROM generate_series(1, ${COUNT}) AS g;
SET session_replication_role = origin;
ANALYZE ${SCHEMA}.instance;
SELECT count(*) AS total_instances FROM ${SCHEMA}.instance;
SQL

echo ">> Done."

