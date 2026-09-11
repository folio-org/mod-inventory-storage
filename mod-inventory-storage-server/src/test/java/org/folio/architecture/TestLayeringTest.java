package org.folio.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Mechanically enforces the docs/testing.md rule that unit-layer tests (org.folio.services..)
 * must not depend on the integration-layer org.folio.it.BaseIntegrationTest.
 */
class TestLayeringTest {

  private static final String INTEGRATION_LAYER_BASE = "org.folio.it.BaseIntegrationTest";

  @Test
  void unitLayerTestsMustNotDependOnIntegrationLayerBase() {
    JavaClasses classes = new ClassFileImporter().importPackages("org.folio.services");

    List<String> violations = classes.stream()
      .filter(javaClass -> javaClass.getDirectDependenciesFromSelf().stream()
        .anyMatch(dependency -> dependency.getTargetClass().getFullName()
          .equals(INTEGRATION_LAYER_BASE)))
      .map(JavaClass::getName)
      .toList();

    assertThat(violations)
      .as("unit-layer tests under org.folio.services must not depend on the integration-layer "
        + "BaseIntegrationTest (see docs/testing.md); use VertxTestContext, Mockito, or another "
        + "unit-layer helper instead")
      .isEmpty();
  }
}
