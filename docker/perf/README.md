# Reindex merge-phase performance test harness

Cheap, repeatable reproduction of the reindex **merge-phase** API throughput (the
`/inventory-reindex-records/publish` and `/inventory-reindex-records/export` endpoints),
so fixes can be A/B-tested in minutes instead of running a full reindex.

See `../../docs/reindex-merge-phase-perf-investigation.md` for the why. This folder is the
how. Everything here is **test tooling** (no production code).

Scripts:
- `fill-instances.sh` — bulk-load minimal Instance rows (data to publish/export).
- `reindex-load-test.sh` — fire many parallel range requests; report req/s & latency.
- `okapi-stub.py` — minimal `/user-tenants` stub so endpoints work without a real Okapi.

> Note: `export` also needs S3; the bundled docker infra has **no** S3/MinIO, so in the
> pure-docker setup test **`publish`** (and the shared HTTP path). Use your real lower-data
> environment (Okapi + S3) to compare `publish` vs `export` directly.

---

## 1. Bring up infra + module (docker)

```bash
cd docker
# build the module fat jar
( cd .. && mvn clean package -DskipTests )
# start postgres + kafka + the module
docker compose -f app-docker-compose.yml up -d --build
docker compose -f app-docker-compose.yml logs -f mod-inventory-storage   # wait for "Listening port 8081"
```

## 2. Initialise a tenant (creates the DB schema)

```bash
curl -s -XPOST http://localhost:8081/_/tenant \
  -H 'Content-Type: application/json' -H 'X-Okapi-Tenant: test' \
  -H 'X-Okapi-Url: http://localhost:8081' \
  -d '{"module_to":"mod-inventory-storage-30.1.0"}'
# RMB tenant init is async; poll the returned id until complete=true:
#   curl http://localhost:8081/_/tenant/<id>?wait=60000 -H 'X-Okapi-Tenant: test'
```

## 3. Fill data

```bash
cd docker/perf
chmod +x fill-instances.sh reindex-load-test.sh
# ~1M minimal instances (adjust to match the environment you are comparing against)
./fill-instances.sh 1000000 test
```

## 4. Start the consortium stub (direct-to-module only)

The endpoints look up consortium membership via `X-Okapi-Url/user-tenants`. Without Okapi,
run the stub and point the load test at it:

```bash
python3 okapi-stub.py 9131 &       # GET /user-tenants -> empty (non-consortium)
```

## 5. Run the load test (the A/B harness)

```bash
# publish: 256 ranges, 32 concurrent — mirrors mod-search's parallel merge calls
BASE_URL=http://localhost:8081 OKAPI_URL=http://localhost:9131 TENANT=test \
  ./reindex-load-test.sh publish 256 32
```

Output reports **req/s**, avg/p95 latency and non-2xx counts. Watch DB load (pgAdmin /
`docker stats`) at the same time — the regression signature is *high* module CPU on a
*single* event loop with the DB nearly idle.

Against your real test environment (publish **and** export, real comparison):

```bash
BASE_URL=https://okapi.<your-test-env> TENANT=<tenant> TOKEN=<token> \
  ./reindex-load-test.sh publish 256 32
BASE_URL=https://okapi.<your-test-env> TENANT=<tenant> TOKEN=<token> \
  ./reindex-load-test.sh export  256 32
```

---

## 6. Cheap A/B experiments (run §5 before & after each change)

| Experiment | How | Confirms / refutes |
|---|---|---|
| **HTTP concurrency (instances)** | add `-instances N` to the jar args (see §7) and restart | whether a single event loop is the ceiling (H-A) |
| **vert.x version bisect** | rebuild with `mvn ... -Dvertx.version=5.0.8` vs `5.1.0`, redeploy | vert.x HTTP-layer regression (H-A) |
| **shared HttpClient** | apply the §7 code change, rebuild | per-request `createHttpClient` cost (H-B) |
| **kafka-clients bisect** | `-Dvertx.version=5.0.8` (→ kafka 3.7.1) vs current (4.2.0), compare **publish only** | Kafka producer cost (H-D) |

Profile one run for the *where* (decisive, before bisecting):

```bash
# async-profiler wall-clock flame graph of the module JVM during a load run
java -jar async-profiler.jar -d 30 -e wall -f /tmp/reindex.html <module-PID>
```

---

## 7. HTTP-throughput levers (project-level, no RMB fork needed)

These are the concrete knobs available to us; see the investigation doc §7 for rationale.

**A. Increase HTTP event loops — deploy multiple `RestVerticle` instances.**
Today the fat jar is launched as `java -jar ...-fat.jar -Dhttp.port=%p` with **no
`-instances`**, so vert.x deploys **one** `RestVerticle` ⇒ a single event-loop thread serves
*all* HTTP requests. The vert.x 5 launcher accepts `-instances`:

```jsonc
// descriptors/DeploymentDescriptor-template.json
"exec": "java -jar ../mod-inventory-storage/target/mod-inventory-storage-fat.jar -Dhttp.port=%p -instances 4"
```
or locally via the docker container command / `JAVA_OPTIONS`. Start with `instances ≈ vCPU`.

**B. Reuse a shared `HttpClient` instead of one per request.**
`InstanceService`/`ItemService`/`HoldingsService` constructors call
`vertxContext.owner().createHttpClient()` on every request and never close it. Replace with a
single shared client/`WebClient` (e.g. context-stored singleton created in `InitApiImpl`,
like the existing `ConsortiumDataCache` client). Removes a per-request allocation on the path
shared by both endpoints.

**C. Tune `HttpServerOptions` in the InitAPI hook.**
RMB exposes `org.folio.rest.RestVerticle.getHttpServerOptions()`, modifiable from the
`InitAPI` implementation (`InitApiImpl.init`). Use it to adjust server-side limits if
profiling points there, e.g.:
```java
// in InitApiImpl.init(...), before completing the handler
RestVerticle.getHttpServerOptions().setCompressionSupported(true); // already default
// e.g. tune accept backlog / idle timeout if warranted by profiling
```

**D. vert.x runtime sizing via JVM properties (no code change).**
Pass through `JAVA_OPTIONS`, e.g. `-Dvertx.eventLoopPoolSize=<n>` (only helps once there are
multiple verticle instances), and keep an eye on the worker pool if any blocking creeps in.

> Recommended order: measure (§5/profile) → **A** (instances) and **B** (shared client) are
> the cheapest high-leverage changes → re-measure → then version bisects for root cause.

