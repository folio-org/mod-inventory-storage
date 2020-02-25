package org.folio.rest.api;

import static org.folio.rest.support.http.InterfaceUrls.instancesStorageUrl;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.support.IndividualResource;
import org.junit.Before;
import org.junit.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class InstanceDiscoverySuppressMigrationScriptTest extends MigrationTestBase {
  private static final String DISCOVERY_SUPPRESS = "discoverySuppress";
  private static final String MIGRATION_SCRIPT
    = loadScript("populateDiscoverySuppressIfNotSet.sql");

  @Before
  public void beforeEach() {
    StorageTestSuite.deleteAll(instancesStorageUrl(""));
  }

  @Test
  public void canSetDiscoverySuppressIfNotPresent() throws Exception {
    List<IndividualResource> allInstances = createInstances(3);

    for (IndividualResource instance : allInstances) {
      assertThat(unsetJsonbProperty("instance", instance.getId(),
        DISCOVERY_SUPPRESS).getUpdated(), is(1));
    }

    executeMultipleSqlStatements(MIGRATION_SCRIPT);

    verifyNotSuppressed(allInstances);
  }

  @Test
  public void discoverySuppressedInstancesAreNotUpdated() throws Exception {
    List<IndividualResource> notSuppressedInstances = createInstances(2);
    List<IndividualResource> suppressedInstances = new ArrayList<>(2);

    suppressedInstances.add(
      instancesClient.create(smallAngryPlanetSuppressedFromDiscovery()));
    suppressedInstances.add(
      instancesClient.create(smallAngryPlanetSuppressedFromDiscovery()));

    for (IndividualResource instance : notSuppressedInstances) {
      assertThat(unsetJsonbProperty("instance", instance.getId(),
        DISCOVERY_SUPPRESS).getUpdated(), is(1));
    }

    executeMultipleSqlStatements(MIGRATION_SCRIPT);

    verifyNotSuppressed(notSuppressedInstances);
    verifySuppressed(suppressedInstances);
  }

  private List<IndividualResource> createInstances(int count) throws Exception {
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

  private void verifyNotSuppressed(List<IndividualResource> instances)
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    for (IndividualResource instance : instances) {
      Instance instanceInStorage = instancesClient.getById(instance.getId()).getJson()
        .mapTo(Instance.class);
      assertThat(instanceInStorage.getDiscoverySuppress(), is(false));
    }
  }

  private void verifySuppressed(List<IndividualResource> instances)
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    for (IndividualResource instance : instances) {
      Instance instanceInStorage = instancesClient.getById(instance.getId()).getJson()
        .mapTo(Instance.class);
      assertThat(instanceInStorage.getDiscoverySuppress(), is(true));
    }
  }
}
