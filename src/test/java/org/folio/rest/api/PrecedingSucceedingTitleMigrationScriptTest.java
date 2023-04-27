package org.folio.rest.api;

import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.vertx.core.json.JsonObject;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import lombok.SneakyThrows;
import org.folio.rest.api.entities.InstanceRelationship;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.Response;
import org.junit.Before;
import org.junit.Test;

public class PrecedingSucceedingTitleMigrationScriptTest extends MigrationTestBase {
  private static final String PRECEDING_SUCCEEDING_TITLE_TABLE = "preceding_succeeding_title";
  private static final String INSTANCE_RELATIONSHIP_TABLE = "instance_relationship";
  private static final String INSTANCE_RELATIONSHIP_TYPE_TABLE = "instance_relationship_type";
  private static final String MIGRATION_SCRIPT = loadScript("migratePrecedingSucceedingTitles.sql");
  private static final String REMOVE_OLD_PRECEDING_SUCCEEDING_TITLES_SCRIPT =
    loadScript("removeOldPrecedingSucceedingTitles.sql");
  private static final String PRECEDING_SUCCEEDING_RELATIONSHIP_TYPE_ID = "cde80cc2-0c8b-4672-82d4-721e51dcb990";
  private static final String BOUND_WITH_INSTANCE_RELATIONSHIP_TYPE_ID = "758f13db-ffb4-440e-bb10-8a364aa6cb4a";
  private IndividualResource instanceRelationship1;
  private IndividualResource instanceRelationship2;

  @SneakyThrows
  @Before
  public void beforeEach() {
    StorageTestSuite.deleteAll(TENANT_ID, INSTANCE_RELATIONSHIP_TABLE);
    StorageTestSuite.deleteAll(TENANT_ID, INSTANCE_RELATIONSHIP_TYPE_TABLE);
    StorageTestSuite.deleteAll(TENANT_ID, PRECEDING_SUCCEEDING_TITLE_TABLE);

    deleteAllById(precedingSucceedingTitleClient);
    clearData();
    setupMaterialTypes();
    setupLoanTypes();
    setupLocations();

    final UUID instance1Id = UUID.randomUUID();
    final UUID instance2Id = UUID.randomUUID();
    final UUID instance3Id = UUID.randomUUID();

    createInstanceRelationType(PRECEDING_SUCCEEDING_RELATIONSHIP_TYPE_ID,
      "preceding-succeeding");
    createInstanceRelationType(BOUND_WITH_INSTANCE_RELATIONSHIP_TYPE_ID,
      "bound-with");

    instancesClient.create(instance(instance1Id));
    instancesClient.create(instance(instance2Id));
    instancesClient.create(instance(instance3Id));

    instanceRelationship1 = createInstanceRelationship(instance1Id, instance2Id,
      PRECEDING_SUCCEEDING_RELATIONSHIP_TYPE_ID);
    instanceRelationship2 = createInstanceRelationship(instance2Id, instance3Id,
      PRECEDING_SUCCEEDING_RELATIONSHIP_TYPE_ID);
    createInstanceRelationship(instance1Id, instance3Id, BOUND_WITH_INSTANCE_RELATIONSHIP_TYPE_ID);

    removeAllEvents();
  }

  @Test
  public void canMigratePrecedingSucceedingTitles() throws Exception {

    executeMultipleSqlStatements(MIGRATION_SCRIPT);

    List<JsonObject> precedingSucceedingTitles = precedingSucceedingTitleClient.getAll();

    assertThat(precedingSucceedingTitles.size(), is(2));

    assertPrecedingSucceedingTitle(precedingSucceedingTitles.get(0), instanceRelationship1);
    assertPrecedingSucceedingTitle(precedingSucceedingTitles.get(1), instanceRelationship2);
  }

  @Test
  public void canRemoveOldPrecedingSucceedingTitles() throws Exception {

    executeMultipleSqlStatements(REMOVE_OLD_PRECEDING_SUCCEEDING_TITLES_SCRIPT);

    Response response = instanceRelationshipTypesClient
      .getById(UUID.fromString(PRECEDING_SUCCEEDING_RELATIONSHIP_TYPE_ID));
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));

    List<JsonObject> instanceRelationships = instanceRelationshipsClient
      .getByQuery("?query=instance_relationship_type=" + PRECEDING_SUCCEEDING_RELATIONSHIP_TYPE_ID);
    assertThat(instanceRelationships.size(), is(0));
  }

  private void createInstanceRelationType(String id, String name) throws Exception {
    instanceRelationshipTypesClient.create(new JsonObject()
      .put("id", id)
      .put("name", name));
  }

  private IndividualResource createInstanceRelationship(UUID instance1Id, UUID instance2Id,
                                                        String instanceRelationTypeId)
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    JsonObject instanceRelationshipRequestObject = new InstanceRelationship(
      instance1Id.toString(), instance2Id.toString(), instanceRelationTypeId).getJson();

    return instanceRelationshipsClient.create(instanceRelationshipRequestObject);
  }

  private void assertPrecedingSucceedingTitle(JsonObject precedingSucceedingTitle,
                                              IndividualResource instanceRelationship) {

    JsonObject instanceRelationshipJson = instanceRelationship.getJson();
    assertThat(precedingSucceedingTitle.getString("id"), is(instanceRelationshipJson.getString("id")));
    assertThat(precedingSucceedingTitle.getString("precedingInstanceId"),
      is(instanceRelationshipJson.getString("superInstanceId")));
    assertThat(precedingSucceedingTitle.getString("succeedingInstanceId"),
      is(instanceRelationshipJson.getString("subInstanceId")));
    assertThat(precedingSucceedingTitle.getJsonObject("metadata").getString("createdDate"),
      is(instanceRelationshipJson.getJsonObject("metadata").getString("createdDate")));
    assertThat(precedingSucceedingTitle.getJsonObject("metadata").getString("updatedDate"),
      is(instanceRelationshipJson.getJsonObject("metadata").getString("updatedDate")));
  }
}
