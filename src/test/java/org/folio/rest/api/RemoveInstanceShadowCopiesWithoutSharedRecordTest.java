package org.folio.rest.api;

import static org.folio.utility.ModuleUtility.getVertx;
import static org.folio.utility.ModuleUtility.prepareTenant;
import static org.folio.utility.ModuleUtility.removeTenant;
import static org.folio.utility.RestUtility.CONSORTIUM_CENTRAL_TENANT;
import static org.folio.utility.RestUtility.CONSORTIUM_MEMBER_TENANT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import org.folio.rest.persist.PostgresClient;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class RemoveInstanceShadowCopiesWithoutSharedRecordTest extends MigrationTestBase {

  private static final String TAG_VALUE = "test-tag";
  private static final String FOLIO_SOURCE = "FOLIO";
  private static final String CONSORTIUM_FOLIO_SOURCE = "CONSORTIUM-FOLIO";
  private static final String CONSORTIUM_MARC_SOURCE = "CONSORTIUM-MARC";
  private static final String SELECT_INSTANCE_COUNT_QUERY = "SELECT COUNT(*) FROM %s_mod_inventory_storage.instance";
  private static final String SELECT_JSONB_QUERY = "SELECT jsonb FROM %s_mod_inventory_storage.instance";
  private static final String SQL_SCRIPT = loadScript(
    "removeInstanceShadowCopiesWithoutSharedRecord.sql",
    RemoveInstanceShadowCopiesWithoutSharedRecordTest::replacePlaceholders);

  @SneakyThrows
  @BeforeClass
  public static void beforeClass() {
    prepareTenant(CONSORTIUM_MEMBER_TENANT, false);
    prepareTenant(CONSORTIUM_CENTRAL_TENANT, false);
  }

  @SneakyThrows
  @AfterClass
  public static void afterClass() {
    removeTenant(CONSORTIUM_MEMBER_TENANT);
    removeTenant(CONSORTIUM_CENTRAL_TENANT);
  }

  @Before
  public void beforeEach() {
    clearData(CONSORTIUM_MEMBER_TENANT);
    clearData(CONSORTIUM_CENTRAL_TENANT);
    removeAllEvents();
  }

  @Test
  public void shouldRemoveInstanceShadowCopiesWithoutSharedRecord() throws Exception {
    //instance shadow copies without shared record
    createInstance(UUID.randomUUID().toString(), CONSORTIUM_FOLIO_SOURCE, CONSORTIUM_MEMBER_TENANT);
    createInstance(UUID.randomUUID().toString(), CONSORTIUM_MARC_SOURCE, CONSORTIUM_MEMBER_TENANT);

    // check the number of instances in the table before executing the script.
    String instanceCountQuery = String.format(SELECT_INSTANCE_COUNT_QUERY, CONSORTIUM_MEMBER_TENANT);
    RowSet<Row> queryResult = runSql(instanceCountQuery);
    assertEquals(2L, queryResult.iterator().next().getLong("count").longValue());

    executeMultipleSqlStatements(SQL_SCRIPT);

    String query = String.format(SELECT_INSTANCE_COUNT_QUERY, CONSORTIUM_MEMBER_TENANT);
    RowSet<Row> result = runSql(query);

    assertEquals(0, result.iterator().next().getLong("count").longValue());
  }

  @Test
  public void shouldNotRemoveInstanceShadowCopiesWithSharedRecord() throws Exception {
    var instanceId = UUID.randomUUID().toString();
    createInstance(instanceId, CONSORTIUM_FOLIO_SOURCE, CONSORTIUM_MEMBER_TENANT);
    createInstance(instanceId, FOLIO_SOURCE, CONSORTIUM_CENTRAL_TENANT);

    executeMultipleSqlStatements(SQL_SCRIPT);

    String query = String.format(SELECT_JSONB_QUERY, CONSORTIUM_MEMBER_TENANT);
    RowSet<Row> result = runSql(query);

    assertEquals(1, result.rowCount());
    JsonObject jsonb = result.iterator().next().getJsonObject("jsonb");
    assertNotNull(jsonb);
    assertEquals(instanceId, jsonb.getString("id"));
    assertEquals(CONSORTIUM_FOLIO_SOURCE, jsonb.getString("source"));
  }

  @Test
  public void shouldNotRemoveInstanceShadowCopiesWithoutConsortiumSourcePrefix() throws Exception {
    var instanceId = UUID.randomUUID().toString();
    createInstance(instanceId, FOLIO_SOURCE, CONSORTIUM_MEMBER_TENANT);

    executeMultipleSqlStatements(SQL_SCRIPT);

    String query = String.format(SELECT_JSONB_QUERY, CONSORTIUM_MEMBER_TENANT);
    RowSet<Row> result = runSql(query);

    assertEquals(1, result.rowCount());
    JsonObject jsonb = result.iterator().next().getJsonObject("jsonb");
    assertNotNull(jsonb);
    assertEquals(instanceId, jsonb.getString("id"));
    assertEquals(FOLIO_SOURCE, jsonb.getString("source"));
  }

  @SneakyThrows
  private RowSet<Row> runSql(String sql) {
    return PostgresClient.getInstance(getVertx(), CONSORTIUM_MEMBER_TENANT)
      .execute(sql)
      .toCompletionStage()
      .toCompletableFuture()
      .get(TIMEOUT, TimeUnit.SECONDS);
  }

  private void createInstance(String instanceId, String source, String tenant) {
    JsonObject instanceToCreate = new JsonObject()
      .put("id", instanceId)
      .put("title", "Test")
      .put("source", source)
      .put("identifiers", new JsonArray().add(identifier(UUID_ISBN, "9781473619777")))
      .put("instanceTypeId", UUID_INSTANCE_TYPE.toString())
      .put("tags", new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))
      .put("_version", 1);

    instancesClient.create(instanceToCreate, tenant);
  }

  private static String replacePlaceholders(String resource) {
    return resource
      .replace("${central}_${mymodule}",
        String.format("%s_mod_inventory_storage", CONSORTIUM_CENTRAL_TENANT))
      .replace("${myuniversity}_${mymodule}",
        String.format("%s_mod_inventory_storage", CONSORTIUM_MEMBER_TENANT));
  }
}
