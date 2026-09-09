# Test Quality Improvement Plan — mod-inventory-storage

## Context

A JUnit 5 → JUnit 6 migration is in flight on `MODINVSTOR-1594` (currently ~82 
working-tree files). The framework bump itself is nearly done — the
test tree has **zero** JUnit 4 remnants left. That makes this the right moment to fix
the structural test problems the migration has exposed, before the diff lands and the
context is lost.

The suite is large (217 files, 41.8k LOC against 19.3k LOC of production code) and has
repeatedly cost stabilization tickets: `MODINVSTOR-964` (intermittent event assert
failures), `MODINVSTOR-982` (reduce test failures), and #1395 "Fix unstable test with
wrong assertions". `AwaitConfiguration.java:15` carries an in-code plea — *"Timeout was
gradually extended to try to alleviate instability. Attempts should be made to reduce
this value"* — which is the suite's flakiness history written down.

Intended outcome: one documented test architecture instead of two undocumented ones,
a measurably faster and less flaky build, and a written standard so new tests stop
adding to the drift.

## Decisions taken (confirmed with the user)

- **Standard + targeted rewrites.** Document conventions, fix infrastructure, hold
  new/touched tests to the standard, and additionally schedule deliberate rewrites of
  the worst offenders as their own tickets. No blanket rewrite of all 217 files.
- **All four pain areas in scope:** flakiness, runtime, readability/conventions,
  coverage gaps.
- **Adopt the FOLIO data-import test conventions standard with documented
  repo-specific deviations** (this repo is not formally in that skill's scope).

---

## Findings

### F1. Two incompatible test architectures coexist — the root cause of most else

|            | `org/folio/rest/api/` (legacy, surefire `*Test`)                                                                                                                                                         | `org/folio/rest/impl/` (modern, failsafe `*IT`)                                                                                      |
|------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------|
| Files      | 37 (21,281 LOC)                                                                                                                                                                                          | 40 (6,122 LOC)                                                                                                                       |
| Bootstrap  | `StorageTestSuite` — one JVM-wide Postgres/Kafka/S3/verticle, lazily started by whichever class runs first, guarded by unsynchronized `private static boolean running`, torn down by a JVM shutdown hook | `BaseIntegrationTest.beforeAll` — **no reuse guard**: fresh `RestVerticle` on a new port + tenant creation **per test class** (~38×) |
| Containers | `PostgresTesterContainer`, `KafkaUtility.KAFKA_CONTAINER`, `S3Utility`                                                                                                                                   | its *own* `@Container` Postgres + Kafka, plus `S3Utility` again                                                                      |
| HTTP       | `support/HttpClient` (WebClient) + `ResourceClient`                                                                                                                                                      | its own raw-`HttpClient` `doGet/doPost/…`                                                                                            |
| Kafka      | `TestBase.KAFKA_CONSUMER`                                                                                                                                                                                | a *second* independent static `FakeKafkaConsumer`                                                                                    |
| Cleanup    | `TestBase.clearData()` — hand-tuned **partial** delete list                                                                                                                                              | `@AfterEach` full table delete                                                                                                       |
| Assertions | Hamcrest + JUnit `Assertions` mixed                                                                                                                                                                      | AssertJ dominant                                                                                                                     |
| Naming     | `can*`/`cannot*` (324 methods)                                                                                                                                                                           | `post_shouldReturn201AndCreatedRecord` (59)                                                                                          |

Nothing documents which to use. `BaseIntegrationTest` also imports from
`TestBase`/`TestBaseWithInventoryUtil`, so the two are coupled rather than independent.
A full `mvn verify` provisions **2× Postgres, 2× Kafka, 2× LocalStack**.

### F2. Runtime cost is concentrated and fixable
- **~38 verticle deployments + tenant creations** in the `impl` stack vs **1** in the
  `api` stack — the single largest asymmetry (`BaseIntegrationTest.java:157-175`).
- Double container stacks (F1).
- Kafka checks: 35 `awaitAtMost()` sites at **20s** each, plus 5 `awaitDuring` sites
  that block a fixed ~1s each by construction (proving absence of messages).
- `TestBase.TIMEOUT = 100` seconds on **151 blocking `.get(...)` calls across 14 files** —
  a hung request burns 100s before failing.
- **No parallelism anywhere:** no `junit-platform.properties`, no `forkCount`/
  `reuseForks`/`<parallel>` in either pom. `@Testcontainers(parallel = true)` only
  parallelizes container *startup*.
- `SampleDataTest` does a full tenant remove+recreate with `loadSample=true` inside the
  shared `api` suite.

### F3. Isolation is known-broken and undocumented
- `TestBase.java:128-165` — `clearData()` deletes a hand-picked subset; its javadoc
  concedes clearing more "has been found to not work across all tests".
- **13 of 39 `api/` classes have no `@BeforeEach` DB reset at all**, relying on random UUIDs.
- `TestBaseWithInventoryUtil.java:55-62,90-123` — static mutable fixture state
  (`journalMaterialTypeId`, `*_LOCATION_ID`…) overwritten by every subclass's
  `@BeforeAll`; safe only because nothing runs in parallel. **This is what blocks F2's
  parallelism**, together with `StorageTestSuite`'s unsynchronized `running` flag.
- Both stacks share one long-lived `FakeKafkaConsumer` each, cleared via
  `discardAllMessages()` in `@BeforeEach`; in-flight messages from a slow prior test can
  still be attributed to the next one.
- `HoldingsStorageTest.java:107` — lone undocumented `@TestMethodOrder(MethodName.class)`.
- 2 `@Disabled` tests (`HoldingsStorageTest.java:2082,2088`), no reason given, keeping an
  otherwise-dead helper alive.

### F4. Readability / conventions
- **722 `@Test` methods in `api/`+`impl/`, 0 `@DisplayName`.** Only 2 of 46 unit-test
  files use `@DisplayName`. **0 `@Nested`** anywhere.
- Four coexisting naming conventions: `can*`/`cannot*` (324), `should*` (91),
  `method_shouldX` (59), bare descriptive camelCase.
- Three files = 55% of `api/` test code and 358 tests, all flat:
  `ItemStorageTest` (4388 LOC / 131 tests / 125 helpers), `HoldingsStorageTest`
  (4221 / 113 / 126), `InstanceStorageTest` (3167 / 114 / 91). These are also the
  **three most-churned files in repo history** (31 / 25 / 22 touches in the last 400
  commits) — i.e. the maintenance hotspot and the readability problem are the same files.
- Helpers reimplemented independently in all three: `assertExists`, `assertNotExists`,
  `getById`, `postSynchronousBatch`, `postSynchronousBatchUnsafe`, `update`.
- A WireMock transformer class is embedded inside `HoldingsStorageTest` rather than extracted.

### F5. Assertion & fixture fragmentation
- `assertThat(` ×1471, `assertEquals(` ×136, `assertNotNull(` ×102, `assertTrue(` ×89.
  **28 of 39 `api/` files mix Hamcrest and JUnit Assertions in the same file**; 14 unit-test
  files do the same. AssertJ exists in only 12 `impl/` + 2 unit files.
- **Three ways to build an instance**: `entities.Instance`,
  `TestBaseWithInventoryUtil.instance(UUID)`, and per-class `smallAngryPlanet(…)`/`nod(…)`
  helpers redefined in 4+ classes (226 occurrences of `smallAngryPlanet`, 70 of `nod(`).
  There is no `InstanceRequestBuilder`, though `HoldingRequestBuilder`/`ItemRequestBuilder` exist.
- **Three HTTP send implementations** (`support/HttpClient`, `RestUtility.send`,
  `BaseIntegrationTest.doRequest`) each with their own header logic, plus rest-assured in
  one file.
- **Three async-wait styles** in the unit layer: `VertxTestContext`, `Awaitility`, and
  `TestBase.get()` — the last being an *integration*-layer helper imported by 9 unit tests
  (`org.folio.rest.api.TestBase` from `org/folio/services/**`), a cross-layer coupling.

### F6. Coverage is currently unmeasurable
- **No jacoco plugin exists** in either pom. The root pom defines an empty `<argLine>`
  property purely so surefire's `@{argLine}` resolves; the adjacent comment references
  jacoco aspirationally. CI runs `do-sonar-scan: true` — so Sonar receives **no coverage data**.
- Unit-level gaps (some have IT coverage, which is a different safety net):
  **20 of 24 repositories** untested; `services/domainevent` has 29 classes / 5 test files
  (every concrete `*DomainEventPublisher` untested); fully untested subpackages include
  `services/batch`, `bulkprocessing`, `callnumber`, `classification`, `holding`,
  `instance`, `loantype`, `location`, `locationunit`, `materialtype`, `servicepoint`,
  `subjectsource`, `subjecttype`; `ItemService` and `ReindexJobRunner` untested.

### F7. Dead code (safe quick wins)
- `org/folio/rest/api/entities/` — **21 of 25 classes have zero usages**; they duplicate
  resource types now covered by `BaseReferenceDataIntegrationTest<T,C>` ITs.
- `support/db/ErrorFactory.java` — entire class unreferenced.
- `LocationUtility` — ~9 of 21 public methods unused (~40%).
- `postgres-conf-ci.json` / `postgres-conf-local.json` — unreferenced anywhere.
- `InterfaceUrls.java:161` `itemDamagedStatusesUrl` unused.
- `AbstractInstanceRecordsApiTest` is a pure Mockito test that `extends TestBase`, paying
  full Postgres/Kafka/Vert.x startup for nothing.
- `TestRailCase` annotation used in exactly one file (16×) — abandoned pilot.

### F8. Mockito hygiene
- `@ExtendWith(MockitoExtension.class)` in only 5 files; **all 5 set
  `@MockitoSettings(strictness = Strictness.WARN)`**, globally silencing unnecessary-stubbing
  detection instead of fixing over-stubbed setups. `@InjectMocks` never used; everything else
  builds mocks by hand in `@BeforeEach`. `mockStatic` in 6 files — 2 use try-with-resources,
  **4 assign to a field and rely on manual close** (verify each closes in teardown).

### F9. Already clean — do not spend effort here
Zero JUnit 4 remnants anywhere (`org.junit.Test`, `@RunWith`, `@Rule`, `@ClassRule`,
junitparams, vertx-unit). Parameterized tests already native JUnit 5. **Zero `Thread.sleep`.**
No `assertTrue(x != null)`, no empty catch blocks in test bodies, no TODO/FIXME.
Kafka event verification already funnels through one shared Awaitility wrapper.
    ### F10. `data-import-test-support` already provides most of the infrastructure WS2 would build

`lib/data-import-utils` (artifact `org.folio:data-import-utils-parent`, currently
**3.0.0-SNAPSHOT**) ships a `data-import-test-support` module — 16 classes, ~2.9k LOC
with its own test suite — of generic **raml-module-builder** integration-test
infrastructure. Despite the `dataimport` name it is not data-import-specific: it is RMB
+ Vert.x + Testcontainers plumbing.

**Dependency alignment is near-exact**, largely because this branch's JUnit 6 bump
brought us into line: vertx `5.1.6`, junit `6.1.3`, testcontainers `2.0.5`, assertj
`3.27.7`, wiremock `3.13.2`, rest-assured `6.0.1` are **identical** in both repos. RMB
differs by a patch (36.0.1 vs 36.0.0); `folio-kafka-wrapper` (4.0.0 vs 4.1.0-SNAPSHOT)
and `folio-s3-client` (3.1.0-SNAPSHOT vs 3.0.2) differ but are `provided` scope there,
so the consumer's version wins.

**Already adopted by three sibling repos:** `mod-inventory` (26 test classes),
`mod-di-converter-storage` (9), `mod-data-import` (3). The proven adoption pattern in
the two RMB modules is **a single repo-local adapter** — `AbstractRestTest extends
BaseRestTest` — not hundreds of direct `extends`.

What it offers, mapped onto findings above:

| Library class | Replaces / fixes |
|---|---|
| `PostgresExtension`, `KafkaExtension`, `S3Extension` | `StorageTestSuite` + `KafkaUtility` + `S3Utility`. Uses the JUnit root store (`getRoot().getStore(...).computeIfAbsent`) with an auto-closed resource — the framework-managed, **thread-safe** equivalent of our unsynchronized `static boolean running` + JVM shutdown hook (F1, F3, WS0-1/2) |
| `KafkaTestEventCollector` | `FakeKafkaConsumer`. A background thread drains topics into an in-memory per-topic index, so an already-arrived record matches on the first check instead of waiting out a per-call broker timeout; unique `groupId` per collector prevents shared offsets (directly targets F3 leakage and F2's 20s `awaitAtMost`) |
| `TenantTestSupport` (incl. `dataLoadingParameters(loadReference, loadSample)`) | tenant bootstrap in both stacks; the `loadSample` helper serves `SampleDataTest` (F2) |
| `EcsTenantSupport.enableConsortium` | ad-hoc `mockConsortiumTenants()` in `TestBaseWithInventoryUtil` and `InstanceDateTypesEcsIT` |
| `BaseRestTest` / `BaseRestAssuredTest` / `BaseWireMockTest` | the **three** HTTP send implementations (F5) with one rest-assured path |
| `VertxTestUtil.await` | `TestBase.get()` — removes the unit→integration cross-layer coupling (F5) |
| `PostgresTestSupport.clearTable` | a real cleanup primitive to replace `clearData()`'s partial list (F3) |

**What it does *not* solve — important.** `BaseRestTest.deployRestVerticle` is
`@BeforeAll` under `PER_CLASS`, with `@AfterAll` closing Vert.x: it deploys the
`RestVerticle` and enables the tenant **per test class**, i.e. exactly the ~38×
cost this plan criticises in `BaseIntegrationTest`. It shares *containers*, not the
*module*. So WS2's "one bootstrap, not 38" remains our own work — and is a good
candidate to contribute upstream rather than fork.

Also staying repo-local: the 9 `*EventMessageChecks` classes (their per-entity grouping,
`getMessagesForInstance/Holdings/Item`, is inventory domain logic that layers *on top of*
`KafkaTestEventCollector`), and all inventory builders/fixtures including the
`InstanceRequestBuilder` WS5 calls for.

**Risks.** (1) **No released version** — `maven-releases` 404s; only `3.0.0-SNAPSHOT` on
`maven-snapshots` (latest `20260907085239`). Depending on it pins CI to a moving target,
and NEWS marks 3.0.0 "In Progress" with breaking renames still landing. (2) Governance:
this module is not a data-import repo, and `org.folio.dataimport.testsupport` imports in
inventory-storage invite the question of whether the artifact should be renamed/extracted
— worth raising with the DI team rather than deciding unilaterally. (3) `mod-inventory`'s
26 usages are almost entirely `BaseWireMockTest`; the heavyweight `BaseRestTest` path has
only two real consumers, so it is less battle-tested than the headline adoption suggests.


---

## Workstreams

Ordered by leverage. WS0 gates everything; WS1 is cheap and unblocks review consistency;
WS2 is the structural fix that WS3–WS5 depend on.

> **About this document.** This is the *roadmap*: the findings and the agreed
> workstreams, kept with the code so it can be referenced from Jira tickets and PRs.
> WS1 below produces a separate, shorter `docs/testing.md` — the *rules* for writing
> tests. The two are complementary; WS1 supersedes nothing here.
> Remaining setup step: link this file from `README.MD`.

### WS0 — Land the JUnit 6 migration cleanly *(prerequisite, this branch)*

The working tree removes surefire's `<includes>/<excludes>` block, which previously ran
`StorageTestSuite.java` as a JUnit 4 `@RunWith(Suite.class)` and **excluded**
`org/folio/rest/api/*Test.java`. Post-migration each `api` class runs independently and
container lifetime depends on surefire's *default* `reuseForks=true` single-fork behaviour
plus a JVM shutdown hook. That is a load-bearing implicit default.

1. Make it explicit in `mod-inventory-storage-server/pom.xml`: set `forkCount=1` and
   `reuseForks=true` on surefire with a comment explaining that `StorageTestSuite`'s
   JVM-wide container singleton requires it. Treat this as a **stopgap**: WS2 Tier 1
   replaces that singleton with `PostgresExtension`'s root-store lifecycle, after which
   the pin should be revisited rather than left to block WS3's parallelism.
2. Synchronize `StorageTestSuite.startupUnlessRunning()` (the `running` flag is a plain
   unsynchronized `static boolean`).
3. Fix stale docs left by the migration: `TestBase.java` javadoc still describes the
   removed `Suite` mechanism; `TestBase.java:81,94` and `TestBaseWithInventoryUtil.java:101,122`
   still say `"@BeforeClass"`.
4. Replace the 3 `org.testcontainers.shaded.org.awaitility.Awaitility` imports (incl.
   `BaseIntegrationTest`) with the declared `org.awaitility` dependency.

### WS1 — Write down the standard *(cheap, do immediately after WS0)*

Add `docs/testing.md` (linked from `README.MD`) and a `CLAUDE.md` pointer. Adopt the
data-import standard, with these **repo-specific deviations** recorded explicitly:

- **Naming:** `should<Outcome>_when<Condition>` + `@DisplayName` for new/edited tests.
  The `can*`/`cannot*` corpus (324 methods) is *not* renamed wholesale — it is renamed
  only within classes being split under WS5.
- **Class suffix:** `*Test` = surefire/unit + the legacy `rest.api` layer; `*IT` =
  failsafe/integration. Both patterns are already load-bearing in the build — do not
  rename existing classes across the boundary without moving the pom config in the same commit.
- **Assertions:** AssertJ for all new and edited tests. Hamcrest in `rest/api` is
  entrenched (37/39 files) and is an allowed legacy exception — don't churn it for style alone.
- **Async:** `VertxTestContext` for new async tests. Ban new uses of `TestBase.get()`
  from unit-layer packages (F5 cross-layer coupling).
- **No conditionals in test bodies**; `assertThatThrownBy`/`assertThrows` over try/catch.
- **Fixtures:** builders under `support/builders`, no new per-class `smallAngryPlanet`.
- Add the AssertJ/naming rules to checkstyle or an ArchUnit test where mechanically
  enforceable, so the standard doesn't rot.

### WS2 — Converge on one integration harness, on top of `data-import-test-support` *(the structural fix)*

Per F10, most of the infrastructure this workstream would otherwise build already exists
in `org.folio:data-import-test-support` and is in use by three sibling repos. Adopt it in
tiers rather than wholesale, and keep inventory domain logic local.

**Prerequisite:** a released `data-import-utils` 3.0.0. Today only `3.0.0-SNAPSHOT`
exists (no `maven-releases` entry), and NEWS still lists breaking renames as in progress.
Do not pin master's CI to a moving snapshot — track the release, and raise the
naming/ownership question (an inventory module depending on `dataimport.testsupport`)
with the DI team in the same conversation.

- **Tier 1 — extensions and utilities (low risk, highest value).** Replace
  `StorageTestSuite`/`KafkaUtility`/`S3Utility` with `PostgresExtension`, `KafkaExtension`,
  `S3Extension`; replace `TestBase.get()` with `VertxTestUtil.await`; use
  `TenantTestSupport` (and `EcsTenantSupport` for the consortium mocking) for tenant
  bootstrap. The root-store lifecycle is thread-safe, which is what actually unblocks WS3
  — and it supersedes WS0's `forkCount`/`reuseForks` pin (see the note in WS0).
- **Tier 2 — Kafka (see WS4).** Put `KafkaTestEventCollector` underneath the existing
  `*EventMessageChecks` facade. The 9 checker classes stay: their per-entity grouping is
  inventory domain logic.
- **Tier 3 — the REST base class (defer).** Adopt `BaseRestTest` through **one**
  repo-local adapter, the pattern `mod-di-converter-storage` and `mod-data-import` both
  use — not 77 direct `extends`. This is the expensive tier because `BaseRestTest` is
  rest-assured based and our 77 integration tests are not; sequence it with the WS5
  rewrites rather than as a big-bang move. Its payoff is collapsing F5's three HTTP send
  implementations into one.
- **Do not adopt:** inventory builders/fixtures. `InstanceRequestBuilder` (WS5) stays local.

Two gaps the library does **not** close, which remain our own work:

1. **Shared module lifecycle.** `BaseRestTest` deploys the `RestVerticle` and enables the
   tenant per test class — the same ~38× cost we are trying to remove. Build the
   shared-module bootstrap here and offer it upstream rather than forking.
2. Fix `BaseIntegrationTest.java:157` regardless: it has no reuse guard, and
   `vertx.deployVerticle(...).compose(...)`'s Future is discarded, so a deploy failure
   surfaces only as an unhelpful 65s `awaitCompletion` timeout.

### WS3 — Runtime *(depends on WS2)*

1. Land WS2 items 1–2 first; they are most of the win (one container stack, one bootstrap).
2. Introduce a real per-test isolation mechanism (transaction rollback or full truncate)
   to replace `TestBase.clearData()`'s partial list, then **remove the static mutable
   fixture state** in `TestBaseWithInventoryUtil` (F3) — these two are the actual
   blockers to parallelism.
3. Only after that, enable JUnit 5 parallel execution via `junit-platform.properties`,
   starting with `concurrent` at class level for the `impl` stack.
4. Lower `TestBase.TIMEOUT` from 100s to something that fails fast (~15–20s), and audit
   the stacked `awaitCompletion(65s)` + `wait=60000` tenant timeouts.
5. Measure before/after — see Verification.

### WS4 — Flakiness *(partly folded into WS2/WS3)*

1. Root-cause the Kafka consumer leakage: `discardAllMessages()` in `@BeforeEach` cannot
   fence in-flight messages from a prior test. Prefer WS2 Tier 2 over a bespoke fix —
   `KafkaTestEventCollector` already gives a per-collector `groupId` (no shared offsets)
   and a continuously-drained in-memory index, so an already-arrived record matches on the
   first check instead of waiting out a broker timeout. Keep the `*EventMessageChecks`
   facade and its per-entity grouping on top.
2. Then reduce `AwaitConfiguration.awaitAtMost()` from 20s, as the code comment itself asks —
   but only after step 1, otherwise the timeout is load-bearing again.
3. Make `InstanceEventMessageChecks.java:109-110` use the shared `AwaitConfiguration`
   instead of its own raw `await().atMost(15, SECONDS)`.
4. Document or delete the 2 undocumented `@Disabled` tests and the undocumented
   `@TestMethodOrder` in `HoldingsStorageTest`.
5. Fix Mockito hygiene (F8): drop the blanket `Strictness.WARN` in the 5 `MockitoExtension`
   classes, fixing the over-stubbing it hides; convert the 4 field-held `mockStatic` uses
   to try-with-resources.

### WS5 — Targeted rewrites of the worst offenders *(the "targeted rewrites" half)*

One ticket per file, in churn order. For each: split by feature into separate classes (or
`@Nested` groups), apply WS1 naming + `@DisplayName`, lift the duplicated helpers
(`assertExists`, `getById`, `postSynchronousBatch`, `update`) into shared support, and
move `smallAngryPlanet`/`nod` into a real `InstanceRequestBuilder` under `support/builders`.

1. `HoldingsStorageTest` (4221 LOC, 113 tests, 31 touches) — also extract the embedded
   WireMock transformer class.
2. `InstanceStorageTest` (3167, 114, 25).
3. `ItemStorageTest` (4388, 131, 22).

Suggested split axes, consistent across all three: CRUD, effective location/call-number,
Kafka event publishing, batch/bulk, optimistic locking, validation.

### WS6 — Coverage *(start with measurement)*

1. **Add the jacoco plugin** — currently absent, so the `@{argLine}` indirection and the
   `do-sonar-scan: true` CI step produce no coverage data at all. This is a prerequisite
   for any coverage claim.
2. Establish the baseline, then set a ratchet (no-decrease) rather than an absolute target.
3. Fill unit gaps in dependency order, prioritising logic-bearing classes over generated
   CRUD passthroughs: `ItemService`, `ReindexJobRunner`, `services/holding` (incl.
   `HoldingsUpsertSqlBuilder`), `services/instance`, `AbstractDomainEventPublisher`.
   The 20 untested repositories are mostly thin `AbstractRepository` subclasses — cover
   `AbstractRepository` once rather than each subclass.

### WS7 — Delete dead code *(independent, do any time)*

Per F7: 21 unused `entities/` classes, `support/db/ErrorFactory`, ~9 `LocationUtility`
methods, `postgres-conf-*.json`, `InterfaceUrls.itemDamagedStatusesUrl`. Move
`AbstractInstanceRecordsApiTest` off `TestBase` so it stops booting the whole stack.
Decide `TestRailCase`: propagate it or drop it.

---

## Sequencing

```
WS0 (land migration) ──► WS1 (write standard)
                          │
                          ├─► WS2 (converge harness) ──► WS3 (runtime) ──► parallelism
                          │                          └─► WS4 (flakiness)
                          ├─► WS5 (split 3 giants)  [needs WS1 naming + WS2 builders]
                          ├─► WS6 (jacoco → coverage)   [independent after WS0]
                          └─► WS7 (dead code)           [independent, any time]
```

WS0, WS1, WS6-step-1 and WS7 are small and can land within days. WS2 is the one genuinely
large piece of engineering and everything expensive depends on it — though F10 removes a
good deal of it, provided `data-import-utils` 3.0.0 gets released. **That release is the
critical-path external dependency; start tracking it now**, since WS2 Tier 1 gates WS3.

## Verification

- **Baseline first.** Before any change, record `mvn -q verify` wall-clock and per-class
  timings from `target/surefire-reports/*.txt` and `failsafe-reports`. Without this, WS3
  cannot be shown to have worked. Capture container count via `docker events` or
  Testcontainers logs during one full run.
- **WS0:** full `mvn verify` green with the explicit `forkCount`/`reuseForks`; confirm
  `rest/api` classes actually execute (compare surefire report count against the pre-change
  Suite run — the include/exclude removal is exactly the kind of change that silently stops
  running tests).
- **WS2/WS3:** re-run the baseline measurement; expect one Postgres/Kafka/LocalStack set
  instead of two and one verticle deployment instead of ~38. Assert the improvement in
  numbers, not impressions.
- **WS3 parallelism:** run the suite 10× consecutively before declaring it stable;
  ordering/isolation bugs are probabilistic and a single green run proves nothing.
- **WS4:** re-run 10× with `awaitAtMost` temporarily lowered to ~5s — if it still passes,
  the flakiness was genuinely fixed rather than masked by the timeout.
- **WS5:** test *count* per split file must be preserved (sum of new classes == old class
  count); diff the surefire report method lists before/after to prove nothing was dropped.
- **WS2 Tier 1:** verify the shared containers really are shared under the new extensions —
  assert one Postgres/Kafka/LocalStack per run, and confirm the suite still passes with the
  WS0 `forkCount`/`reuseForks` pin *removed*, which is the point of moving to the root-store
  lifecycle.
- **WS2 Tier 3:** migrate one class first and compare its runtime and assertion coverage
  against the original before converting any others.
- **WS6:** jacoco report generates and Sonar shows non-zero coverage.
- **WS7:** compile + full verify green after each deletion batch.
