package org.folio.rest.api;

import static org.folio.rest.api.ItemDamagedStatusAPITest.TEST_TENANT;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.vertx.core.json.JsonObject;
import org.folio.rest.api.entities.InstanceRelationship;
import org.folio.rest.support.IndividualResource;
import org.junit.Before;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class PrecedingSucceedingTitleMigrationScriptTest extends MigrationTestBase {
  private static final String PRECEDING_SUCCEEDING_TITLE_TABLE = "preceding_succeeding_title";
  private static final String MIGRATION_SCRIPT = loadScript("migratePrecedingSucceedingTitles.sql");
  private final static String PRECEDING_SUCCEEDING_INSTANCE_RELATIONSHIP_TYPE_ID = "cde80cc2-0c8b-4672-82d4-721e51dcb990";
  private final static String BOUND_WITH_INSTANCE_RELATIONSHIP_TYPE_ID = "758f13db-ffb4-440e-bb10-8a364aa6cb4a";

  @Before
  public void beforeEach() {
    StorageTestSuite.deleteAll(TEST_TENANT, PRECEDING_SUCCEEDING_TITLE_TABLE);
  }

  @Test
  public void canMigratePrecedingSucceedingTitles() throws Exception {
    final UUID instance1Id = UUID.randomUUID();
    final UUID instance2Id = UUID.randomUUID();
    final UUID instance3Id = UUID.randomUUID();

    instancesClient.create(instance(instance1Id));
    instancesClient.create(instance(instance2Id));
    instancesClient.create(instance(instance3Id));

    IndividualResource instanceRelationship1 = createInstanceRelationship(instance1Id, instance2Id,
      PRECEDING_SUCCEEDING_INSTANCE_RELATIONSHIP_TYPE_ID);
    IndividualResource instanceRelationship2 = createInstanceRelationship(instance2Id, instance3Id,
      PRECEDING_SUCCEEDING_INSTANCE_RELATIONSHIP_TYPE_ID);
    createInstanceRelationship(instance1Id, instance3Id, BOUND_WITH_INSTANCE_RELATIONSHIP_TYPE_ID);

    executeMultipleSqlStatements(MIGRATION_SCRIPT);

    List<JsonObject> precedingSucceedingTitles = precedingSucceedingTitleClient.getAll();

    assertThat(precedingSucceedingTitles.size(), is(2));

    assertPrecedingSucceedingTitle(precedingSucceedingTitles.get(0), instanceRelationship1);
    assertPrecedingSucceedingTitle(precedingSucceedingTitles.get(1), instanceRelationship2);
  }

  private IndividualResource createInstanceRelationship(UUID instance1Id, UUID instance2Id,
    String instanceRelationTypeId) throws MalformedURLException, InterruptedException,
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
