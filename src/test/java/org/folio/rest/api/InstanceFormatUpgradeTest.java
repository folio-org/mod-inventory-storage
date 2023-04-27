package org.folio.rest.api;

import static org.junit.Assert.assertEquals;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import junitparams.JUnitParamsRunner;
import lombok.SneakyThrows;
import org.folio.rest.persist.PostgresClient;
import org.folio.util.ResourceUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class InstanceFormatUpgradeTest extends TestBaseWithInventoryUtil {
  private static final String ADD_INSTANCE_FORMAT_AUDIO_BELT = ResourceUtil
    .asString("templates/db_scripts/addInstanceFormatsAudioBelt.sql")
    .replace("${myuniversity}_${mymodule}", "test_tenant_mod_inventory_storage");
  private static final Vertx VERTX = Vertx.vertx();

  @SneakyThrows
  @Before
  public void beforeEach() {
    clearData();
    setupMaterialTypes();
    setupLoanTypes();
    setupLocations();
    removeAllEvents();
  }

  @Test
  public void canMigrateToDefaultPreviouslyHeldValue() throws
    InterruptedException, ExecutionException, TimeoutException {

    runSql(ADD_INSTANCE_FORMAT_AUDIO_BELT);

    String query =
      "SELECT jsonb FROM test_tenant_mod_inventory_storage.instance_format WHERE id "
        + "= '0d9b1c3d-2d13-4f18-9472-cc1b91bf1752'";

    RowSet<Row> result = runSql(query);
    assertEquals(1, result.rowCount());
    JsonObject resultEntry = result.iterator().next().toJson();
    assertEquals("sb", resultEntry.getJsonObject("jsonb").getString("code"));
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
