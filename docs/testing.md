# Testing standards — mod-inventory-storage

This is the house standard for tests in this repo: how to name, structure, and assert
in **new tests and tests you're already touching for another reason**. It is not a
mandate to retroactively rewrite the existing ~217 test files (41.8k LOC).

We adopt the FOLIO data-import test conventions standard (the house standard used
across `lib/data-import-processing-core`, `mod-data-import`, `mod-inventory`,
`mod-source-record-manager`, `mod-source-record-storage`, `mod-di-converter-storage`,
and `lib/data-import-utils`) as our base — this repo is not formally in that
standard's scope, but the rules transfer directly — with the repo-specific
deviations recorded below. Where this document and that standard disagree, this
document wins for mod-inventory-storage.

## Two test layers — know which one you're in

| | `org/folio/rest/api/` (legacy) | `org/folio/rest/impl/` (modern) |
|---|---|---|
| Class suffix | `*Test` | `*IT` |
| Runs under | Surefire (`mvn test`) | Failsafe (`mvn verify`) |
| Bootstrap | `StorageTestSuite` (shared JVM-wide Postgres/Kafka/S3/verticle) | `BaseIntegrationTest` (per-class verticle) |
| HTTP | `support/HttpClient` + `ResourceClient` | its own `doGet`/`doPost`/… |
| Assertions | Hamcrest + JUnit `Assertions`, mixed | AssertJ |

Both suffixes are load-bearing in the build (Surefire and Failsafe include patterns
respectively) — **do not rename an existing class across the `*Test`/`*IT` boundary
without moving the corresponding Surefire/Failsafe config in the same commit.**
Converging these two stacks onto one harness is tracked separately (WS2 in the
improvement plan); until that lands, match the convention of whichever layer you're
adding to.

## 1. Naming

New and edited test methods: `should<Outcome>_when<Condition>` (the `_when...` clause
is optional for trivial cases), plus a `@DisplayName` with a human-readable sentence.

```java
@Test
@DisplayName("should return 404 when the item does not exist")
void shouldReturn404_whenItemDoesNotExist() { ... }
```

The existing `can*`/`cannot*` corpus (324 methods, mostly in `ItemStorageTest`,
`HoldingsStorageTest`, `InstanceStorageTest`) is **not** renamed wholesale. Rename a
`can*`/`cannot*` method only when the class it lives in is being split under a
targeted rewrite (see the improvement plan's WS5); don't drive-by rename it as part
of an unrelated change.

## 2. Structure

Arrange-Act-Assert (unit) or Given-When-Then (integration), with blank-line
separation and, where it clarifies intent, short `// arrange`/`// act`/`// assert`
comments. One logical assertion target per test — split multi-scenario tests into
separate `@Test`s or a `@ParameterizedTest`.

## 3. Assertions

**AssertJ (`assertThat` from `org.assertj.core.api.Assertions`) for all new and
edited tests.**

- **Hamcrest exception:** Hamcrest is entrenched in `rest/api` (37 of 39 files mix
  it with JUnit `Assertions` already). This is an allowed legacy exception — don't
  churn existing Hamcrest assertions to AssertJ for style alone in a file you're
  editing for an unrelated reason.
- Prefer `assertThatThrownBy(...)` / `assertThrows(...)` over try/catch for expected
  exceptions.

## 4. Async

**`VertxTestContext` for new asynchronous unit tests.**

`TestBase.get()` (`org.folio.rest.api.TestBase`, an integration-layer helper) must
**not** be imported by new unit-layer tests (anything under `org/folio/services/**`
and similar unit packages) — this is a cross-layer coupling that already affects 8
existing files, grandfathered rather than fixed today. This rule is enforced by
`TestLayeringTest` (`org.folio.architecture.TestLayeringTest`), which fails the build
if a *new* class outside the recorded grandfathered list depends on
`org.folio.rest.api.TestBase`.

## 5. No conditionals in test bodies

No `if`/`for`/`while`/`switch`/`try`-for-branching inside `@Test` methods. Use
`@ParameterizedTest` + `@MethodSource`/`@CsvSource` for input/expected pairs, move
conditional setup into fixtures/builders, or split into several single-purpose
tests.

## 6. Mocking (Mockito)

- `@ExtendWith(MockitoExtension.class)` with `@MockitoSettings(strictness =
  Strictness.STRICT_STUBS)` for new Mockito-based unit tests — not the blanket
  `Strictness.WARN` used by the 5 existing classes, which silences unnecessary-stub
  detection instead of fixing over-stubbed setups. Fix the underlying over-stubbing
  rather than relaxing strictness.
- `mockStatic(...)` via try-with-resources, not a field with manual `close()`.

## 7. Fixtures and builders

New reusable test data goes under `support/builders` as a fluent builder (see
`HoldingRequestBuilder`/`ItemRequestBuilder` for the pattern) — don't add another
per-class `smallAngryPlanet(...)`/`nod(...)`-style helper. Before writing a new
helper (`assertExists`, `getById`, `postSynchronousBatch`, etc.), check whether one
already exists in `support/` — three of these are currently reimplemented
independently across `ItemStorageTest`, `HoldingsStorageTest`, and
`InstanceStorageTest`, which is exactly the drift this document exists to stop.

Note: unlike the data-import repos, this repo does **not** adopt
`data-import-test-support`'s builders/fixtures — inventory domain builders
(`InstanceRequestBuilder` and friends) stay local. See the improvement plan (F10,
WS2) for why.

## Mechanical enforcement

`org.folio.architecture.TestLayeringTest` enforces rule 4 (no new
integration-layer/unit-layer coupling via `TestBase`) as part of the normal test run.
As more rules here become mechanically checkable (e.g. a Hamcrest/AssertJ mix
detector), add them next to it rather than leaving this document as the only source
of truth.
