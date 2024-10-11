package org.folio.rest.api;

import static org.folio.rest.support.http.InterfaceUrls.instancesStorageUrl;
import static org.folio.utility.ModuleUtility.getVertx;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import org.folio.rest.persist.PostgresClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Disabled;

@Disabled
public class PublicationPeriodMigrationTest extends MigrationTestBase {
  private static final String MIGRATION_SCRIPT = loadScript("publication-period/migratePublicationPeriod.sql");
  private static final String TAG_VALUE = "test-tag";
  private static final String START_DATE = "1877";
  private static final String END_DATE = "1880";
  private static final String MULTIPLE_DATE_TYPE_ID = "8fa6d067-41ff-4362-96a0-96b16ddce267";
  private static final String SINGLE_DATE_TYPE_ID = "24a506e8-2a92-4ecc-bd09-ff849321fd5a";
  private static final String SELECT_JSONB_BY_ID =
    "SELECT jsonb FROM %s_mod_inventory_storage.instance WHERE id = $1";
  private static final String UPDATE_JSONB_WITH_PUB_PERIOD = """
    UPDATE %s_mod_inventory_storage.instance
    SET jsonb = jsonb_set(jsonb, '{publicationPeriod}', jsonb_build_object('start', $1, 'end', $2))
    WHERE id = $3
    """;
  private static final String UPDATE_JSONB_WITH_PUB_PERIOD_START_DATE = """
    UPDATE %s_mod_inventory_storage.instance
    SET jsonb = jsonb_set(jsonb, '{publicationPeriod}', jsonb_build_object('start', $1))
    WHERE id = $2
    """;

  @SneakyThrows
  @Before
  public void beforeEach() {
    StorageTestSuite.deleteAll(instancesStorageUrl(""));
    removeAllEvents();
  }

  @Test
  public void canMigratePublicationPeriodToMultipleDates() throws Exception {
    var instanceId = createInstance();

    // add "publicationPeriod" object to jsonb
    addPublicationPeriodToJsonb(instanceId, END_DATE);

    //migrate "publicationPeriod" to Dates object
    executeMultipleSqlStatements(MIGRATION_SCRIPT);

    var query = String.format(SELECT_JSONB_BY_ID, TENANT_ID);
    RowSet<Row> result = runSql(query, Tuple.of(instanceId));

    assertEquals(1, result.rowCount());
    JsonObject entry = result.iterator().next().toJson();
    JsonObject dates = entry.getJsonObject("jsonb").getJsonObject("dates");
    assertNotNull(dates);
    assertEquals(START_DATE, dates.getString("date1"));
    assertEquals(END_DATE, dates.getString("date2"));
    assertEquals(MULTIPLE_DATE_TYPE_ID, dates.getString("dateTypeId"));
  }

  @Test
  public void canMigratePublicationPeriodToSingleDates() throws Exception {
    var instanceId = createInstance();

    // add "publicationPeriod" object to jsonb
    addPublicationPeriodToJsonb(instanceId, null);

    //migrate "publicationPeriod" to Dates object
    executeMultipleSqlStatements(MIGRATION_SCRIPT);

    var query = String.format(SELECT_JSONB_BY_ID, TENANT_ID);
    RowSet<Row> result = runSql(query, Tuple.of(instanceId));

    assertEquals(1, result.rowCount());
    JsonObject entry = result.iterator().next().toJson();
    JsonObject dates = entry.getJsonObject("jsonb").getJsonObject("dates");
    assertNotNull(dates);
    assertEquals(START_DATE, dates.getString("date1"));
    assertNull(dates.getString("date2"));
    assertEquals(SINGLE_DATE_TYPE_ID, dates.getString("dateTypeId"));
  }

  @Test
  public void canNotMigrateWhenPublicationPeriodIsNull() throws Exception {
    var instanceId = createInstance();

    //migrate "publicationPeriod" to Dates object
    executeMultipleSqlStatements(MIGRATION_SCRIPT);

    var query = String.format(SELECT_JSONB_BY_ID, TENANT_ID);
    RowSet<Row> result = runSql(query, Tuple.of(instanceId));

    assertEquals(1, result.rowCount());
    JsonObject entry = result.iterator().next().toJson();
    JsonObject dates = entry.getJsonObject("jsonb").getJsonObject("dates");
    assertNull(dates);
  }

  private String createInstance() {
    var instanceId = UUID.randomUUID().toString();
    JsonObject instanceToCreate = new JsonObject()
      .put("id", instanceId)
      .put("title", "Test")
      .put("source", "FOLIO")
      .put("identifiers", new JsonArray().add(identifier(UUID_ISBN, "9781473619777")))
      .put("instanceTypeId", UUID_INSTANCE_TYPE.toString())
      .put("tags", new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))
      .put("_version", 1);

    instancesClient.create(instanceToCreate);
    return instanceId;
  }

  private void addPublicationPeriodToJsonb(String instanceId, String endDate) {
    var id = UUID.fromString(instanceId);
    if (endDate != null) {
      var query = String.format(UPDATE_JSONB_WITH_PUB_PERIOD, TENANT_ID);
      runSql(query, Tuple.of(PublicationPeriodMigrationTest.START_DATE, endDate, id));
    } else {
      var query = String.format(UPDATE_JSONB_WITH_PUB_PERIOD_START_DATE, TENANT_ID);
      runSql(query, Tuple.of(PublicationPeriodMigrationTest.START_DATE, id));
    }
  }

  @SneakyThrows
  private RowSet<Row> runSql(String sql, Tuple tuple) {
    return PostgresClient.getInstance(getVertx())
      .execute(sql, tuple)
      .toCompletionStage()
      .toCompletableFuture()
      .get(TIMEOUT, TimeUnit.SECONDS);
  }
}
