package org.folio.rest;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.support.*;
import org.junit.Before;
import org.junit.Test;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

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

    URL deleteInstancesUrl = storageUrl();

    client.delete(deleteInstancesUrl, StorageTestSuite.TENANT_ID, response -> {
      if(response.statusCode() == 200) {
        deleteAllFinished.complete(null);
      }
    });

    deleteAllFinished.get(5, TimeUnit.SECONDS);
  }

  @Test
  public void canCreateInstances()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    UUID id = UUID.randomUUID();

    URL postInstanceUrl = storageUrl();

    JsonObject instanceToCreate = new JsonObject();

    instanceToCreate.put("id",id.toString());
    instanceToCreate.put("title", "Real Analysis");

    CompletableFuture<JsonResponse> postCompleted = new CompletableFuture();

    client.post(postInstanceUrl, instanceToCreate, StorageTestSuite.TENANT_ID,
      response -> {
        int statusCode = response.statusCode();

        response.bodyHandler(buffer -> {
          JsonObject body = BufferHelper.jsonObjectFromBuffer(buffer);

          postCompleted.complete(new JsonResponse(statusCode, body));
      });
    });

    JsonResponse response = postCompleted.get(5, TimeUnit.SECONDS);

    JsonObject instance = response.getBody();

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    assertThat(instance.getString("title"), is("Real Analysis"));
    assertThat(instance.getString("id"), is(id.toString()));
  }

  @Test
  public void canGetInstanceById()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    String id = UUID.randomUUID().toString();

    JsonObject instanceToCreate = new JsonObject();
    instanceToCreate.put("id", id);
    instanceToCreate.put("title", "Refactoring");

    createInstance(instanceToCreate);

    URL getInstanceUrl = storageUrl(String.format("/%s", id));

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture();

      client.get(getInstanceUrl, StorageTestSuite.TENANT_ID, response -> {
        int statusCode = response.statusCode();

        response.bodyHandler(buffer -> {
          JsonObject body = BufferHelper.jsonObjectFromBuffer(buffer);

          getCompleted.complete(new JsonResponse(statusCode, body));
        });
      });

    JsonResponse response = getCompleted.get(5, TimeUnit.SECONDS);

    JsonObject instance = response.getBody();

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(instance.getString("title"), is("Refactoring"));
    assertThat(instance.getString("id"), is(id.toString()));
  }

  @Test
  public void canGetAllInstances()
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

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture();

    client.get(storageUrl(), StorageTestSuite.TENANT_ID, response -> {
      int statusCode = response.statusCode();

      response.bodyHandler(buffer -> {
        JsonObject body = BufferHelper.jsonObjectFromBuffer(buffer);

        getCompleted.complete(new JsonResponse(statusCode, body));
      });
    });

    JsonResponse response = getCompleted.get(5, TimeUnit.SECONDS);

    JsonObject responseBody = response.getBody();

    JsonArray allInstances = responseBody.getJsonArray("instances");

    assertThat(allInstances.size(), is(2));
    assertThat(responseBody.getInteger("total_records"), is(2));

    JsonObject firstInstance = allInstances.getJsonObject(0);
    JsonObject secondInstance = allInstances.getJsonObject(1);

    assertThat(firstInstance.getString("title"), is("Refactoring"));
    assertThat(firstInstance.getString("id"), is(firstInstanceId.toString()));

    assertThat(secondInstance.getString("title"), is("Real Analysis"));
    assertThat(secondInstance.getString("id"), is(secondInstanceId.toString()));
  }

  @Test
  public void canDeleteAllInstances()
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

    CompletableFuture<Response> allDeleted = new CompletableFuture();

    client.delete(storageUrl(), StorageTestSuite.TENANT_ID, response -> {
      allDeleted.complete(new Response(response.statusCode()));
    });

    Response deleteResponse = allDeleted.get(5, TimeUnit.SECONDS);

    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture();

    client.get(storageUrl(), StorageTestSuite.TENANT_ID, response -> {
      int statusCode = response.statusCode();

      response.bodyHandler(buffer -> {
        JsonObject body = BufferHelper.jsonObjectFromBuffer(buffer);

        getCompleted.complete(new JsonResponse(statusCode, body));
      });
    });

    JsonResponse response = getCompleted.get(5, TimeUnit.SECONDS);

    JsonObject responseBody = response.getBody();

    JsonArray allInstances = responseBody.getJsonArray("instances");

    assertThat(allInstances.size(), is(0));
    assertThat(responseBody.getInteger("total_records"), is(0));
  }

  @Test
  public void tenantIsRequiredForCreatingNewInstance()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    Instance instance = new Instance();
    instance.setId(UUID.randomUUID().toString());
    instance.setTitle("Refactoring");

    CompletableFuture<StringResponse> postCompleted = new CompletableFuture();

    client.post(storageUrl(), instance, response -> {
      response.bodyHandler( buffer -> {
        int statusCode = response.statusCode();

        String body = BufferHelper.stringFromBuffer(buffer);

        postCompleted.complete(new StringResponse(statusCode, body));
      });
    });

    StringResponse response = postCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), is("Tenant Must Be Provided"));
  }

  @Test
  public void tenantIsRequiredForGettingAnInstance()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    URL getInstanceUrl = storageUrl(String.format("/%s",
      UUID.randomUUID().toString()));

    CompletableFuture<StringResponse> getCompleted = new CompletableFuture();

    client.get(getInstanceUrl, response -> {
      response.bodyHandler( buffer -> {
        int statusCode = response.statusCode();

        String body = BufferHelper.stringFromBuffer(buffer);

        getCompleted.complete(new StringResponse(statusCode, body));
      });
    });

    StringResponse response = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), is("Tenant Must Be Provided"));
  }

  @Test
  public void tenantIsRequiredForGettingAllInstances()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    CompletableFuture<StringResponse> getCompleted = new CompletableFuture();

    client.get(storageUrl(), response -> {
      response.bodyHandler( buffer -> {
        int statusCode = response.statusCode();

        String body = BufferHelper.stringFromBuffer(buffer);

        getCompleted.complete(new StringResponse(statusCode, body));
      });
    });

    StringResponse response = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), is("Tenant Must Be Provided"));
  }

  private void createInstance(JsonObject instanceToCreate)
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    CompletableFuture createComplete = new CompletableFuture();

    client.post(storageUrl(), instanceToCreate, StorageTestSuite.TENANT_ID,
      response -> {
        createComplete.complete(null);
    });

    createComplete.get(2, TimeUnit.SECONDS);
  }

  private URL storageUrl() throws MalformedURLException {
    return new URL("http", "localhost", port,
      "/instance-storage/instances");
  }

  private URL storageUrl(String subPath) throws MalformedURLException {
    return new URL("http", "localhost", port,
      "/instance-storage/instances" + subPath);
  }
}
