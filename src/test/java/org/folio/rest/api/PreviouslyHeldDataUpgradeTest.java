package org.folio.rest.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.folio.rest.persist.PostgresClient;
import org.folio.util.ResourceUtil;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.core.Vertx;
import junitparams.JUnitParamsRunner;

@RunWith(JUnitParamsRunner.class)
public class PreviouslyHeldDataUpgradeTest extends TestBaseWithInventoryUtil {
  private static Vertx vertx = Vertx.vertx();
  private static UUID instanceId = UUID.randomUUID();
  private static final String SET_DEFAULT_PREVIOUSLY_HELD = ResourceUtil
    .asString("templates/db_scripts/setPreviouslyHeldDefault.sql")
    .replace("${myuniversity}_${mymodule}", "test_tenant_mod_inventory_storage");

  @Test
  public void canMigrateToDefaultPreviouslyHeldValue() throws 
    InterruptedException, ExecutionException, TimeoutException{
    instancesClient.create(instance(instanceId));
    String query = "UPDATE test_tenant_mod_inventory_storage.instance SET jsonb = jsonb - 'previouslyHeld';";
             
    runSql(query);
    
    String migrationQuery = "SELECT jsonb FROM test_tenant_mod_inventory_storage.instance WHERE id = '" + instanceId.toString() + "';";
    
    RowSet<Row> result = runSql(migrationQuery);
    assertEquals(result.rowCount(), 1);
    JsonObject entry = result.iterator().next().toJson();
    assertNull(entry.getJsonObject("jsonb").getBoolean("previouslyHeld"));

    runSql(SET_DEFAULT_PREVIOUSLY_HELD);

    RowSet<Row> migrationResult = runSql(migrationQuery);
    assertEquals(migrationResult.rowCount(), 1);
    JsonObject migrationEntry = migrationResult.iterator().next().toJson();
    assertEquals(false, migrationEntry.getJsonObject("jsonb").getBoolean("previouslyHeld")); 
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
