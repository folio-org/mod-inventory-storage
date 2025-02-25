package org.folio.rest.api;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.SneakyThrows;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.IndividualResource;
import org.junit.Before;
import org.junit.Test;

public class InstanceDiscoverySuppressMigrationScriptTest extends MigrationTestBase {
  private static final String DISCOVERY_SUPPRESS = "discoverySuppress";
  private static final String MIGRATION_SCRIPT
    = "SET ROLE " + PostgresClient.getInstance(null).getConnectionConfig().getString("username") + ";\n"
      + loadScript("populateDiscoverySuppressIfNotSet.sql");

  @SneakyThrows
  @Before
  public void beforeEach() {
    clearData();
    setupMaterialTypes();
    setupLoanTypes();
    setupLocations();
    removeAllEvents();
  }

  @Test
  public void canSetDiscoverySuppressIfNotPresent() {
    List<IndividualResource> allInstances = createInstances(3);

    for (IndividualResource instance : allInstances) {
      assertThat(unsetJsonbProperty("instance", instance.getId(),
        DISCOVERY_SUPPRESS).rowCount(), is(1));
    }

    executeMultipleSqlStatements(MIGRATION_SCRIPT);

    verifyNotSuppressed(allInstances);
  }

  @Test
  public void discoverySuppressedInstancesAreNotUpdated() {
    List<IndividualResource> notSuppressedInstances = createInstances(2);
    List<IndividualResource> suppressedInstances = new ArrayList<>(2);

    suppressedInstances.add(
      instancesClient.create(smallAngryPlanetSuppressedFromDiscovery()));
    suppressedInstances.add(
      instancesClient.create(smallAngryPlanetSuppressedFromDiscovery()));

    for (IndividualResource instance : notSuppressedInstances) {
      assertThat(unsetJsonbProperty("instance", instance.getId(),
        DISCOVERY_SUPPRESS).rowCount(), is(1));
    }

    executeMultipleSqlStatements(MIGRATION_SCRIPT);

    verifyNotSuppressed(notSuppressedInstances);
    verifySuppressed(suppressedInstances);
  }

  private List<IndividualResource> createInstances(int count) {
    final List<IndividualResource> allInstances = new ArrayList<>();

    for (int i = 0; i < count; i++) {
      allInstances.add(instancesClient.create(smallAngryPlanet()));
    }
    return allInstances;
  }

  private JsonObject smallAngryPlanet() {
    return createInstanceRequest(UUID.randomUUID(), "TEST",
      "Long Way to a Small Angry Planet", new JsonArray(), new JsonArray(),
      UUID_INSTANCE_TYPE, new JsonArray());
  }

  private JsonObject smallAngryPlanetSuppressedFromDiscovery() {
    return smallAngryPlanet()
      .put(DISCOVERY_SUPPRESS, true);
  }

  private void verifyNotSuppressed(List<IndividualResource> instances) {
    for (IndividualResource instance : instances) {
      Instance instanceInStorage = instancesClient.getById(instance.getId()).getJson()
        .mapTo(Instance.class);
      assertThat(instanceInStorage.getDiscoverySuppress(), is(false));
    }
  }

  private void verifySuppressed(List<IndividualResource> instances) {
    for (IndividualResource instance : instances) {
      Instance instanceInStorage = instancesClient.getById(instance.getId()).getJson()
        .mapTo(Instance.class);
      assertThat(instanceInStorage.getDiscoverySuppress(), is(true));
    }
  }
}
