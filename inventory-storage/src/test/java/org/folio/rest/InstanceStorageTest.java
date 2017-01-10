package org.folio.rest;

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

  private static HttpClient client = new HttpClient(StorageTestSuite.getVertx());

  @Before
  public void beforeEach()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    CompletableFuture<Response> deleteAllFinished = new CompletableFuture();

    URL deleteInstancesUrl = instanceStorageUrl();

    client.delete(deleteInstancesUrl, StorageTestSuite.TENANT_ID,
      ResponseHandler.empty(deleteAllFinished));

    Response response = deleteAllFinished.get(5, TimeUnit.SECONDS);

    if(response.getStatusCode() != 200) {
      throw new UnknownError("Delete all instances preparation failed");
    }
  }

  @Test
  public void canCreateInstances()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    UUID id = UUID.randomUUID();

    URL postInstanceUrl = instanceStorageUrl();

    JsonObject instanceToCreate = new JsonObject();

    instanceToCreate.put("id",id.toString());
    instanceToCreate.put("title", "Real Analysis");

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture();

    client.post(postInstanceUrl, instanceToCreate, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

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

    URL getInstanceUrl = instanceStorageUrl(String.format("/%s", id));

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture();

    client.get(getInstanceUrl, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

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

    client.get(instanceStorageUrl(), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

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

    client.delete(instanceStorageUrl(), StorageTestSuite.TENANT_ID,
      ResponseHandler.empty(allDeleted));

    Response deleteResponse = allDeleted.get(5, TimeUnit.SECONDS);

    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture();

    client.get(instanceStorageUrl(), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

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

    CompletableFuture<TextResponse> postCompleted = new CompletableFuture();

    client.post(instanceStorageUrl(), instance,
      ResponseHandler.text(postCompleted));

    TextResponse response = postCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), is("Tenant Must Be Provided"));
  }

  @Test
  public void tenantIsRequiredForGettingAnInstance()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    URL getInstanceUrl = instanceStorageUrl(String.format("/%s",
      UUID.randomUUID().toString()));

    CompletableFuture<TextResponse> getCompleted = new CompletableFuture();

    client.get(getInstanceUrl, ResponseHandler.text(getCompleted));

    TextResponse response = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), is("Tenant Must Be Provided"));
  }

  @Test
  public void tenantIsRequiredForGettingAllInstances()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    CompletableFuture<TextResponse> getCompleted = new CompletableFuture();

    client.get(instanceStorageUrl(), ResponseHandler.text(getCompleted));

    TextResponse response = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), is("Tenant Must Be Provided"));
  }

  private void createInstance(JsonObject instanceToCreate)
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    CompletableFuture<Response> createCompleted = new CompletableFuture();

    client.post(instanceStorageUrl(), instanceToCreate,
      StorageTestSuite.TENANT_ID, ResponseHandler.empty(createCompleted));

    Response response = createCompleted.get(2, TimeUnit.SECONDS);

    if(response.getStatusCode() != 201) {
      throw new UnknownError("Create instance preparation failed");
    }
  }

  private static URL instanceStorageUrl() throws MalformedURLException {
    return instanceStorageUrl("");
  }

  private static URL instanceStorageUrl(String subPath)
    throws MalformedURLException {

    return StorageTestSuite.storageUrl("/instance-storage/instances" + subPath);
  }
}
