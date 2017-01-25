package org.folio.rest;

import io.vertx.core.json.Json;
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

    JsonObject itemToCreate = nod(id, instanceId);

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture();

    client.post(itemStorageUrl(), itemToCreate, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    JsonObject item = response.getBody();

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    assertThat(item.getString("id"), is(id.toString()));
    assertThat(item.getString("instanceId"), is(instanceId.toString()));
    assertThat(item.getString("title"), is("Nod"));
    assertThat(item.getString("barcode"), is("565578437802"));
  }

  @Test
  public void canGetItemById()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    UUID id = UUID.randomUUID();
    UUID instanceId = UUID.randomUUID();

    createItem(smallAngryPlanet(id, instanceId));

    URL getItemUrl = itemStorageUrl(String.format("/%s", id));

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture();

    client.get(getItemUrl, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    JsonResponse response = getCompleted.get(5, TimeUnit.SECONDS);

    JsonObject item = response.getBody();

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    assertThat(item.getString("id"), is(id.toString()));
    assertThat(item.getString("instanceId"), is(instanceId.toString()));
    assertThat(item.getString("title"), is("Long Way to a Small Angry Planet"));
    assertThat(item.getString("barcode"), is("036000291452"));
  }

  @Test
  public void canGetAllItems()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID firstItemId = UUID.randomUUID();
    UUID firstItemInstanceId = UUID.randomUUID();

    UUID secondItemId = UUID.randomUUID();
    UUID secondItemInstanceId = UUID.randomUUID();

    createItem(smallAngryPlanet(firstItemId, firstItemInstanceId));
    createItem(nod(secondItemId, secondItemInstanceId));

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture();

    client.get(itemStorageUrl(), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    JsonResponse response = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject body = response.getBody();

    JsonArray allItems = body.getJsonArray("items");

    assertThat(allItems.size(), is(2));
    assertThat(body.getInteger("totalRecords"), is(2));

    JsonObject firstItem = allItems.getJsonObject(0);
    JsonObject secondItem = allItems.getJsonObject(1);

    assertThat(firstItem.getString("id"), is(firstItemId.toString()));
    assertThat(firstItem.getString("instanceId"),
      is(firstItemInstanceId.toString()));

    assertThat(firstItem.getString("title"), is("Long Way to a Small Angry Planet"));
    assertThat(firstItem.getString("barcode"), is("036000291452"));

    assertThat(secondItem.getString("id"), is(secondItemId.toString()));
    assertThat(secondItem.getString("instanceId"),
      is(secondItemInstanceId.toString()));

    assertThat(secondItem.getString("title"), is("Nod"));
    assertThat(secondItem.getString("barcode"), is("565578437802"));
  }

  @Test
  public void canPageAllItems()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    createItem(smallAngryPlanet());
    createItem(nod());
    createItem(uprooted());
    createItem(temeraire());
    createItem(interestingTimes());

    CompletableFuture<JsonResponse> firstPageCompleted = new CompletableFuture();
    CompletableFuture<JsonResponse> secondPageCompleted = new CompletableFuture();

    client.get(itemStorageUrl() + "?limit=3", StorageTestSuite.TENANT_ID,
      ResponseHandler.json(firstPageCompleted));

    client.get(itemStorageUrl() + "?limit=3&offset=3", StorageTestSuite.TENANT_ID,
      ResponseHandler.json(secondPageCompleted));

    JsonResponse firstPageResponse = firstPageCompleted.get(5, TimeUnit.SECONDS);
    JsonResponse secondPageResponse = secondPageCompleted.get(5, TimeUnit.SECONDS);

    assertThat(firstPageResponse.getStatusCode(), is(200));
    assertThat(secondPageResponse.getStatusCode(), is(200));

    JsonObject firstPage = firstPageResponse.getBody();
    JsonObject secondPage = secondPageResponse.getBody();

    JsonArray firstPageItems = firstPage.getJsonArray("items");
    JsonArray secondPageItems = secondPage.getJsonArray("items");

    assertThat(firstPageItems.size(), is(3));
    assertThat(firstPage.getInteger("totalRecords"), is(5));

    assertThat(secondPageItems.size(), is(2));
    assertThat(secondPage.getInteger("totalRecords"), is(5));
  }

  @Test
  public void canSearchForItemsByTitle()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    createItem(smallAngryPlanet());
    createItem(nod());
    createItem(uprooted());
    createItem(temeraire());
    createItem(interestingTimes());

    CompletableFuture<JsonResponse> searchCompleted = new CompletableFuture();

    String url = itemStorageUrl() + "?query=title=\"*Up*\"";

    client.get(url,
      StorageTestSuite.TENANT_ID, ResponseHandler.json(searchCompleted));

    JsonResponse searchResponse = searchCompleted.get(5, TimeUnit.SECONDS);

    assertThat(searchResponse.getStatusCode(), is(200));

    JsonObject searchBody = searchResponse.getBody();

    JsonArray foundItems = searchBody.getJsonArray("items");

    assertThat(foundItems.size(), is(1));
    assertThat(searchBody.getInteger("totalRecords"), is(1));

    assertThat(foundItems.getJsonObject(0).getString("title"),
      is("Uprooted"));
  }

  @Test
  public void canDeleteAllItems()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    createItem(smallAngryPlanet());
    createItem(nod());
    createItem(uprooted());
    createItem(temeraire());
    createItem(interestingTimes());

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
    assertThat(responseBody.getInteger("totalRecords"), is(0));
  }

  @Test
  public void tenantIsRequiredForCreatingNewItem()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    CompletableFuture<TextResponse> postCompleted = new CompletableFuture();

    client.post(itemStorageUrl(), smallAngryPlanet(),
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

  private JsonObject createItemRequest(
    UUID id,
    UUID instanceId,
    String title,
    String barcode) {

    JsonObject itemToCreate = new JsonObject();

    itemToCreate.put("id", id.toString());
    itemToCreate.put("instanceId", instanceId.toString());
    itemToCreate.put("title", title);
    itemToCreate.put("barcode", barcode);

    return itemToCreate;
  }

  private JsonObject smallAngryPlanet(UUID itemId, UUID instanceId) {
    return createItemRequest(itemId, instanceId,
      "Long Way to a Small Angry Planet", "036000291452");
  }

  private JsonObject smallAngryPlanet() {
    return smallAngryPlanet(UUID.randomUUID(), UUID.randomUUID());
  }

  private JsonObject nod(UUID itemId, UUID instanceId) {
    return createItemRequest(itemId, instanceId,
      "Nod", "565578437802");
  }

  private JsonObject nod() {
    return nod(UUID.randomUUID(), UUID.randomUUID());
  }

  private JsonObject uprooted() {
    return createItemRequest(UUID.randomUUID(), UUID.randomUUID(),
      "Uprooted", "657670342075");
  }

  private JsonObject temeraire() {
    return createItemRequest(UUID.randomUUID(), UUID.randomUUID(),
      "Temeraire", "232142443432");
  }

  private JsonObject interestingTimes() {
    return createItemRequest(UUID.randomUUID(), UUID.randomUUID(),
      "Interesting Times", "56454543534");
  }
}
