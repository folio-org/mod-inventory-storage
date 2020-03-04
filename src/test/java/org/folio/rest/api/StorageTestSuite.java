package org.folio.rest.api;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.UpdateResult;
import org.folio.rest.RestVerticle;
import org.folio.rest.impl.StorageHelperTest;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.HttpClient;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.rest.unit.ItemDamagedStatusAPIUnitTest;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(Suite.class)

@Suite.SuiteClasses({
  InstanceStorageTest.class,
  InstanceBulkTest.class,
  HoldingsStorageTest.class,
  ItemStorageTest.class,
  HoldingsTypeTest.class,
  LoanTypeTest.class,
  MaterialTypeTest.class,
  ContributorTypesTest.class,
  ShelfLocationsTest.class,
  LocationUnitTest.class,
  LocationsTest.class,
  ServicePointTest.class,
  ServicePointsUserTest.class,
  StorageHelperTest.class,
  InstanceRelationshipsTest.class,
  ReferenceTablesTest.class,
  ItemDamagedStatusAPITest.class,
  ItemDamagedStatusAPIUnitTest.class,
  ItemEffectiveLocationTest.class,
  SampleDataTest.class,
  HridSettingsStorageTest.class,
  HridSettingsStorageParameterizedTest.class,
  ItemCopyNumberMigrationScriptTest.class,
  ItemEffectiveCallNumberComponentsTest.class,
  ItemEffectiveCallNumberDataUpgradeTest.class,
  ModesOfIssuanceMigrationScriptTest.class,
  PrecedingSucceedingTitleTest.class
})
public class StorageTestSuite {
  public static final String TENANT_ID = "test_tenant";

  private static Vertx vertx;
  private static int port;

  private StorageTestSuite() {
    throw new UnsupportedOperationException("Cannot instantiate utility class.");
  }

  public static URL storageUrl(String path) {
    try {
      return new URL("http", "localhost", port, path);
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  public static Vertx getVertx() {
    return vertx;
  }

  @BeforeClass
  public static void before()
    throws Exception {

    // tests expect English error messages only, no Danish/German/...
    Locale.setDefault(Locale.US);

    vertx = Vertx.vertx();

    String useExternalDatabase = System.getProperty(
      "org.folio.inventory.storage.test.database",
      "embedded");

    switch (useExternalDatabase) {
      case "environment":
        System.out.println("Using environment settings");
        break;

      case "external":
        String postgresConfigPath = System.getProperty(
          "org.folio.inventory.storage.test.config",
          "/postgres-conf-local.json");

        PostgresClient.setConfigFilePath(postgresConfigPath);
        break;
      case "embedded":
        PostgresClient.setIsEmbedded(true);
        PostgresClient.getInstance(vertx).startEmbeddedPostgres();
        break;
      default:
        String message = "No understood database choice made." +
          "Please set org.folio.inventory.storage.test.config" +
          "to 'external', 'environment' or 'embedded'";

        throw new Exception(message);
    }

    port = NetworkUtils.nextFreePort();

    DeploymentOptions options = new DeploymentOptions();

    options.setConfig(new JsonObject().put("http.port", port));
    options.setWorker(true);

    startVerticle(options);

    prepareTenant(TENANT_ID, false);
  }

  @AfterClass
  public static void after()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    removeTenant(TENANT_ID);

    CompletableFuture<String> undeploymentComplete = new CompletableFuture<>();

    PostgresClient.stopEmbeddedPostgres();

    vertx.close(res -> {
      if (res.succeeded()) {
        undeploymentComplete.complete(null);
      } else {
        undeploymentComplete.completeExceptionally(res.cause());
      }
    });
    PostgresClient.stopEmbeddedPostgres();
    undeploymentComplete.get(20, TimeUnit.SECONDS);
  }

  static void deleteAll(URL rootUrl) {
    HttpClient client = new HttpClient(getVertx());

    CompletableFuture<Response> deleteAllFinished = new CompletableFuture<>();

    try {
      client.delete(rootUrl, TENANT_ID,
        ResponseHandler.any(deleteAllFinished));

      Response response = deleteAllFinished.get(5, TimeUnit.SECONDS);

      if (response.getStatusCode() != 204) {
        Assert.fail("Delete all preparation failed: " +
          response.getBody());
      }
    } catch (Exception e) {
      Assert.fail("WARNING!!!!! Unable to delete all: " + e.getMessage());
    }
  }

  static void checkForMismatchedIDs(String table) {
    try {
      ResultSet results = getRecordsWithUnmatchedIds(
        TENANT_ID, table);

      Integer mismatchedRowCount = results.getNumRows();

      assertThat(mismatchedRowCount, is(0));
    } catch (Exception e) {
      System.out.println(
        "WARNING!!!!! Unable to determine mismatched ID rows");
    }
  }

  protected static Boolean deleteAll(String tenantId, String tableName) {
    CompletableFuture<Boolean> cf = new CompletableFuture<>();

    try {
      PostgresClient postgresClient = PostgresClient.getInstance(getVertx(), tenantId);

      Promise<UpdateResult> promise = Promise.promise();
      String sql = String.format("DELETE FROM %s_%s.%s", tenantId, "mod_inventory_storage", tableName);
      postgresClient.execute(sql, promise);

      promise.future()
        .map(deleteResult -> cf.complete(deleteResult.getUpdated() >= 0))
        .otherwise(error -> cf.complete(false));

      return cf.get(5, TimeUnit.SECONDS);
    } catch (Exception e) {
      Assert.fail("WARNING!!!!! Unable to delete all: " + e.getMessage());
    }

    return false;
  }

  private static ResultSet getRecordsWithUnmatchedIds(String tenantId,
                                                      String tableName)
    throws InterruptedException, ExecutionException, TimeoutException {

    PostgresClient dbClient = PostgresClient.getInstance(
      getVertx(), tenantId);

    CompletableFuture<ResultSet> selectCompleted = new CompletableFuture<>();

    String sql = String.format("SELECT null FROM %s_%s.%s" +
        " WHERE CAST(id AS VARCHAR(50)) != jsonb->>'id'",
      tenantId, "mod_inventory_storage", tableName);

    dbClient.select(sql, result -> {
      if (result.succeeded()) {
        selectCompleted.complete(result.result());
      } else {
        selectCompleted.completeExceptionally(result.cause());
      }
    });

    return selectCompleted.get(5, TimeUnit.SECONDS);
  }

  private static void startVerticle(DeploymentOptions options)
    throws InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<String> deploymentComplete = new CompletableFuture<>();

    vertx.deployVerticle(RestVerticle.class.getName(), options, res -> {
      if (res.succeeded()) {
        deploymentComplete.complete(res.result());
      } else {
        deploymentComplete.completeExceptionally(res.cause());
      }
    });

    deploymentComplete.get(20, TimeUnit.SECONDS);
  }

  static void prepareTenant(String tenantId, String moduleFrom, String moduleTo, boolean loadSample)
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<Response> tenantPrepared = new CompletableFuture<>();

    HttpClient client = new HttpClient(vertx);

    JsonArray ar = new JsonArray();
    ar.add(new JsonObject().put("key", "loadReference").put("value", "true"));
    ar.add(new JsonObject().put("key", "loadSample").put("value", Boolean.toString(loadSample)));

    JsonObject jo = new JsonObject();
    jo.put("parameters", ar);
    if (moduleFrom != null) {
      jo.put("module_from", moduleFrom);
    }
    jo.put("module_to", moduleTo);

    client.post(storageUrl("/_/tenant"), jo, tenantId,
      ResponseHandler.any(tenantPrepared));

    Response response = tenantPrepared.get(60, TimeUnit.SECONDS);

    String failureMessage = String.format("Tenant init failed: %s: %s",
      response.getStatusCode(), response.getBody());

    assertThat(failureMessage,
      response.getStatusCode(), is(201));
  }

  static void prepareTenant(String tenantId, boolean loadSample)
      throws InterruptedException,
      ExecutionException,
      TimeoutException,
      MalformedURLException {
    prepareTenant(tenantId, null, "mod-inventory-storage-1.0.0", loadSample);
  }

  static void removeTenant(String tenantId)
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    CompletableFuture<Response> tenantDeleted = new CompletableFuture<>();

    HttpClient client = new HttpClient(vertx);

    client.delete(storageUrl("/_/tenant"), tenantId,
      ResponseHandler.any(tenantDeleted));

    Response response = tenantDeleted.get(20, TimeUnit.SECONDS);

    String failureMessage = String.format("Tenant cleanup failed: %s: %s",
      response.getStatusCode(), response.getBody());

    assertThat(failureMessage,
      response.getStatusCode(), is(204));

    // Prevent "aclcheck_error" "permission denied for schema"
    // when recreating the ROLE with the same name but a different role OID.
    PostgresClient.closeAllClients();
  }
}
