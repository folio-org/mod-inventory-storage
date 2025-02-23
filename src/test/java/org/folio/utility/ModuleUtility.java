package org.folio.utility;

import static org.folio.rest.api.TestBase.TIMEOUT;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.RestVerticle;
import org.folio.rest.support.HttpClient;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.tools.utils.NetworkUtils;

public final class ModuleUtility {
  private static final Logger logger = LogManager.getLogger();
  private static Vertx vertx;
  private static HttpClient client;
  private static int port = 0;

  private ModuleUtility() {
    throw new UnsupportedOperationException("Cannot instantiate utility class.");
  }

  public static HttpClient getClient() {
    return client;
  }

  public static void startVerticleWebClientAndPrepareTenant(String tenantId)
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    logger.info("starting RestVerticle");

    vertx = Vertx.vertx();
    client = new HttpClient(vertx);
    port = NetworkUtils.nextFreePort();
    DeploymentOptions options = new DeploymentOptions();
    options.setConfig(new JsonObject().put("http.port", port));
    startVerticle(options);

    logger.info("preparing tenant");

    prepareTenant(tenantId, false);
  }

  public static void stopVerticleAndWebClient()
    throws InterruptedException,
    ExecutionException {

    try {
      vertx.close()
        .toCompletionStage()
        .toCompletableFuture()
        .get(TIMEOUT, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      // Do not care if Vertx close times out.
      logger.debug(e.getMessage(), e);
    }

    client.getWebClient().close();
  }

  public static int getPort() {
    return port;
  }

  public static Vertx getVertx() {
    return vertx;
  }

  public static void startVerticle(DeploymentOptions options)
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    vertx.deployVerticle(RestVerticle.class, options)
      .toCompletionStage()
      .toCompletableFuture()
      .get(TIMEOUT, TimeUnit.SECONDS);
  }

  public static void prepareTenant(String tenantId, String moduleFrom, String moduleTo, boolean loadSample)
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    JsonArray ar = new JsonArray();
    ar.add(new JsonObject().put("key", "loadReference").put("value", "true"));
    ar.add(new JsonObject().put("key", "loadSample").put("value", Boolean.toString(loadSample)));

    JsonObject jo = new JsonObject();
    jo.put("parameters", ar);
    if (moduleFrom != null) {
      jo.put("module_from", moduleFrom);
    }
    jo.put("module_to", moduleTo);
    tenantOp(tenantId, jo);
  }

  public static void prepareTenant(String tenantId, boolean loadSample)
    throws InterruptedException,
    ExecutionException,
    TimeoutException {
    prepareTenant(tenantId, null, "mod-inventory-storage-1.0.0", loadSample);
  }

  public static void removeTenant(String tenantId)
    throws InterruptedException, ExecutionException, TimeoutException {

    JsonObject jo = new JsonObject();
    jo.put("purge", Boolean.TRUE);

    tenantOp(tenantId, jo);
  }

  public static void tenantOp(String tenantId, JsonObject job)
    throws InterruptedException, ExecutionException, TimeoutException {
    CompletableFuture<Response> tenantPrepared = new CompletableFuture<>();

    HttpClient client = new HttpClient(vertx);
    client.post(vertxUrl("/_/tenant"), job, tenantId,
      ResponseHandler.any(tenantPrepared));

    Response response = tenantPrepared.get(60, TimeUnit.SECONDS);

    String failureMessage = String.format("Tenant post failed: %s: %s",
      response.getStatusCode(), response.getBody());

    // wait if not complete ...
    if (response.getStatusCode() == 201) {
      String id = response.getJson().getString("id");

      tenantPrepared = new CompletableFuture<>();
      client.get(vertxUrl("/_/tenant/" + id + "?wait=60000"), tenantId,
        ResponseHandler.any(tenantPrepared));
      // The extra 1 in 61 is intentionally added to rule out potential
      // real-time timing issues with the 60 second wait above.
      response = tenantPrepared.get(61, TimeUnit.SECONDS);

      failureMessage = String.format("Tenant get failed: %s: %s",
        response.getStatusCode(), response.getBody());

      if (response.getStatusCode() == 200 && response.getJson().containsKey("error")) {
        throw new IllegalStateException(response.getJson().getString("error"));
      }

      assertThat(failureMessage, response.getStatusCode(), is(200));
    } else {
      assertThat(failureMessage, response.getStatusCode(), is(204));
    }
  }

  public static URL vertxUrl(String path) {
    try {
      return new URI("http://localhost:%d%s".formatted(getPort(), path)).toURL();
    } catch (MalformedURLException | URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }
}
