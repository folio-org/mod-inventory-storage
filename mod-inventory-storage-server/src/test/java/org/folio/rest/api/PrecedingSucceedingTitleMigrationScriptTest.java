package org.folio.rest.api;

import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.vertx.core.json.JsonObject;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.UUID;
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
  private static final String REMOVAL_SCRIPT = loadScript("removeOldPrecedingSucceedingTitles.sql");
  private static final String PRECEDING_SUCCEEDING_TYPE_ID = "cde80cc2-0c8b-4672-82d4-721e51dcb990";
  private static final String BOUND_WITH_TYPE_ID = "758f13db-ffb4-440e-bb10-8a364aa6cb4a";

  private IndividualResource precedingRelationship1;
  private IndividualResource precedingRelationship2;

  @SneakyThrows
  @Before
  public void beforeEach() {
    cleanupTestData();
    setupRequiredData();
    createTestInstances();
    removeAllEvents();
  }

  @Test
  public void canMigratePrecedingSucceedingTitles() {
    executeMultipleSqlStatements(MIGRATION_SCRIPT);
    var precedingSucceedingTitles = precedingSucceedingTitleClient.getAll();

    assertTitlesMatch(precedingSucceedingTitles);
  }

  @Test
  public void canRemoveOldPrecedingSucceedingTitles() {
    executeMultipleSqlStatements(REMOVAL_SCRIPT);

    assertRelationshipTypeRemoved();
    assertNoRelationshipsExist();
  }

  private void assertTitlesMatch(List<JsonObject> precedingSucceedingTitles) {
    assertThat(precedingSucceedingTitles.size(), is(2));
    var title1 = findPrecedingSucceedingTitle(precedingSucceedingTitles, precedingRelationship1);
    var title2 = findPrecedingSucceedingTitle(precedingSucceedingTitles, precedingRelationship2);
    assertPrecedingSucceedingTitle(title1, precedingRelationship1);
    assertPrecedingSucceedingTitle(title2, precedingRelationship2);
  }

  private void cleanupTestData() {
    StorageTestSuite.deleteAll(TENANT_ID, INSTANCE_RELATIONSHIP_TABLE);
    StorageTestSuite.deleteAll(TENANT_ID, INSTANCE_RELATIONSHIP_TYPE_TABLE);
    StorageTestSuite.deleteAll(TENANT_ID, PRECEDING_SUCCEEDING_TITLE_TABLE);
    deleteAllById(precedingSucceedingTitleClient);
    clearData();
  }

  private void setupRequiredData() {
    setupMaterialTypes();
    setupLoanTypes();
    setupLocations();
    createRelationshipTypes();
  }

  private void createRelationshipTypes() {
    createInstanceRelationType(PRECEDING_SUCCEEDING_TYPE_ID, "preceding-succeeding");
    createInstanceRelationType(BOUND_WITH_TYPE_ID, "bound-with");
  }

  private void createTestInstances() {
    UUID[] instanceIds = createThreeInstances();
    createTestRelationshipsWithInstances(instanceIds[0], instanceIds[1], instanceIds[2]);
  }

  private UUID[] createThreeInstances() {
    UUID[] ids = new UUID[] {UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};
    for (UUID id : ids) {
      instancesClient.create(instance(id));
    }
    return ids;
  }

  private void createTestRelationshipsWithInstances(UUID instance1Id, UUID instance2Id, UUID instance3Id) {
    precedingRelationship1 = createInstanceRelationship(
      new InstanceRelationshipBuilder(instance1Id, instance2Id, PRECEDING_SUCCEEDING_TYPE_ID));
    precedingRelationship2 = createInstanceRelationship(
      new InstanceRelationshipBuilder(instance2Id, instance3Id, PRECEDING_SUCCEEDING_TYPE_ID));
    createInstanceRelationship(
      new InstanceRelationshipBuilder(instance1Id, instance3Id, BOUND_WITH_TYPE_ID));
  }

  private JsonObject findPrecedingSucceedingTitle(List<JsonObject> titles, IndividualResource relationship) {
    return titles.stream()
      .filter(title -> title.getString("id").equals(relationship.getJson().getString("id")))
      .findFirst()
      .orElseThrow(() -> new AssertionError("Preceding-succeeding title not found"));
  }

  private void assertPrecedingSucceedingTitle(JsonObject precedingSucceedingTitle,
                                              IndividualResource instanceRelationship) {
    JsonObject relationshipJson = instanceRelationship.getJson();
    assertThat(precedingSucceedingTitle.getString("id"), is(relationshipJson.getString("id")));
    assertThat(precedingSucceedingTitle.getString("precedingInstanceId"),
      is(relationshipJson.getString("superInstanceId")));
    assertThat(precedingSucceedingTitle.getString("succeedingInstanceId"),
      is(relationshipJson.getString("subInstanceId")));
    assertThat(precedingSucceedingTitle.getJsonObject("metadata").getString("createdDate"),
      is(relationshipJson.getJsonObject("metadata").getString("createdDate")));
    assertThat(precedingSucceedingTitle.getJsonObject("metadata").getString("updatedDate"),
      is(relationshipJson.getJsonObject("metadata").getString("updatedDate")));
  }

  private void assertRelationshipTypeRemoved() {
    Response response = instanceRelationshipTypesClient
      .getById(UUID.fromString(PRECEDING_SUCCEEDING_TYPE_ID));
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  private void assertNoRelationshipsExist() {
    List<JsonObject> relationships = instanceRelationshipsClient
      .getByQuery("?query=instance_relationship_type=" + PRECEDING_SUCCEEDING_TYPE_ID);
    assertThat(relationships.size(), is(0));
  }

  private void createInstanceRelationType(String id, String name) {
    instanceRelationshipTypesClient.create(new JsonObject()
      .put("id", id)
      .put("name", name));
  }

  private IndividualResource createInstanceRelationship(InstanceRelationshipBuilder builder) {
    return instanceRelationshipsClient.create(builder.build());
  }

  private record InstanceRelationshipBuilder(UUID instance1Id, UUID instance2Id, String relationTypeId) {

    public JsonObject build() {
      return new InstanceRelationship(
        instance1Id.toString(),
        instance2Id.toString(),
        relationTypeId
      ).getJson();
    }
  }
}
