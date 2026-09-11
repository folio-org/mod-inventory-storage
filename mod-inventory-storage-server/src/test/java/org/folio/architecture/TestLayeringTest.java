package org.folio.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Mechanically enforces the docs/testing.md rule that unit-layer tests (org.folio.services..)
 * must not depend on the integration-layer org.folio.rest.api.TestBase.
 */
class TestLayeringTest {

  private static final String INTEGRATION_LAYER_TEST_BASE = "org.folio.rest.api.TestBase";

  // Predate this rule (WS1) and are grandfathered. Do not add to this list: new unit-layer
  // tests must use an async helper that does not cross into the integration layer, for example
  // VertxTestContext, instead of TestBase.get().
  private static final Set<String> GRANDFATHERED_CLASSES = Set.of(
    "org.folio.services.migration.BatchedReadStreamTest",
    "org.folio.services.iteration.IterationServiceTest",
    "org.folio.services.reindex.ReindexServiceTest",
    "org.folio.services.reindex.ReindexExportOrchestratorTest",
    "org.folio.services.reindex.ReindexFileReadyEventPublisherTest",
    "org.folio.services.reindex.ReindexS3ExportServiceTest",
    "org.folio.services.domainevent.CommonDomainEventPublisherTest"
  );

  @Test
  void unitLayerTestsMustNotDependOnIntegrationLayerTestBase() {
    JavaClasses classes = new ClassFileImporter().importPackages("org.folio.services");

    List<String> violations = classes.stream()
      .filter(javaClass -> !GRANDFATHERED_CLASSES.contains(javaClass.getName()))
      .filter(javaClass -> javaClass.getDirectDependenciesFromSelf().stream()
        .anyMatch(dependency -> dependency.getTargetClass().getFullName()
          .equals(INTEGRATION_LAYER_TEST_BASE)))
      .map(JavaClass::getName)
      .toList();

    assertThat(violations)
      .as("unit-layer tests under org.folio.services must not depend on the integration-layer "
        + "TestBase (see docs/testing.md); use VertxTestContext or another unit-layer async "
        + "helper instead")
      .isEmpty();
  }
}
