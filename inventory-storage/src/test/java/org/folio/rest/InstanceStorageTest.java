package org.folio.rest;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.rest.jaxrs.model.Instance;
import org.junit.Before;
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
public class InstanceStorageTest {

  private static Vertx vertx = StorageTestSuite.getVertx();
  private static int port = StorageTestSuite.getPort();

  private static HttpClient client = new HttpClient(vertx);

  @Before
  public void beforeEach()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    CompletableFuture deleteAllFinished = new CompletableFuture();

    URL deleteInstancesUrl = new URL("http", "localhost", port,
      "/instance-storage/instances");

    client.delete(deleteInstancesUrl.toString(), StorageTestSuite.TENANT_ID, response -> {
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

    URL postInstanceUrl = new URL("http", "localhost", port, "/instance-storage/instances");

    JsonObject instanceToCreate = new JsonObject();

    instanceToCreate.put("id",id.toString());
    instanceToCreate.put("title", "Real Analysis");

    client.post(postInstanceUrl, instanceToCreate, StorageTestSuite.TENANT_ID, response -> {
      int statusCode = response.statusCode();
      context.assertEquals(statusCode, HttpURLConnection.HTTP_CREATED);

      response.bodyHandler(buffer -> {
        JsonObject instance = BufferHelper.jsonObjectFromBuffer(buffer);

        context.assertEquals(instance.getString("title"), "Real Analysis");
        context.assertEquals(instance.getString("id"), id.toString());

        async.complete();
      });
    });
  }

  @Test
  public void canGetInstanceById(TestContext context)
    throws MalformedURLException {

    Async async = context.async();
    String id = UUID.randomUUID().toString();

    URL postInstanceUrl = new URL("http", "localhost", port, "/instance-storage/instances");

    JsonObject instanceToCreate = new JsonObject();
    instanceToCreate.put("id", id);
    instanceToCreate.put("title", "Refactoring");

    client.post(postInstanceUrl, instanceToCreate, StorageTestSuite.TENANT_ID, postResponse -> {
      final int postStatusCode = postResponse.statusCode();
      context.assertEquals(postStatusCode, HttpURLConnection.HTTP_CREATED);

      String urlForGet =
        String.format("http://localhost:%s/instance-storage/instances/%s",
          port, id);

      client.get(urlForGet, StorageTestSuite.TENANT_ID, getResponse -> {
        final int getStatusCode = getResponse.statusCode();

        getResponse.bodyHandler(getBuffer -> {
          JsonObject restResponse1 =
            BufferHelper.jsonObjectFromBuffer(getBuffer);

          context.assertEquals(getStatusCode, HttpURLConnection.HTTP_OK);
          context.assertEquals(restResponse1.getString("id") , id);

          async.complete();
        });
      });
    });
  }

  @Test
  public void canGetAllInstances(TestContext context)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID firstInstanceId = UUID.randomUUID();

    JsonObject firstInstanceToCreate = new JsonObject();
    firstInstanceToCreate.put("id", firstInstanceId.toString());
    firstInstanceToCreate.put("title", "Refactoring");

    createInstance(firstInstanceToCreate);

    UUID secondInstanceId = UUID.randomUUID();

    JsonObject secondInstanceToCreate = new JsonObject();
    secondInstanceToCreate.put("id", secondInstanceId.toString());
    secondInstanceToCreate.put("title", "Real Analysis");

    createInstance(secondInstanceToCreate);

    URL instancesUrl = new URL("http", "localhost", port, "/instance-storage/instances");

    Async async = context.async();

    client.get(instancesUrl.toString(), StorageTestSuite.TENANT_ID,
      getResponse -> {
        if(getResponse.statusCode() == HttpURLConnection.HTTP_INTERNAL_ERROR)
        {
          getResponse.bodyHandler(body -> {
            System.out.println("Response Message: " + BufferHelper.stringFromBuffer(body));
          });
        }

        context.assertEquals(getResponse.statusCode(),
          HttpURLConnection.HTTP_OK);

        getResponse.bodyHandler(body -> {
          JsonObject getAllResponse = BufferHelper.jsonObjectFromBuffer(body);

          JsonArray allInstances = getAllResponse.getJsonArray("instances");

          context.assertEquals(allInstances.size(), 2);
          context.assertEquals(getAllResponse.getInteger("total_records"), 2);

          JsonObject firstInstance = allInstances.getJsonObject(0);
          JsonObject secondInstance = allInstances.getJsonObject(1);

          context.assertEquals(firstInstance.getString("title"), "Refactoring");
          context.assertEquals(firstInstance.getString("id"), firstInstanceId.toString());

          context.assertEquals(secondInstance.getString("title"), "Real Analysis");
          context.assertEquals(secondInstance.getString("id"), secondInstanceId.toString());

          async.complete();
        });
      });
  }

  @Test
  public void canDeleteAllInstances(TestContext context)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    JsonObject firstInstanceToCreate = new JsonObject();
    firstInstanceToCreate.put("id", UUID.randomUUID().toString());
    firstInstanceToCreate.put("title", "Refactoring");

    createInstance(firstInstanceToCreate);

    JsonObject secondInstanceToCreate = new JsonObject();
    secondInstanceToCreate.put("id", UUID.randomUUID().toString());
    secondInstanceToCreate.put("title", "Real Analysis");

    createInstance(secondInstanceToCreate);

    CompletableFuture deleteAllFinished = new CompletableFuture();

    URL instancesUrl = new URL("http", "localhost", port, "/instance-storage/instances");

    client.delete(instancesUrl.toString(), StorageTestSuite.TENANT_ID, response -> {
      context.assertEquals(response.statusCode(), HttpURLConnection.HTTP_OK);

      deleteAllFinished.complete(null);
    });

    deleteAllFinished.get(5, TimeUnit.SECONDS);

    Async async = context.async();

    client.get(instancesUrl.toString(), StorageTestSuite.TENANT_ID, response -> {
      response.bodyHandler(body -> {

        JsonObject getAllResponse = BufferHelper.jsonObjectFromBuffer(body);

        JsonArray allInstances = getAllResponse.getJsonArray("instances");

        context.assertEquals(allInstances.size(), 0);
        context.assertEquals(getAllResponse.getInteger("total_records"), 0);
        async.complete();
      });
    });
  }

  @Test
  public void tenantIsRequiredForCreatingNewInstance(TestContext context)
    throws MalformedURLException {

    Async async = context.async();

    Instance instance = new Instance();
    instance.setId(UUID.randomUUID().toString());
    instance.setTitle("Refactoring");

    client.post(new URL("http", "localhost",  port, "/instance-storage/instances"),
      instance, response -> {
        context.assertEquals(response.statusCode(), 400);

        response.bodyHandler( buffer -> {
          String responseBody = BufferHelper.stringFromBuffer(buffer);

          context.assertEquals(responseBody, "Tenant Must Be Provided");

          async.complete();
        });
      });
  }

  @Test
  public void tenantIsRequiredForGettingAnInstance(TestContext context)
    throws MalformedURLException {

    Async async = context.async();

    String path = String.format("/instance-storage/instances/%s",
      UUID.randomUUID().toString());

    URL getInstanceUrl = new URL("http", "localhost", port, path);

    client.get(getInstanceUrl,
      response -> {
        context.assertEquals(response.statusCode(), 400);

        response.bodyHandler( buffer -> {
          String responseBody = BufferHelper.stringFromBuffer(buffer);

          context.assertEquals(responseBody, "Tenant Must Be Provided");

          async.complete();
        });
      });
  }

  @Test
  public void tenantIsRequiredForGettingAllInstances(TestContext context)
    throws MalformedURLException {

    Async async = context.async();

    String path = String.format("/instance-storage/instances");

    URL getInstancesUrl = new URL("http", "localhost", port, path);

    client.get(getInstancesUrl,
      response -> {
        System.out.println("Response received");

        context.assertEquals(response.statusCode(), 400);

        response.bodyHandler( buffer -> {
          String responseBody = BufferHelper.stringFromBuffer(buffer);

          context.assertEquals(responseBody, "Tenant Must Be Provided");

          async.complete();
        });
      });
  }

  private void createInstance(JsonObject instanceToCreate)
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    URL postInstanceUrl = new URL("http", "localhost", port, "/instance-storage/instances");

    CompletableFuture createComplete = new CompletableFuture();

    client.post(postInstanceUrl, instanceToCreate, StorageTestSuite.TENANT_ID, response -> {
      createComplete.complete(null);
    });

    createComplete.get(2, TimeUnit.SECONDS);
  }

}
