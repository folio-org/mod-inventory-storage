package org.folio.rest.api;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import junitparams.JUnitParamsRunner;
import org.folio.rest.persist.PostgresClient;
import org.folio.util.ResourceUtil;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;

@RunWith(JUnitParamsRunner.class)
public class InstanceFormatUpgradeTest extends TestBaseWithInventoryUtil{
  private static Vertx vertx = Vertx.vertx();
  private static final String ADD_INSTANCE_FORMAT_AUDIO_BELT = ResourceUtil
    .asString("templates/db_scripts/addInstanceFormatsAudioBelt.sql")
    .replace("${myuniversity}_${mymodule}", "test_tenant_mod_inventory_storage");


  @Test
  public void canMigrateToDefaultPreviouslyHeldValue() throws
    InterruptedException, ExecutionException, TimeoutException {

    runSql(ADD_INSTANCE_FORMAT_AUDIO_BELT);

    String query = "SELECT jsonb FROM test_tenant_mod_inventory_storage.instance_format WHERE id = '0d9b1c3d-2d13-4f18-9472-cc1b91bf1752'";

    RowSet<Row> result = runSql(query);
    assertEquals(result.rowCount(), 1);
    JsonObject resultEntry = result.iterator().next().toJson();
    assertEquals("sb", resultEntry.getJsonObject("jsonb").getString("code"));
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

    return future.get(5, TimeUnit.SECONDS);
  }
}
