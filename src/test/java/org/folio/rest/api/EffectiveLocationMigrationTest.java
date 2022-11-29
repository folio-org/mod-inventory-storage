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
  private static Vertx vertx = Vertx.vertx();
  private static UUID instanceId = UUID.randomUUID();
  private static UUID holdingsId = UUID.randomUUID();
  private static final String SET_EFFECTIVE_LOCATION = ResourceUtil
    .asString("templates/db_scripts/setEffectiveHoldingsLocation.sql")
    .replace("${myuniversity}_${mymodule}", "test_tenant_mod_inventory_storage");
  private static String removeExistingField = "UPDATE test_tenant_mod_inventory_storage.holdings_record SET jsonb = jsonb - 'effectiveLocationId';";
  private static String query = "SELECT jsonb FROM test_tenant_mod_inventory_storage.holdings_record WHERE id = '" + holdingsId.toString() + "';";

  @Before
  public void beforeEach() {
    instancesClient.create(instance(instanceId));
  }

  @After
  public void afterEach() {
    holdingsClient.delete(holdingsId);
    instancesClient.delete(instanceId);
  }

  @Test
  public void canMigrateToEffectiveLocationForItemsWithPermanentLocationOnly() throws 
    InterruptedException, ExecutionException, TimeoutException{
    holdingsClient.create(new HoldingRequestBuilder()
    .withId(holdingsId)
    .forInstance(instanceId)
    .withPermanentLocation(mainLibraryLocationId));

    runSql(removeExistingField);

    RowSet<Row> result = runSql(query);
    assertEquals(result.rowCount(), 1);
    JsonObject entry = result.iterator().next().toJson();
    assertNull(entry.getJsonObject("jsonb").getString("effectiveLocationId"));

    runSql(SET_EFFECTIVE_LOCATION);

    RowSet<Row> migrationResult = runSql(query);
    assertEquals(migrationResult.rowCount(), 1);
    JsonObject migrationEntry = migrationResult.iterator().next().toJson();
    assertEquals(mainLibraryLocationId.toString(), migrationEntry.getJsonObject("jsonb").getString("effectiveLocationId")); 
  }

  @Test
  public void canMigrateToEffectiveLocationForItemsWithTemporaryLocation() throws 
    InterruptedException, ExecutionException, TimeoutException{
    holdingsClient.create(new HoldingRequestBuilder()
    .withId(holdingsId)
    .forInstance(instanceId)
    .withTemporaryLocation(annexLibraryLocationId)
    .withPermanentLocation(mainLibraryLocationId));

    runSql(removeExistingField);

    RowSet<Row> result = runSql(query);
    assertEquals(result.rowCount(), 1);
    JsonObject entry = result.iterator().next().toJson();
    assertNull(entry.getJsonObject("jsonb").getString("effectiveLocationId"));

    runSql(SET_EFFECTIVE_LOCATION);

    RowSet<Row> migrationResult = runSql(query);
    assertEquals(migrationResult.rowCount(), 1);
    JsonObject migrationEntry = migrationResult.iterator().next().toJson();
    assertEquals(annexLibraryLocationId.toString(), migrationEntry.getJsonObject("jsonb").getString("effectiveLocationId")); 
  }

  private RowSet<Row> runSql(String sql) throws 
    InterruptedException, ExecutionException, TimeoutException {
    CompletableFuture<RowSet<Row>> future = new CompletableFuture<>();

    PostgresClient.getInstance(vertx).execute(sql, handler -> {
      if (handler.failed()) {
        future.completeExceptionally(handler.cause());
      }
      future.complete(handler.result());
    });

    return future.get(10, TimeUnit.SECONDS);
  }
}
