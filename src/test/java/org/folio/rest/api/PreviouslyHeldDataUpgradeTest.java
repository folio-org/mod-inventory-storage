package org.folio.rest.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.isNull;

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
    public void canMigrateToDefaultPreviouslyHeldValue() {
        instancesClient.create(instance(instanceId));

        String query = "UPDATE test_tenant_mod_inventory_storage.instance SET jsonb = jsonb - 'previouslyHeld';";
             
        runSql(query);

        JsonObject instance = instancesClient.getById(instanceId).getJson();
        assertThat(instance.getBoolean("previouslyHeld"), isNull());

        runSql(SET_DEFAULT_PREVIOUSLY_HELD);

        JsonObject instanceAfter = instancesClient.getById(instanceId).getJson();
        assertNull(instanceAfter.getBoolean("previouslyHeld"));
    }

    private void runSql(String sql) {
        CompletableFuture<Void> future = new CompletableFuture<>();
    
        PostgresClient.getInstance(vertx).execute(sql, handler -> {
          if (handler.failed()) {
            future.completeExceptionally(handler.cause());
            return;
          }
          future.complete(null);
        });
    
        try {
          future.get(1, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
          throw new RuntimeException(e);
        }
    }
}
