package org.folio.rest;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.ResponseHandler;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;

public class MaterialTypeTest {

  private static final String       SUPPORTED_CONTENT_TYPE_JSON_DEF = "application/json";
  private static final String       SUPPORTED_CONTENT_TYPE_TEXT_DEF = "text/plain";

  private static final String       ITEM_URL = "/item-storage/items";
  private static final String       MATERIAL_TYPE_URL = "/material-type/";

  private static String postRequest = "{\"name\": \"journal\"}";
  private static String putRequest = "{\"name\": \"Book\"}";
  //private static JsonObject createItemRequest = createItem();
  private static Vertx vertx;
  private static int port;

  @Before
  public void beforeEach()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

  }

  @After
  public void checkIdsAfterEach()
    throws InterruptedException, ExecutionException, TimeoutException {

  }

  @Test
  public void kickoff(){
    try {
      String url = StorageTestSuite.storageUrl("").toString();// "http://localhost:"+port;

      /**add a material type*/
      CompletableFuture<JsonResponse> createMT = new CompletableFuture();
      String createMTURL = url+MATERIAL_TYPE_URL;
      send(createMTURL, HttpMethod.POST, postRequest,
        SUPPORTED_CONTENT_TYPE_JSON_DEF, 201, ResponseHandler.json(createMT));
      JsonResponse createMTURLResponse = createMT.get(5, TimeUnit.SECONDS);
      assertThat(createMTURLResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
      //fix to read from location header
      String materialTypeID = createMTURLResponse.getJson().getString("id");
      System.out.println(createMTURLResponse.getBody() +
        "\nStatus - " + createMTURLResponse.getStatusCode() + " at " + System.currentTimeMillis() + " for " + createMTURL);

      /**add a duplicate material type*/
      CompletableFuture<JsonResponse> createMT2 = new CompletableFuture();
      String createMTURL2 = url+MATERIAL_TYPE_URL;
      send(createMTURL2, HttpMethod.POST, postRequest,
        SUPPORTED_CONTENT_TYPE_JSON_DEF, 201, ResponseHandler.json(createMT2));
      JsonResponse createMTURLResponse2 = createMT2.get(5, TimeUnit.SECONDS);
      assertThat(createMTURLResponse2.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
      System.out.println(createMTURLResponse2.getBody() +
        "\nStatus - " + createMTURLResponse2.getStatusCode() + " at " + System.currentTimeMillis() + " for " + createMTURL2);

      /**update the material type*/
      CompletableFuture<JsonResponse> updateMT = new CompletableFuture();
      String updateMTURL = url+MATERIAL_TYPE_URL+materialTypeID;
      send(updateMTURL, HttpMethod.PUT, putRequest,
        SUPPORTED_CONTENT_TYPE_JSON_DEF, 204, ResponseHandler.json(updateMT));
      JsonResponse updateMTURLResponse = updateMT.get(5, TimeUnit.SECONDS);
      assertThat(updateMTURLResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
      System.out.println(updateMTURLResponse.getBody() +
        "\nStatus - " + updateMTURLResponse.getStatusCode() + " at " + System.currentTimeMillis() + " for " + updateMTURL);

      /**get specific mt in mt table*/
      CompletableFuture<JsonResponse> getSpecificMT = new CompletableFuture();
      String getSpecificMTUrl = url+MATERIAL_TYPE_URL + materialTypeID;
      send(getSpecificMTUrl, HttpMethod.GET, null, SUPPORTED_CONTENT_TYPE_JSON_DEF, 200, ResponseHandler.json(getSpecificMT));
      JsonResponse getSpecificMTURLResponse = getSpecificMT.get(5, TimeUnit.SECONDS);
      assertThat(getSpecificMTURLResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
      System.out.println(getSpecificMTURLResponse.getBody() +
        "\nStatus - " + getSpecificMTURLResponse.getStatusCode() + " at " + System.currentTimeMillis() + " for " + getSpecificMTUrl);

      /**get bad id mt in mt table*/
      CompletableFuture<JsonResponse> getSpecificBadMT = new CompletableFuture();
      String getSpecificBadMTUrl = url+MATERIAL_TYPE_URL + "12345";
      send(getSpecificBadMTUrl, HttpMethod.GET, null, SUPPORTED_CONTENT_TYPE_JSON_DEF, 200, ResponseHandler.json(getSpecificBadMT));
      JsonResponse getSpecificBadMTURLResponse = getSpecificBadMT.get(5, TimeUnit.SECONDS);
      assertThat(getSpecificBadMTURLResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
      System.out.println(getSpecificBadMTURLResponse.getBody() +
        "\nStatus - " + getSpecificBadMTURLResponse.getStatusCode() + " at " + System.currentTimeMillis() + " for " + getSpecificBadMTUrl);

      /** add an item */
      CompletableFuture<JsonResponse> addItem = new CompletableFuture();
      String addItemURL = url+ITEM_URL;
      send(addItemURL, HttpMethod.POST, createItem().encode(),
        SUPPORTED_CONTENT_TYPE_JSON_DEF, 201, ResponseHandler.json(addItem));
      JsonResponse addItemURLResponse = addItem.get(5, TimeUnit.SECONDS);
      assertThat(addItemURLResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
      System.out.println(addItemURLResponse.getBody() +
        "\nStatus - " + addItemURLResponse.getStatusCode() + " at " + System.currentTimeMillis() + " for " + addItemURL);
      String itemID = addItemURLResponse.getJson().getString("id");

     /**get all mt in mt table*/
      CompletableFuture<JsonResponse> getAllMT = new CompletableFuture();
      String getAllMTUrl = url+MATERIAL_TYPE_URL;
      send(getAllMTUrl, HttpMethod.GET, null, SUPPORTED_CONTENT_TYPE_JSON_DEF, 200, ResponseHandler.json(getAllMT));
      JsonResponse getAllMTURLResponse = getAllMT.get(5, TimeUnit.SECONDS);
      assertThat(getAllMTURLResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
      System.out.println(getAllMTURLResponse.getBody() +
        "\nStatus - " + getAllMTURLResponse.getStatusCode() + " at " + System.currentTimeMillis() + " for " + getAllMTUrl);
      assertThat(isSizeMatch(getAllMTURLResponse, 1), is(true));

      /**delete an mt - should fail as there is an item associated with the mt*/
      CompletableFuture<JsonResponse> delMT = new CompletableFuture();
      String delMTURL = url+MATERIAL_TYPE_URL+materialTypeID;
      send(delMTURL, HttpMethod.DELETE, null, SUPPORTED_CONTENT_TYPE_JSON_DEF, 204, ResponseHandler.json(delMT));
      JsonResponse delMTResponse = delMT.get(5, TimeUnit.SECONDS);
      assertThat(delMTResponse.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
      System.out.println(delMTResponse.getBody() +
        "\nStatus - " + delMTResponse.getStatusCode() + " at " + System.currentTimeMillis() + " for " + delMTURL);

      /**delete item belonging to an mt*/
      CompletableFuture<JsonResponse> delAllItemsMT = new CompletableFuture();
      String delAllMTURL = url+ITEM_URL+"/"+itemID;
      send(delAllMTURL, HttpMethod.DELETE, null,
        SUPPORTED_CONTENT_TYPE_JSON_DEF, 204, ResponseHandler.json(delAllItemsMT));
      JsonResponse delAllMTResponse = delAllItemsMT.get(5, TimeUnit.SECONDS);
      assertThat(delAllMTResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
      System.out.println(delAllMTResponse.getBody() +
        "\nStatus - " + delAllMTResponse.getStatusCode() + " at " + System.currentTimeMillis() + " for " + delAllMTURL);

      /**delete an mt with no items attached*/
      CompletableFuture<JsonResponse> delDetachedMT = new CompletableFuture();
      String delDetachedMTURL = url+MATERIAL_TYPE_URL+materialTypeID;
      send(delDetachedMTURL, HttpMethod.DELETE, null,
        SUPPORTED_CONTENT_TYPE_JSON_DEF, 204, ResponseHandler.json(delDetachedMT));
      JsonResponse delDetachedMTResponse = delDetachedMT.get(5, TimeUnit.SECONDS);
      assertThat(delDetachedMTResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
      System.out.println(delDetachedMTResponse.getBody() +
        "\nStatus - " + delDetachedMTResponse.getStatusCode() + " at " + System.currentTimeMillis() + " for " + delDetachedMTURL);

      /**add a material type for item-storage tests to pass*/
      CompletableFuture<JsonResponse> createMT3 = new CompletableFuture();
      String createMTURL3 = url+MATERIAL_TYPE_URL;
      send(createMTURL3, HttpMethod.POST, putRequest,
        SUPPORTED_CONTENT_TYPE_JSON_DEF, 201, ResponseHandler.json(createMT3));
      JsonResponse createMTURLResponse3 = createMT3.get(5, TimeUnit.SECONDS);
      assertThat(createMTURLResponse3.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
      System.out.println(createMTURLResponse3.getBody() +
        "\nStatus - " + createMTURLResponse3.getStatusCode() + " at " + System.currentTimeMillis() + " for " + createMTURL3);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private boolean isSizeMatch(JsonResponse r, int size){
    if(r.getJson().getInteger("totalRecords") == size){
      return true;
    }
    return false;
  }

  private void send(String url, HttpMethod method, String content,
      String contentType, int errorCode, Handler<HttpClientResponse> handler) {
    HttpClient client = StorageTestSuite.getVertx().createHttpClient();
    HttpClientRequest request;
    if(content == null){
      content = "";
    }
    Buffer buffer = Buffer.buffer(content);

    if (method == HttpMethod.POST) {
      request = client.postAbs(url);
    }
    else if (method == HttpMethod.DELETE) {
      request = client.deleteAbs(url);
    }
    else if (method == HttpMethod.GET) {
      request = client.getAbs(url);
    }
    else {
      request = client.putAbs(url);
    }
    request.exceptionHandler(error -> {
      Assert.fail(error.getLocalizedMessage());
    })
    .handler(handler);
    request.putHeader("Authorization", "test_tenant");
    request.putHeader("x-okapi-tenant", "test_tenant");
    request.putHeader("Accept", "application/json,text/plain");
    request.putHeader("Content-type", contentType);
    request.end(buffer);
  }

  private static JsonObject createItem() {

    JsonObject item = new JsonObject();

    //item.put("id", ""+UUID.randomUUID());
    item.put("instanceId", ""+UUID.randomUUID());
    item.put("title", "abcd");
    item.put("barcode", "12345");
    item.put("materialType", new JsonObject().put("name", "Book"));

    return item;
  }

}
