package org.folio.rest.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import junitparams.JUnitParamsRunner;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.builders.HoldingRequestBuilder;
import org.folio.util.ResourceUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class EffectiveLocationMigrationTest extends TestBaseWithInventoryUtil {
  private static final String SET_EFFECTIVE_LOCATION = ResourceUtil
    .asString("templates/db_scripts/setEffectiveHoldingsLocation.sql")
    .replace("${myuniversity}_${mymodule}", "test_tenant_mod_inventory_storage");
  private static final Vertx VERTX = Vertx.vertx();
  private static final UUID INSTANCE_ID = UUID.randomUUID();
  private static final UUID HOLDINGS_ID = UUID.randomUUID();
  private static final String REMOVE_EXISTING_FIELD =
    "UPDATE test_tenant_mod_inventory_storage.holdings_record SET jsonb = jsonb - 'effectiveLocationId';";
  private static final String QUERY =
    "SELECT jsonb FROM test_tenant_mod_inventory_storage.holdings_record WHERE id = '" + HOLDINGS_ID + "';";

  @Before
  public void beforeEach() {
    instancesClient.create(instance(INSTANCE_ID));
  }

  @After
  public void afterEach() {
    holdingsClient.delete(HOLDINGS_ID);
    instancesClient.delete(INSTANCE_ID);
  }

  @Test
  public void canMigrateToEffectiveLocationForItemsWithPermanentLocationOnly() throws
    InterruptedException, ExecutionException, TimeoutException {
    holdingsClient.create(new HoldingRequestBuilder()
      .withId(HOLDINGS_ID)
      .forInstance(INSTANCE_ID)
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID));

    runSql(REMOVE_EXISTING_FIELD);

    RowSet<Row> result = runSql(QUERY);
    assertEquals(1, result.rowCount());
    JsonObject entry = result.iterator().next().toJson();
    assertNull(entry.getJsonObject("jsonb").getString("effectiveLocationId"));

    runSql(SET_EFFECTIVE_LOCATION);

    RowSet<Row> migrationResult = runSql(QUERY);
    assertEquals(1, migrationResult.rowCount());
    JsonObject migrationEntry = migrationResult.iterator().next().toJson();
    assertEquals(MAIN_LIBRARY_LOCATION_ID.toString(),
      migrationEntry.getJsonObject("jsonb").getString("effectiveLocationId"));
  }

  @Test
  public void canMigrateToEffectiveLocationForItemsWithTemporaryLocation() throws
    InterruptedException, ExecutionException, TimeoutException {
    holdingsClient.create(new HoldingRequestBuilder()
      .withId(HOLDINGS_ID)
      .forInstance(INSTANCE_ID)
      .withTemporaryLocation(ANNEX_LIBRARY_LOCATION_ID)
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID));

    runSql(REMOVE_EXISTING_FIELD);

    RowSet<Row> result = runSql(QUERY);
    assertEquals(1, result.rowCount());
    JsonObject entry = result.iterator().next().toJson();
    assertNull(entry.getJsonObject("jsonb").getString("effectiveLocationId"));

    runSql(SET_EFFECTIVE_LOCATION);

    RowSet<Row> migrationResult = runSql(QUERY);
    assertEquals(1, migrationResult.rowCount());
    JsonObject migrationEntry = migrationResult.iterator().next().toJson();
    assertEquals(ANNEX_LIBRARY_LOCATION_ID.toString(),
      migrationEntry.getJsonObject("jsonb").getString("effectiveLocationId"));
  }

  private RowSet<Row> runSql(String sql) throws
    InterruptedException, ExecutionException, TimeoutException {
    CompletableFuture<RowSet<Row>> future = new CompletableFuture<>();

    PostgresClient.getInstance(VERTX).execute(sql, handler -> {
      if (handler.failed()) {
        future.completeExceptionally(handler.cause());
      }
      future.complete(handler.result());
    });

    return future.get(10, TimeUnit.SECONDS);
  }
}
