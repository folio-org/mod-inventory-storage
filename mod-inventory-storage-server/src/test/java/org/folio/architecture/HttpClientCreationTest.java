package org.folio.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards against creating Vert.x clients outside module/verticle startup.
 *
 * <p>Every client created by {@code Vertx.createHttpClient()} (and the factories below, which call it) is
 * registered in Vert.x close hooks and is only released when closed explicitly. Creating one per request leaks
 * memory until OOM (MODINVSTOR-1620). Create the client once and share it; wrap it per call with
 * {@code WebClient.wrap(sharedClient)}, which is allowed.
 */
class HttpClientCreationTest {

  /** Owner class name to the method names whose calls create a client. */
  private static final Map<String, Set<String>> CLIENT_FACTORY_METHODS = Map.of(
    "io.vertx.core.Vertx", Set.of("createHttpClient", "createNetClient"),
    "io.vertx.ext.web.client.WebClient", Set.of("create"),
    "org.folio.okapi.common.HttpClientFactory", Set.of("getHttpClient")
  );

  /**
   * Classes that create a client once at startup. Do not add request-scoped classes here.
   * Remove entries as they move to the Spring configuration (see docs/tech-debt/introduce-dependency-injection.md).
   */
  private static final Set<String> ALLOWED_CREATORS = Set.of(
    "org.folio.rest.impl.InitApiImpl",
    "org.folio.services.consortium.SynchronizationVerticle",
    "org.folio.services.consortium.ShadowInstanceSynchronizationVerticle",
    "org.folio.services.consortium.ServicePointSynchronizationVerticle"
  );

  @Test
  @DisplayName("Should create HTTP clients only in allowed startup classes")
  void shouldCreateClientsOnlyInAllowedClasses_whenScanningProductionClasses() {
    JavaClasses productionClasses = new ClassFileImporter()
      .withImportOption(new ImportOption.DoNotIncludeTests())
      .withImportOption(new ImportOption.DoNotIncludeJars())
      .importPackages("org.folio");

    List<String> violations = productionClasses.stream()
      .filter(javaClass -> !ALLOWED_CREATORS.contains(topLevelClassName(javaClass.getName())))
      .flatMap(javaClass -> javaClass.getMethodCallsFromSelf().stream())
      .filter(HttpClientCreationTest::isClientCreation)
      .map(call -> call.getOrigin().getFullName() + " calls " + call.getTarget().getFullName())
      .sorted()
      .toList();

    assertThat(violations)
      .as("HTTP clients must be created once at startup and shared, never per request (MODINVSTOR-1620). "
          + "Inject or reuse the shared client and use WebClient.wrap(client) per call. "
          + "Allowed creators: " + ALLOWED_CREATORS)
      .isEmpty();
  }

  private static boolean isClientCreation(JavaMethodCall call) {
    return CLIENT_FACTORY_METHODS
      .getOrDefault(call.getTargetOwner().getFullName(), Set.of())
      .contains(call.getName());
  }

  private static String topLevelClassName(String className) {
    int nestedClassSeparator = className.indexOf('$');
    return nestedClassSeparator < 0 ? className : className.substring(0, nestedClassSeparator);
  }
}
