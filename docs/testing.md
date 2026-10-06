# Testing standards — mod-inventory-storage

This is the house standard for tests in this repo: how to name, structure, and assert
in **new tests and tests you're already touching for another reason**. It is not a
mandate to retroactively rewrite every existing test file.

We adopt the FOLIO data-import test conventions standard (the house standard used
across `lib/data-import-processing-core`, `mod-data-import`, `mod-inventory`,
`mod-source-record-manager`, `mod-source-record-storage`, `mod-di-converter-storage`,
and `lib/data-import-utils`) as our base — this repo is not formally in that
standard's scope, but the rules transfer directly — with the repo-specific
deviations recorded below. Where this document and that standard disagree, this
document wins for mod-inventory-storage.

## One integration layer, one unit layer

The legacy `org.folio.rest.api.*Test` stack (Surefire, `StorageTestSuite`,
`support/HttpClient`) has been fully migrated away and removed. There is now exactly
one integration-test layer:

| | Integration (`org.folio.it.**`) | Unit (everything else under `org.folio.**`) |
|---|---|---|
| Class suffix | `*IT` | `*Test` |
| Runs under | Failsafe (`mvn verify`) | Surefire (`mvn test`) |
| Bootstrap | `BaseIntegrationTest` (per-class Postgres/Kafka/verticle via Testcontainers) | none — plain JUnit 5, Mockito, or `VertxTestContext` for async |
| HTTP | its own `doGet`/`doPost`/`doPut`/`doPatch`/`doDelete` helpers | n/a |
| Assertions | AssertJ (primary); Hamcrest still present in files not yet touched | AssertJ (primary); Hamcrest still present in files not yet touched |

Both suffixes are load-bearing in the build (Surefire and Failsafe include patterns
respectively) — **do not rename a class across the `*Test`/`*IT` boundary without
moving the corresponding Surefire/Failsafe config in the same commit**, and don't put
an integration test outside `org.folio.it.**` or a unit test inside it.

## 1. Naming

New and edited test methods: `should<Outcome>_when<Condition>` (the `_when...` clause
is optional for trivial cases), plus a `@DisplayName` with a human-readable sentence.

```java
@Test
@DisplayName("should return 404 when the item does not exist")
void shouldReturn404_whenItemDoesNotExist() { ... }
```

This is now the dominant style across the suite. A handful of pre-existing files still
use the older `can*`/`cannot*` style — rename those only incidentally, when you're
already rewriting the method for another reason; don't drive-by rename as part of an
unrelated change.

## 2. Structure

Arrange-Act-Assert (unit) or Given-When-Then (integration), with blank-line
separation and, where it clarifies intent, short `// arrange`/`// act`/`// assert`
comments. One logical assertion target per test — split multi-scenario tests into
separate `@Test`s or a `@ParameterizedTest`.

## 3. Assertions

**AssertJ (`assertThat` from `org.assertj.core.api.Assertions`) for all new and
edited tests**, in both layers.

- **Hamcrest exception:** still present across a meaningful slice of the suite
  (integration and unit alike) — an allowed legacy exception. Don't churn existing
  Hamcrest assertions to AssertJ for style alone in a file you're editing for an
  unrelated reason.
- Prefer `assertThatThrownBy(...)` / `assertThrows(...)` over try/catch for expected
  exceptions.

## 4. Async

**`VertxTestContext` for new asynchronous unit tests.**

Unit-layer tests (anything outside `org.folio.it.**`) must **not** depend on
`org.folio.it.BaseIntegrationTest` — that base pulls in the full Postgres/Kafka/verticle
bootstrap, which a unit test has no business paying for. Use `VertxTestContext`,
Mockito, or another unit-layer helper instead. This is enforced by
`TestLayeringTest` (`org.folio.architecture.TestLayeringTest`), which fails the build
if any class outside `org.folio.it.**` depends on `BaseIntegrationTest`.

## 5. No conditionals in test bodies

No `if`/`for`/`while`/`switch`/`try`-for-branching inside `@Test` methods. Use
`@ParameterizedTest` + `@MethodSource`/`@CsvSource`/`@ValueSource` for input/expected
pairs, move conditional setup into fixtures/builders, or split into several
single-purpose tests.

## 6. Mocking (Mockito)

- `@ExtendWith(MockitoExtension.class)` — leave strictness at its default
  (`STRICT_STUBS`). Do not add `@MockitoSettings(strictness = Strictness.WARN)`; it
  silences unnecessary-stub detection instead of fixing over-stubbed setups. Fix the
  underlying over-stubbing instead.
- `mockStatic(...)` via try-with-resources, always — never assign the `MockedStatic` to
  a field and close it manually. This is a hard rule with no current exceptions in the
  codebase.
- For classes whose constructor self-constructs many collaborators from a `Context`
  (making normal dependency injection in a test impractical), it's an established
  pattern here to build the object under test via
  `Mockito.mock(TheClass.class, withSettings().defaultAnswer(CALLS_REAL_METHODS))` and
  invoke its private, pure-logic methods reflectively
  (`target.getClass().getDeclaredMethod(...).setAccessible(true)`). Mockito's inline
  mock maker does not subclass the target or run its constructor, so the returned
  object is a real instance of the class with default-valued fields — `CALLS_REAL_METHODS`
  (not `RETURNS_DEFAULTS`) lets any public method on it also run for real. See
  `HoldingsServiceTest`/`InstanceServiceTest` for worked examples. Prefer a real
  constructor-based test when the class *is* practically constructible — reach for this
  pattern only when it isn't.

## 7. Fixtures and builders

Check `support/builders` before writing a new one: `HoldingRequestBuilder`,
`ItemRequestBuilder`, `InstanceRequestBuilder`, `BoundWithPartBuilder`,
`JsonRequestBuilder` (all implement the `Builder` marker interface). Before writing a
new helper (`assertExists`, `getById`, `syncBatch`, etc.), check whether one already
exists on the relevant `*TestBase` (see §8) or in `support/` first.

Note: unlike the data-import repos, this repo does **not** adopt
`data-import-test-support`'s builders/fixtures — inventory domain builders stay local.

## 8. Splitting a large `*StorageIT` class

`HoldingsStorageIT`, `InstanceStorageIT`, and `ItemStorageIT` were each split from one
flat 1900–2100-line class into a per-domain subpackage
(`org.folio.it.api.holdings`, `.instance`, `.item`). If a class grows past a few
hundred lines or accumulates several unrelated concerns, follow the same pattern:

1. **Shared base class**: `abstract class <Domain>StorageTestBase extends
   BaseIntegrationTest`, package-private, in the domain subpackage. It holds
   `@BeforeAll`/`@BeforeEach`/`@AfterEach` lifecycle (reference-data seeding, per-test
   cleanup) and shared constants/fields. **Only pull a helper method into the base if
   it's used by two or more of the split classes** — a helper used by exactly one
   feature class stays local to that class instead, to avoid bloating the base with
   imports only one caller needs.
2. **Feature classes**: `class <Domain>Storage<Feature>IT extends
   <Domain>StorageTestBase`, package-private, same subpackage — so inherited
   package-private static helpers/fields are directly usable with no `protected` or
   extra imports. Name the feature suffix for the concern it covers (`Crud`,
   `Validation`, `OptimisticLocking`, `Hrid`, `Batch`, `Search`, `Status`, `Order`,
   `CallNumber`, …) — pick axes that fit the class's actual test population rather than
   forcing a fixed list.
3. **Copy test bodies verbatim** — no behavior changes as part of a split. Don't
   rename, restructure, or "improve" a test while moving it; do that as a separate,
   reviewable change if it's needed.
4. **Verify before deleting the original**: diff the full sorted list of
   `@DisplayName` strings from the original file against the concatenation of all new
   files — they must match exactly (proves no test was dropped, duplicated, or
   silently renamed). Then run a targeted Failsafe pass
   (`mvn test-compile failsafe:integration-test failsafe:verify
   -Dit.test='<Domain>Storage*IT'`) confirming the same total test count with 0
   failures/errors, and only then delete the original file and run the full
   `mvn clean verify`.

## Coverage

Jacoco is wired up (root `pom.xml`): unit tests feed the `prepare-agent` agent,
integration tests feed a separate `prepare-agent-integration` agent, and both
`.exec` files are merged before reporting — most real coverage comes from the
integration suite, not unit tests. Generated JAXRS model/resource classes
(`org/folio/rest/jaxrs/**`) are excluded from the report and the ratchet, since they're
pure data holders with no logic to cover.

`jacoco.line.ratchet` (in the root `pom.xml`) is a **no-decrease** line-coverage floor
enforced by the `check` goal during `verify` — it only ever moves up. When adding
meaningful new coverage, raising the ratchet in the same change is encouraged but not
required; when a change would lower measured coverage, add tests rather than lowering
the ratchet.

When filling a coverage gap, prioritize logic-bearing classes over generated CRUD
passthroughs — a class that's 100% delegation to a repository/client with no
conditional logic of its own just re-verifies Mockito's delegation mechanics and isn't
worth a dedicated test.

## Mechanical enforcement

`org.folio.architecture.TestLayeringTest` enforces §4 (no unit-layer dependency on
`BaseIntegrationTest`) as part of the normal test run. As more rules here become
mechanically checkable (e.g. a Hamcrest/AssertJ mix detector, a `mockStatic`
field-assignment detector), add them next to it rather than leaving this document as
the only source of truth.
