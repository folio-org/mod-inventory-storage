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
import org.folio.util.ResourceUtil;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class PreviouslyHeldDataUpgradeTest extends TestBaseWithInventoryUtil {
  private static final String SET_DEFAULT_PREVIOUSLY_HELD = ResourceUtil
    .asString("templates/db_scripts/setPreviouslyHeldDefault.sql")
    .replace("${myuniversity}_${mymodule}", "test_mod_inventory_storage");
  private static final Vertx VERTX = Vertx.vertx();
  private static final UUID INSTANCE_ID = UUID.randomUUID();

  @Test
  public void canMigrateToDefaultPreviouslyHeldValue() throws
    InterruptedException, ExecutionException, TimeoutException {
    instancesClient.create(instance(INSTANCE_ID));
    String query = "UPDATE test_mod_inventory_storage.instance SET jsonb = jsonb - 'previouslyHeld';";

    runSql(query);

    String migrationQuery =
      "SELECT jsonb FROM test_mod_inventory_storage.instance WHERE id = '" + INSTANCE_ID + "';";

    RowSet<Row> result = runSql(migrationQuery);
    assertEquals(1, result.rowCount());
    JsonObject entry = result.iterator().next().toJson();
    assertNull(entry.getJsonObject("jsonb").getBoolean("previouslyHeld"));

    runSql(SET_DEFAULT_PREVIOUSLY_HELD);

    RowSet<Row> migrationResult = runSql(migrationQuery);
    assertEquals(1, migrationResult.rowCount());
    JsonObject migrationEntry = migrationResult.iterator().next().toJson();
    assertEquals(false, migrationEntry.getJsonObject("jsonb").getBoolean("previouslyHeld"));
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

    return future.get(TIMEOUT, TimeUnit.SECONDS);
  }
}
