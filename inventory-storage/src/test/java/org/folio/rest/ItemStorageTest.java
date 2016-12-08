package org.folio.rest;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RunWith(VertxUnitRunner.class)
public class ItemStorageTest {

  private static Vertx vertx;
  private static int port;

  private static final String TENANT_ID = "test_tenant";
  private static final String TENANT_HEADER = "X-Okapi-Tenant";

  @BeforeClass
  public static void before(TestContext context)
    throws InterruptedException, ExecutionException, TimeoutException {

    vertx = Vertx.vertx();

    port = NetworkUtils.nextFreePort();

    DeploymentOptions options = new DeploymentOptions();

    options.setConfig(new JsonObject().put("http.port", port));
    options.setWorker(true);

    startVerticle(options);
  }

  private static void startVerticle(DeploymentOptions options)
    throws InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture deploymentComplete = new CompletableFuture<String>();

    vertx.deployVerticle(RestVerticle.class.getName(), options, res -> {
      if(res.succeeded()) {
        deploymentComplete.complete(res.result());
      }
      else {
        deploymentComplete.completeExceptionally(res.cause());
      }
    });

    deploymentComplete.get(20, TimeUnit.SECONDS);
  }

  @AfterClass
  public static void after()
    throws InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture undeploymentComplete = new CompletableFuture<String>();

    vertx.close(res -> {
      if(res.succeeded()) {
        undeploymentComplete.complete(null);
      }
      else {
        undeploymentComplete.completeExceptionally(res.cause());
      }
    });

    undeploymentComplete.get(20, TimeUnit.SECONDS);
  }

  @Before
  public void beforeEach()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    CompletableFuture deleteAllFinished = new CompletableFuture();

    URL deleteItemsUrl = new URL("http", "localhost", port, "/item-storage/items");

    Delete(vertx, deleteItemsUrl.toString(), TENANT_ID, response -> {
      if(response.statusCode() == 200) {
        deleteAllFinished.complete(null);
      }
    });

    deleteAllFinished.get(5, TimeUnit.SECONDS);
  }

  @Test
  public void createViaPostRespondsWithCorrectResource(TestContext context)
    throws MalformedURLException {

    Async async = context.async();
    UUID id = UUID.randomUUID();
    UUID instanceId = UUID.randomUUID();

    URL postItemUrl = new URL("http", "localhost", port, "/item-storage/items");

    JsonObject itemToCreate = new JsonObject();

    itemToCreate.put("id",id.toString());
    itemToCreate.put("instance_id", instanceId.toString());
    itemToCreate.put("title", "Real Analysis");
    itemToCreate.put("barcode", "271828");

    Post(vertx, postItemUrl, itemToCreate, TENANT_ID, response -> {
      int statusCode = response.statusCode();
      context.assertEquals(statusCode, HttpURLConnection.HTTP_CREATED);

      response.bodyHandler(buffer -> {
        JsonObject item = jsonObjectFromBuffer(buffer);

        context.assertEquals(item.getString("title"), "Real Analysis");
        context.assertEquals(item.getString("barcode"), "271828");
        context.assertEquals(item.getString("instance_id"), instanceId.toString());
        context.assertEquals(item.getString("id"), id.toString());

        async.complete();
      });
    });
  }

  @Test
  public void canGetItemById(TestContext context)
    throws MalformedURLException {

    Async async = context.async();
    String id = UUID.randomUUID().toString();

    URL postItemUrl = new URL("http", "localhost", port, "/item-storage/items");

    JsonObject itemToCreate = new JsonObject();
    itemToCreate.put("id", id);
    itemToCreate.put("instance_id", "MadeUp");
    itemToCreate.put("title", "Refactoring");
    itemToCreate.put("barcode", "314159");

    Post(vertx, postItemUrl, itemToCreate, TENANT_ID, postResponse -> {
      final int postStatusCode = postResponse.statusCode();
        context.assertEquals(postStatusCode, HttpURLConnection.HTTP_CREATED);

        String urlForGet =
          String.format("http://localhost:%s/item-storage/items/%s",
            port, id);

        Get(vertx, urlForGet, TENANT_ID, getResponse -> {
          final int getStatusCode = getResponse.statusCode();

          getResponse.bodyHandler(getBuffer -> {
            JsonObject restResponse1 =
              jsonObjectFromBuffer(getBuffer);

            context.assertEquals(getStatusCode, HttpURLConnection.HTTP_OK);
            context.assertEquals(restResponse1.getString("id") , id);

            async.complete();
          });
        });
      });
  }

  @Test
  public void canGetAllItems(TestContext context)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID firstItemId = UUID.randomUUID();

    JsonObject firstItemToCreate = new JsonObject();
    firstItemToCreate.put("id", firstItemId.toString());
    firstItemToCreate.put("instance_id", "MadeUp");
    firstItemToCreate.put("title", "Refactoring");
    firstItemToCreate.put("barcode", "314159");

    createItem(firstItemToCreate);

    UUID secondItemId = UUID.randomUUID();

    JsonObject secondItemToCreate = new JsonObject();
    secondItemToCreate.put("id", secondItemId.toString());
    secondItemToCreate.put("instance_id", "MadeUp");
    secondItemToCreate.put("title", "Real Analysis");
    secondItemToCreate.put("barcode", "271828");

    createItem(secondItemToCreate);

    URL itemsUrl = new URL("http", "localhost", port, "/item-storage/items");

    Async async = context.async();

    Get(vertx, itemsUrl.toString(), TENANT_ID,
      getResponse -> {
        context.assertEquals(getResponse.statusCode(),
          HttpURLConnection.HTTP_OK);

        getResponse.bodyHandler(body -> {
          JsonObject getAllResponse = jsonObjectFromBuffer(body);

          JsonArray allItems = getAllResponse.getJsonArray("items");

          context.assertEquals(allItems.size(), 2);
          context.assertEquals(getAllResponse.getInteger("total_records"), 2);

          JsonObject firstItem = allItems.getJsonObject(0);
          JsonObject secondItem = allItems.getJsonObject(1);

          context.assertEquals(firstItem.getString("title"), "Refactoring");
          context.assertEquals(firstItem.getString("id"), firstItemId.toString());

          context.assertEquals(secondItem.getString("title"), "Real Analysis");
          context.assertEquals(secondItem.getString("id"), secondItemId.toString());

          async.complete();
        });
      });
  }

  @Test
  public void canDeleteAllItems(TestContext context)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    JsonObject firstItemToCreate = new JsonObject();
    firstItemToCreate.put("id", UUID.randomUUID().toString());
    firstItemToCreate.put("instance_id", "MadeUp");
    firstItemToCreate.put("title", "Refactoring");
    firstItemToCreate.put("barcode", "314159");

    createItem(firstItemToCreate);

    JsonObject secondItemToCreate = new JsonObject();
    secondItemToCreate.put("id", UUID.randomUUID().toString());
    secondItemToCreate.put("instance_id", "MadeUp");
    secondItemToCreate.put("title", "Real Analysis");
    secondItemToCreate.put("barcode", "271828");

    createItem(secondItemToCreate);

    CompletableFuture deleteAllFinished = new CompletableFuture();

    URL itemsUrl = new URL("http", "localhost", port, "/item-storage/items");

    Delete(vertx, itemsUrl.toString(), TENANT_ID, response -> {
      context.assertEquals(response.statusCode(), HttpURLConnection.HTTP_OK);

      deleteAllFinished.complete(null);
    });

    deleteAllFinished.get(5, TimeUnit.SECONDS);

    Async async = context.async();

    Get(vertx, itemsUrl.toString(), TENANT_ID, response -> {
      response.bodyHandler(body -> {

        JsonObject getAllResponse = jsonObjectFromBuffer(body);

        JsonArray allItems = getAllResponse.getJsonArray("items");

        context.assertEquals(allItems.size(), 0);
        context.assertEquals(getAllResponse.getInteger("total_records"), 0);
        async.complete();
      });
    });
  }

  @Test
  public void tenantIsRequiredForCreatingNewItem(TestContext context)
    throws MalformedURLException {

    Async async = context.async();

    Item item = new Item();
    item.setId(UUID.randomUUID().toString());
    item.setTitle("Refactoring");
    item.setInstanceId(UUID.randomUUID().toString());
    item.setBarcode("4554345453");

    Post(vertx, new URL("http", "localhost",  port, "/item-storage/items"),
      item, response -> {
        context.assertEquals(response.statusCode(), 400);

        response.bodyHandler( buffer -> {
          String responseBody = stringFromBuffer(buffer);

          context.assertEquals(responseBody, "Tenant Must Be Provided");

          async.complete();
        });
      });
  }

  @Test
  public void tenantIsRequiredForGettingAnItem(TestContext context)
    throws MalformedURLException {

    Async async = context.async();

    String path = String.format("/item-storage/items/%s",
      UUID.randomUUID().toString());

    URL getItemUrl = new URL("http", "localhost", port, path);

    Get(vertx, getItemUrl,
      response -> {
        context.assertEquals(response.statusCode(), 400);

        response.bodyHandler( buffer -> {
          String responseBody = stringFromBuffer(buffer);

          context.assertEquals(responseBody, "Tenant Must Be Provided");

          async.complete();
        });
      });
  }

  @Test
  public void tenantIsRequiredForGettingAllItems(TestContext context)
    throws MalformedURLException {

    Async async = context.async();

    String path = String.format("/item-storage/items");

    URL getItemsUrl = new URL("http", "localhost", port, path);

    Get(vertx, getItemsUrl,
      response -> {
        System.out.println("Response received");

        context.assertEquals(response.statusCode(), 400);

        response.bodyHandler( buffer -> {
          String responseBody = stringFromBuffer(buffer);

          context.assertEquals(responseBody, "Tenant Must Be Provided");

          async.complete();
        });
      });
  }

  private void createItem(JsonObject itemToCreate)
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    URL postItemUrl = new URL("http", "localhost", port, "/item-storage/items");

    CompletableFuture createComplete = new CompletableFuture();

    Post(vertx, postItemUrl, itemToCreate, TENANT_ID, response -> {
      createComplete.complete(null);
    });

    createComplete.get(2, TimeUnit.SECONDS);
  }

  private void Post(Vertx vertx,
                   URL url,
                   Object body,
                   Handler<HttpClientResponse> responseHandler) {

      Post(vertx, url, body, null, responseHandler);
  }

  private void Post(Vertx vertx,
                    URL url,
                    Object body,
                    String tenantId,
                    Handler<HttpClientResponse> responseHandler) {

    HttpClient client = vertx.createHttpClient();

    HttpClientRequest request = client.postAbs(url.toString(), responseHandler);

    request.headers().add("Accept","application/json");
    request.headers().add("Content-type","application/json");

    if(tenantId != null) {
      request.headers().add(TENANT_HEADER, tenantId);
    }

    request.end(Json.encodePrettily(body));
  }

  private void Get(Vertx vertx,
                   URL url,
                   Handler<HttpClientResponse> responseHandler) {

    Get(vertx, url, null, responseHandler);
  }

  private void Get(Vertx vertx,
                   URL url,
                   String tenantId,
                   Handler<HttpClientResponse> responseHandler) {

    Get(vertx, url.toString(), tenantId, responseHandler);
  }

  private void Get(Vertx vertx,
                   String url,
                   String tenantId,
                   Handler<HttpClientResponse> responseHandler) {

    HttpClient client = vertx.createHttpClient();

    HttpClientRequest request = client.getAbs(url, responseHandler);

    request.headers().add("Accept","application/json");

    if(tenantId != null) {
      request.headers().add(TENANT_HEADER, tenantId);
    }

    request.end();
  }

  private void Delete(Vertx vertx,
                   String url,
                   String tenantId,
                   Handler<HttpClientResponse> responseHandler) {

    HttpClient client = vertx.createHttpClient();

    HttpClientRequest request = client.deleteAbs(url, responseHandler);

    request.headers().add("Accept","application/json");

    if(tenantId != null) {
      request.headers().add(TENANT_HEADER, tenantId);
    }

    request.end();
  }

  private JsonObject jsonObjectFromBuffer(Buffer buffer) {
    return new JsonObject(stringFromBuffer(buffer));
  }

  private String stringFromBuffer(Buffer buffer) {
    return buffer.getString(0, buffer.length());
  }
}
