package org.folio.services.migration;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.rest.api.TestBase;
import org.folio.services.migration.async.AsyncMigrationConsumerVerticle;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class AsyncMigrationVerticleTest extends TestBase {

  @Test
  public void shouldDeployVerticle(TestContext context) {
    Async async = context.async();
    DeploymentOptions options = new DeploymentOptions()
      .setConfig(new JsonObject()
        .put("x-okapi-tenant", "test_tenant"))
      .setWorker(true);

    Promise<String> promise = Promise.promise();
    Vertx.vertx().deployVerticle(AsyncMigrationConsumerVerticle.class.getName(), options, promise);

    promise.future().onComplete(ar -> {
      context.assertTrue(ar.succeeded());
      async.complete();
    });
  }
}
