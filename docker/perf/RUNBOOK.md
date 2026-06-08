# RUNBOOK — bring up env, fill data, run the reindex load test

Copy/paste each step in order. Run **all commands from the `docker/` directory** unless a
step says otherwise (that's where `.env` lives, which sets the compose project name).

Collect the outputs marked **📋 COLLECT** and send them back.

---

## Step 0 — prerequisites & location

```bash
# from the repo root
cd "$(git rev-parse --show-toplevel)"
docker --version && docker compose version && java -version
```

## Step 1 — build the module fat jar

```bash
mvn clean package -DskipTests
```

## Step 2 — start infrastructure + module

```bash
cd docker
docker compose -f app-docker-compose.yml up -d --build
```

Wait until the module is listening (Ctrl-C to stop following once you see it):

```bash
docker compose -f app-docker-compose.yml logs -f mod-inventory-storage | grep -m1 "Listening port"
```

Health check (should print `{"status":"UP"...}` or HTTP 200):

```bash
curl -s -o /dev/null -w "module health: %{http_code}\n" http://localhost:8081/admin/health
```

## Step 3 — start the consortium stub on the module's docker network

The endpoints call `X-Okapi-Url/user-tenants`; this stub returns an empty (non-consortium)
result so the calls succeed without a real Okapi. The module reaches it by name `okapi-stub`.

```bash
# discover the compose network name
NET=$(docker network ls --format '{{.Name}}' | grep mod-inventory-storage-local | head -1)
echo "network: $NET"

docker rm -f okapi-stub 2>/dev/null || true
docker run -d --name okapi-stub --network "$NET" \
  -v "$PWD/perf/okapi-stub.py:/okapi-stub.py:ro" \
  python:3-alpine python -u /okapi-stub.py 9131

docker logs okapi-stub   # should say: listening on :9131
```

## Step 4 — initialise the tenant `test` (creates the DB schema)

```bash
curl -s -D - -o /dev/null -XPOST http://localhost:8081/_/tenant \
  -H 'Content-Type: application/json' \
  -H 'X-Okapi-Tenant: test' \
  -H 'X-Okapi-Url: http://okapi-stub:9131' \
  -d '{"module_to":"mod-inventory-storage-30.1.0"}'
```

If the response has an `id` / `Location` (async job), wait for it to finish:

```bash
TID=$(curl -s -XPOST http://localhost:8081/_/tenant \
  -H 'Content-Type: application/json' -H 'X-Okapi-Tenant: test' \
  -H 'X-Okapi-Url: http://okapi-stub:9131' \
  -d '{"module_to":"mod-inventory-storage-30.1.0"}' \
  | sed -n 's/.*"id"[ :]*"\([^"]*\)".*/\1/p')
echo "tenant job id: ${TID:-<none/synchronous>}"
[ -n "$TID" ] && curl -s "http://localhost:8081/_/tenant/$TID?wait=60000" -H 'X-Okapi-Tenant: test'; echo
```

Verify the schema exists:

```bash
docker compose -f app-docker-compose.yml exec -T postgres \
  psql -U folio_admin -d folio -c "\dn" | grep test_mod_inventory_storage
```

## Step 5 — fill data (1,000,000 instances; adjust to match your comparison env)

```bash
chmod +x perf/fill-instances.sh perf/reindex-load-test.sh
COMPOSE=app-docker-compose.yml perf/fill-instances.sh 1000000 test
```

**📋 COLLECT:** the final `total_instances` count printed.

> Troubleshooting: if the INSERT errors on a missing NOT NULL column, inspect the table and
> tell me the columns:
> `docker compose -f app-docker-compose.yml exec -T postgres psql -U folio_admin -d folio -c "\d test_mod_inventory_storage.instance"`

## Step 6 — baseline load test (publish)

Open a second terminal to watch resource usage during the run:

```bash
# terminal B
docker stats --format 'table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}'
```

```bash
# terminal A (from docker/)
BASE_URL=http://localhost:8081 OKAPI_URL=http://okapi-stub:9131 TENANT=test \
  perf/reindex-load-test.sh publish 256 32
```

**📋 COLLECT:**
- the full summary block (requests / wall clock / throughput / latency / non-2xx breakdown);
- the peak `mod-inventory-storage` CPU% and the `postgres` CPU% from terminal B during the run.

> If you see many non-2xx: grab a few error bodies for me:
> `curl -s -XPOST http://localhost:8081/inventory-reindex-records/publish -H 'Content-Type: application/json' -H 'X-Okapi-Tenant: test' -H 'X-Okapi-Url: http://okapi-stub:9131' -d '{"id":"11111111-1111-1111-1111-111111111111","recordType":"instance","recordIdsRange":{"from":"00000000-0000-0000-0000-000000000000","to":"ffffffff-ffff-ffff-ffff-ffffffffffff"}}' -i | head -40`

## Step 7 — A/B #1: HTTP concurrency (the cheapest high-leverage test)

Re-run the module with **4 RestVerticle instances** and repeat the load test.

`docker compose run --service-ports` doesn't reliably bind ports, so we use `docker run`
directly on the already-built image. `run-java.sh` (the image entrypoint) forwards any extra
args straight to the Java program, so `-instances 4` is passed correctly.

```bash
# stop compose-managed instance so port 8081 is free
docker compose -f app-docker-compose.yml stop mod-inventory-storage

# read env from .env
set -a; source .env; set +a
NET=$(docker network ls --format '{{.Name}}' | grep mod-inventory-storage-local | head -1)

docker rm -f mis-i4 2>/dev/null || true
docker run -d --name mis-i4 \
  --network "$NET" \
  -p 8081:8081 \
  -e ENV="$ENV" \
  -e KAFKA_HOST="$KAFKA_HOST" \
  -e KAFKA_PORT="$KAFKA_PORT" \
  -e REPLICATION_FACTOR="$REPLICATION_FACTOR" \
  -e DB_HOST="$DB_HOST" \
  -e DB_PORT=5432 \
  -e DB_DATABASE="$DB_DATABASE" \
  -e DB_USERNAME="$DB_USERNAME" \
  -e DB_PASSWORD="$DB_PASSWORD" \
  dev.folio/mod-inventory-storage:latest \
  -instances 4

# wait for start, then health check
sleep 10
curl -s -o /dev/null -w "i4 health: %{http_code}\n" http://localhost:8081/admin/health
docker logs mis-i4 2>&1 | grep -E 'Listening|ERROR|WARN' | head -10
```

```bash
# same load test as Step 6
BASE_URL=http://localhost:8081 OKAPI_URL=http://okapi-stub:9131 TENANT=test \
  perf/reindex-load-test.sh publish 256 32
```

**📋 COLLECT:** the summary block again + peak CPU. (We compare req/s vs Step 6.)

Restore the single-instance container afterwards:

```bash
docker rm -f mis-i4 2>/dev/null || true
docker compose -f app-docker-compose.yml up -d mod-inventory-storage
sleep 8; curl -s -o /dev/null -w "restored health: %{http_code}\n" http://localhost:8081/admin/health
```

## Step 8 — (optional) A/B #2: vert.x version bisect

Rebuild on the pre-Apr-9 vert.x (which also pins kafka-clients 3.7.1), redeploy, retest.

```bash
cd "$(git rev-parse --show-toplevel)"
mvn clean package -DskipTests -Dvertx.version=5.0.8
cd docker
docker compose -f app-docker-compose.yml up -d --build mod-inventory-storage
sleep 8; curl -s -o /dev/null -w "health: %{http_code}\n" http://localhost:8081/admin/health
BASE_URL=http://localhost:8081 OKAPI_URL=http://okapi-stub:9131 TENANT=test \
  perf/reindex-load-test.sh publish 256 32
```

**📋 COLLECT:** the summary block (compare req/s vs Step 6 on the current vert.x).

Restore current build when done: `mvn clean package -DskipTests` (root) then rebuild image.

## Step 9 — teardown (when finished)

```bash
cd docker
docker rm -f okapi-stub mis-i4 2>/dev/null || true
docker compose -f app-docker-compose.yml down -v
```

---

### What to send back
For each load test you ran (Step 6, Step 7, optionally Step 8): the **summary block** and the
**peak module CPU% / postgres CPU%**. That lets us see whether throughput is event-loop-bound
(module CPU pegged on one core, DB idle) and how much `-instances 4` and the vert.x bisect move
the number.




