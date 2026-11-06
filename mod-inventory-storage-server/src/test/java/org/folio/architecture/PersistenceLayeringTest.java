package org.folio.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Keeps the layers apart: the Api layer builds HTTP responses, services work with domain objects and
 * repositories own the database access.
 */
class PersistenceLayeringTest {

  private static JavaClasses productionClasses;

  @BeforeAll
  static void importClasses() {
    productionClasses = new ClassFileImporter()
      .withImportOption(new ImportOption.DoNotIncludeTests())
      .withImportOption(new ImportOption.DoNotIncludeJars())
      .importPackages("org.folio");
  }

  @Test
  @DisplayName("should not use RMB's PgUtil anywhere, data is accessed through repositories")
  void shouldNotUsePgUtil_whenScanningProductionClasses() {
    noClasses()
      .should().dependOnClassesThat().haveFullyQualifiedName("org.folio.rest.persist.PgUtil")
      .because("data access belongs in repositories (org.folio.persist), HTTP responses in the Api classes")
      .check(productionClasses);
  }

  @Test
  @DisplayName("should access the database client only in repositories")
  void shouldNotUsePostgresClient_whenOutsideRepositories() {
    noClasses()
      .that().resideOutsideOfPackage("org.folio.persist..")
      .should().dependOnClassesThat().haveFullyQualifiedName("org.folio.rest.persist.PostgresClient")
      .because("repositories own the database access, everything else goes through them")
      .check(productionClasses);
  }

  @Test
  @DisplayName("should keep JAX-RS responses out of services and repositories")
  void shouldNotUseJaxRsResponse_whenInServicesOrRepositories() {
    noClasses()
      .that().resideInAnyPackage("org.folio.services..")
      .should().dependOnClassesThat().haveFullyQualifiedName("javax.ws.rs.core.Response")
      .because("services return domain objects or fail with exceptions, the Api classes build the responses")
      .check(productionClasses);
  }

  @Test
  @DisplayName("should keep repositories independent of services and Api classes")
  void shouldNotDependOnUpperLayers_whenInRepositories() {
    noClasses()
      .that().resideInAPackage("org.folio.persist..")
      .should().dependOnClassesThat().resideInAnyPackage("org.folio.services..")
      .because("repositories are the lowest layer")
      .check(productionClasses);
  }
}
