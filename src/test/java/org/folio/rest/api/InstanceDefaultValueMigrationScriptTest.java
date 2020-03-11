package org.folio.rest.api;

import static org.folio.rest.support.http.InterfaceUrls.instancesStorageUrl;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.support.IndividualResource;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class InstanceDefaultValueMigrationScriptTest
  extends MigrationTestBase {

  @Before
  public void beforeEach() {
    StorageTestSuite.deleteAll(instancesStorageUrl(""));
  }

  @Test
  public void canSetDefaultValueIfNotPresent() throws Exception {
    List<IndividualResource> allInstances = createInstances(3);

    for (IndividualResource instance : allInstances) {
      assertThat(unsetJsonbProperty("instance", instance.getId(),
        getFieldName()).getUpdated(), is(1));
    }

    executeMultipleSqlStatements(getMigrationScript());

    verifyDefaultValue(allInstances);
  }

  @Test
  public void instancesWithFieldsAreNotUpdated() throws Exception {
    List<IndividualResource> instancesWithDefaultValue = createInstances(2);
    List<IndividualResource> instancesWithField = new ArrayList<>(2);

    instancesWithField.add(
      instancesClient.create(smallAngryWithField()));
    instancesWithField.add(
      instancesClient.create(smallAngryWithField()));

    for (IndividualResource instance : instancesWithDefaultValue) {
      assertThat(unsetJsonbProperty("instance", instance.getId(),
        getFieldName()).getUpdated(), is(1));
    }

    executeMultipleSqlStatements(getMigrationScript());

    verifyDefaultValue(instancesWithDefaultValue);
    verifyFieldValue(instancesWithField);
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

  private JsonObject smallAngryWithField() {
    return smallAngryPlanet()
      .put(getFieldName(), true);
  }

  private void verifyDefaultValue(List<IndividualResource> instances) throws Exception {

    for (IndividualResource instance : instances) {
      Instance instanceInStorage = instancesClient.getById(instance.getId()).getJson()
        .mapTo(Instance.class);
      assertThat(getFieldValue(instanceInStorage), is(false));
    }
  }

  private void verifyFieldValue(List<IndividualResource> instances) throws Exception {

    for (IndividualResource instance : instances) {
      Instance instanceInStorage = instancesClient.getById(instance.getId()).getJson()
        .mapTo(Instance.class);
      assertThat(getFieldValue(instanceInStorage), is(true));
    }
  }

  protected abstract Boolean getFieldValue(Instance instanceInStorage);

  protected abstract String getFieldName();

  protected abstract String getMigrationScript();
}
