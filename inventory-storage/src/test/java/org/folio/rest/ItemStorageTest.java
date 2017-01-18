package org.folio.rest;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.rest.jaxrs.model.Item;
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

public class ItemStorageTest {

  private static HttpClient client = new HttpClient(StorageTestSuite.getVertx());

  @Before
  public void beforeEach()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    CompletableFuture<Response> deleteAllFinished = new CompletableFuture();

    URL deleteItemsUrl = itemStorageUrl();

    client.delete(deleteItemsUrl, StorageTestSuite.TENANT_ID,
      ResponseHandler.empty(deleteAllFinished));

    Response response = deleteAllFinished.get(5, TimeUnit.SECONDS);

    if(response.getStatusCode() != 204) {
      throw new UnknownError("Delete all items preparation failed");
    }
  }

  @Test
  public void canCreateItems()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    UUID id = UUID.randomUUID();
    UUID instanceId = UUID.randomUUID();

    JsonObject itemToCreate = new JsonObject();

    itemToCreate.put("id",id.toString());
    itemToCreate.put("instance_id", instanceId.toString());
    itemToCreate.put("title", "Real Analysis");
    itemToCreate.put("barcode", "271828");

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture();

    client.post(itemStorageUrl(), itemToCreate, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    JsonObject item = response.getBody();

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    assertThat(item.getString("id"), is(id.toString()));
    assertThat(item.getString("instance_id"), is(instanceId.toString()));
    assertThat(item.getString("title"), is("Real Analysis"));
    assertThat(item.getString("barcode"), is("271828"));
  }

  @Test
  public void canGetItemById()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    UUID id = UUID.randomUUID();
    UUID instanceId = UUID.randomUUID();

    JsonObject itemToCreate = new JsonObject();
    itemToCreate.put("id", id.toString());
    itemToCreate.put("instance_id", instanceId.toString());
    itemToCreate.put("title", "Refactoring");
    itemToCreate.put("barcode", "314159");

    createItem(itemToCreate);

    URL getItemUrl = itemStorageUrl(String.format("/%s", id));

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture();

    client.get(getItemUrl, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    JsonResponse response = getCompleted.get(5, TimeUnit.SECONDS);

    JsonObject item = response.getBody();

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    assertThat(item.getString("id"), is(id.toString()));
    assertThat(item.getString("instance_id"), is(instanceId.toString()));
    assertThat(item.getString("title"), is("Refactoring"));
    assertThat(item.getString("barcode"), is("314159"));
  }

  @Test
  public void canGetAllItems()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID firstItemId = UUID.randomUUID();
    UUID firstItemInstanceId = UUID.randomUUID();

    JsonObject firstItemToCreate = new JsonObject();
    firstItemToCreate.put("id", firstItemId.toString());
    firstItemToCreate.put("instance_id", firstItemInstanceId.toString());
    firstItemToCreate.put("title", "Refactoring");
    firstItemToCreate.put("barcode", "314159");

    createItem(firstItemToCreate);

    UUID secondItemId = UUID.randomUUID();
    UUID secondItemInstanceId = UUID.randomUUID();

    JsonObject secondItemToCreate = new JsonObject();
    secondItemToCreate.put("id", secondItemId.toString());
    secondItemToCreate.put("instance_id", secondItemInstanceId.toString());
    secondItemToCreate.put("title", "Real Analysis");
    secondItemToCreate.put("barcode", "271828");

    createItem(secondItemToCreate);

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture();

    client.get(itemStorageUrl(), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    JsonResponse response = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject body = response.getBody();

    JsonArray allItems = body.getJsonArray("items");

    assertThat(allItems.size(), is(2));
    assertThat(body.getInteger("total_records"), is(2));

    JsonObject firstItem = allItems.getJsonObject(0);
    JsonObject secondItem = allItems.getJsonObject(1);

    assertThat(firstItem.getString("id"), is(firstItemId.toString()));
    assertThat(firstItem.getString("instance_id"),
      is(firstItemInstanceId.toString()));

    assertThat(firstItem.getString("title"), is("Refactoring"));
    assertThat(firstItem.getString("barcode"), is("314159"));

    assertThat(secondItem.getString("id"), is(secondItemId.toString()));
    assertThat(secondItem.getString("instance_id"),
      is(secondItemInstanceId.toString()));

    assertThat(secondItem.getString("title"), is("Real Analysis"));
    assertThat(secondItem.getString("barcode"), is("271828"));
  }

  @Test
  public void canDeleteAllItems()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    JsonObject firstItemToCreate = new JsonObject();
    firstItemToCreate.put("id", UUID.randomUUID().toString());
    firstItemToCreate.put("instance_id", UUID.randomUUID().toString());
    firstItemToCreate.put("title", "Refactoring");
    firstItemToCreate.put("barcode", "314159");

    createItem(firstItemToCreate);

    JsonObject secondItemToCreate = new JsonObject();
    secondItemToCreate.put("id", UUID.randomUUID().toString());
    secondItemToCreate.put("instance_id", UUID.randomUUID().toString());
    secondItemToCreate.put("title", "Real Analysis");
    secondItemToCreate.put("barcode", "271828");

    createItem(secondItemToCreate);

    CompletableFuture<Response> deleteAllFinished = new CompletableFuture();

    client.delete(itemStorageUrl(), StorageTestSuite.TENANT_ID,
      ResponseHandler.empty(deleteAllFinished));

    Response deleteResponse = deleteAllFinished.get(5, TimeUnit.SECONDS);

    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture();

    client.get(itemStorageUrl(), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    JsonResponse response = getCompleted.get(5, TimeUnit.SECONDS);

    JsonObject responseBody = response.getBody();

    JsonArray allItems = responseBody.getJsonArray("items");

    assertThat(allItems.size(), is(0));
    assertThat(responseBody.getInteger("total_records"), is(0));
  }

  @Test
  public void tenantIsRequiredForCreatingNewItem()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    JsonObject item = new JsonObject();
    item.put("id", UUID.randomUUID().toString());
    item.put("title", "Refactoring");
    item.put("instance_id", UUID.randomUUID().toString());
    item.put("barcode", "4554345453");

    CompletableFuture<TextResponse> postCompleted = new CompletableFuture();

    client.post(itemStorageUrl(), item,
      ResponseHandler.text(postCompleted));

    TextResponse response = postCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), is("Tenant Must Be Provided"));
  }

  @Test
  public void tenantIsRequiredForGettingAnItem()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    URL getInstanceUrl = itemStorageUrl(String.format("/%s",
      UUID.randomUUID().toString()));

    CompletableFuture<TextResponse> getCompleted = new CompletableFuture();

    client.get(getInstanceUrl, ResponseHandler.text(getCompleted));

    TextResponse response = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), is("Tenant Must Be Provided"));
  }

  @Test
  public void tenantIsRequiredForGettingAllItems()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    CompletableFuture<TextResponse> getCompleted = new CompletableFuture();

    client.get(itemStorageUrl(), ResponseHandler.text(getCompleted));

    TextResponse response = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), is("Tenant Must Be Provided"));
  }

  private void createItem(JsonObject itemToCreate)
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    CompletableFuture<Response> createCompleted = new CompletableFuture();

    client.post(itemStorageUrl(), itemToCreate, StorageTestSuite.TENANT_ID,
      ResponseHandler.empty(createCompleted));

    Response response = createCompleted.get(2, TimeUnit.SECONDS);

    if(response.getStatusCode() != 201) {
      throw new UnknownError("Create item preparation failed");
    }
  }

  private static URL itemStorageUrl() throws MalformedURLException {
    return itemStorageUrl("");
  }

  private static URL itemStorageUrl(String subPath)
    throws MalformedURLException {

    return StorageTestSuite.storageUrl("/item-storage/items" + subPath);
  }
}
