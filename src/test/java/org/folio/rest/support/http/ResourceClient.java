package org.folio.rest.support.http;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.folio.rest.api.StorageTestSuite;
import org.folio.rest.support.HttpClient;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.JsonArrayHelper;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.builders.Builder;
import org.folio.util.StringUtil;

import io.vertx.core.json.JsonObject;

public class ResourceClient {

  private final HttpClient client;
  private final UrlMaker urlMaker;
  private final String resourceName;
  private final String collectionArrayPropertyName;

  public static ResourceClient forItems(HttpClient client) {
    return new ResourceClient(client, InterfaceUrls::itemsStorageUrl,
      "items");
  }

  public static ResourceClient forHoldings(HttpClient client) {
    return new ResourceClient(client, InterfaceUrls::holdingsStorageUrl,
      "holdingsRecords");
  }

  public static ResourceClient forHoldingsType(HttpClient client) {
    return new ResourceClient(client, InterfaceUrls::holdingsTypesUrl,
      "holdingsTypeRecords");
  }

  public static ResourceClient forInstances(HttpClient client) {
    return new ResourceClient(client, InterfaceUrls::instancesStorageUrl,
      "instances");
  }

  public static ResourceClient forMaterialTypes(HttpClient client) {
    return new ResourceClient(client, InterfaceUrls::materialTypesStorageUrl,
      "material types", "mtypes");
  }

  public static ResourceClient forModesOfIssuance(HttpClient client) {
    return new ResourceClient(client, InterfaceUrls::modesOfIssuanceUrl,
      "modes of issuance", "issuanceModes");
  }

  public static ResourceClient forInstanceRelationships(HttpClient client) {
    return new ResourceClient(client, InterfaceUrls::instanceRelationshipsUrl,
      "instance relationships", "instanceRelationships");
  }

  public static ResourceClient forInstanceRelationshipTypes(HttpClient client) {
    return new ResourceClient(client, InterfaceUrls::instanceRelationshipTypesUrl,
      "instance relationship types", "instanceRelationshipTypes");
  }

  public static ResourceClient forPrecedingSucceedingTitles(HttpClient client) {
    return new ResourceClient(client, InterfaceUrls::precedingSucceedingTitleUrl,
      "preceding succeeding titles", "precedingSucceedingTitles");
  }

  public static ResourceClient forLoanTypes(HttpClient client) {
    return new ResourceClient(client, InterfaceUrls::loanTypesStorageUrl,
      "loan types", "loantypes");
  }

  public static ResourceClient forLocations(HttpClient client) {
    return new ResourceClient(client, InterfaceUrls::ShelfLocationsStorageUrl,
      "locations", "shelflocations");
  }

  public static ResourceClient forInstanceTypes(HttpClient client) {
    return new ResourceClient(client, InterfaceUrls::instanceTypesStorageUrl,
      "instance types", "instanceTypes");
  }

  public static ResourceClient forCallNumberTypes(HttpClient client) {
    return new ResourceClient(client, InterfaceUrls::callNumberTypesUrl,
      "call number types", "callNumberTypes");
  }

  public static ResourceClient forInstancesStorageSync(HttpClient client) {
    return new ResourceClient(client, InterfaceUrls::instancesStorageSyncUrl,
      "Instances batch sync", "instances");
  }

  public static ResourceClient forItemsStorageSync(HttpClient client) {
    return new ResourceClient(client, InterfaceUrls::itemsStorageSyncUrl,
      "Items batch sync", "items");
  }

  public static ResourceClient forInstancesStorageBatchInstances(HttpClient client) {
    return new ResourceClient(client, InterfaceUrls::instancesStorageBatchInstancesUrl,
      "Instances batch (Deprecated)", "instances");
  }

  private ResourceClient(
    HttpClient client,
    UrlMaker urlMaker, String resourceName,
    String collectionArrayPropertyName) {

    this.client = client;
    this.urlMaker = urlMaker;
    this.resourceName = resourceName;
    this.collectionArrayPropertyName = collectionArrayPropertyName;
  }

  private ResourceClient(
    HttpClient client,
    UrlMaker urlMaker,
    String resourceName) {

    this.client = client;
    this.urlMaker = urlMaker;
    this.resourceName = resourceName;
    this.collectionArrayPropertyName = resourceName;
  }

  public IndividualResource create(Builder builder)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return create(builder.create());
  }

  public IndividualResource create(JsonObject request) throws MalformedURLException,
    InterruptedException, ExecutionException, TimeoutException {

    Response response = attemptToCreate(request);

    assertThat(
      String.format("Failed to create %s: %s", resourceName, response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    System.out.println(String.format("Created resource %s: %s", resourceName,
      response.getJson().encodePrettily()));

    return new IndividualResource(response);
  }

  public void createNoResponse(JsonObject request) throws MalformedURLException,
    InterruptedException, ExecutionException, TimeoutException {

    Response response = attemptToCreate(request);

    assertThat(
      String.format("Failed to create %s: %s", resourceName, response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
  }

  public Response attemptToCreate(JsonObject request) throws MalformedURLException,
    InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    client.post(urlMaker.combine(""), request, StorageTestSuite.TENANT_ID,
      ResponseHandler.any(createCompleted));

    return createCompleted.get(5, TimeUnit.SECONDS);
  }

  public void replace(UUID id, Builder builder)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    replace(id, builder.create());
  }

  public void replace(UUID id, JsonObject request) throws MalformedURLException,
    InterruptedException, ExecutionException, TimeoutException {

    Response putResponse = attemptToReplace(id != null ? id.toString() : null, request);

    assertThat(
      String.format("Failed to update %s %s: %s", resourceName, id, putResponse.getBody()),
      putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
  }

  public Response attemptToReplace(String id, JsonObject request) throws MalformedURLException,
    InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    client.put(urlMaker.combine(String.format("/%s", id)), request,
      StorageTestSuite.TENANT_ID, ResponseHandler.any(putCompleted));

    return putCompleted.get(5, TimeUnit.SECONDS);
  }

  public Response getById(UUID id) throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    return getByIdIfPresent(id != null ? id.toString() : null);
  }

  public Response getByIdIfPresent(String id) throws MalformedURLException,
    InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(urlMaker.combine(String.format("/%s", id)),
      StorageTestSuite.TENANT_ID, ResponseHandler.any(getCompleted));

    return getCompleted.get(5, TimeUnit.SECONDS);
  }

  public Response deleteIfPresent(String id) throws MalformedURLException,
    InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<Response> deleteFinished = new CompletableFuture<>();

    client.delete(urlMaker.combine(String.format("/%s", id)),
      StorageTestSuite.TENANT_ID, ResponseHandler.any(deleteFinished));

    return deleteFinished.get(5, TimeUnit.SECONDS);
  }

  public void delete(UUID id) throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    Response response = deleteIfPresent(id != null ? id.toString() : null);

    assertThat(String.format(
      "Failed to delete %s %s: %s", resourceName, id, response.getBody()),
      response.getStatusCode(), is(204));
  }

  public void deleteAll()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<Response> deleteAllFinished = new CompletableFuture<>();

    client.delete(urlMaker.combine(""), StorageTestSuite.TENANT_ID,
      ResponseHandler.any(deleteAllFinished));

    Response response = deleteAllFinished.get(5, TimeUnit.SECONDS);

    assertThat(String.format(
      "Failed to delete %s: %s", resourceName, response.getBody()),
      response.getStatusCode(), is(204));
  }

  public void deleteAllIndividually()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    List<JsonObject> records = getAll();

    records.stream().forEach(record -> {
      try {
        CompletableFuture<Response> deleteFinished = new CompletableFuture<>();

        client.delete(urlMaker.combine(String.format("/%s",
          record.getString("id"))), StorageTestSuite.TENANT_ID,
          ResponseHandler.any(deleteFinished));

        Response deleteResponse = deleteFinished.get(5, TimeUnit.SECONDS);

        assertThat(String.format(
          "Failed to delete %s: %s", resourceName, deleteResponse.getBody()),
          deleteResponse.getStatusCode(), is(204));

      } catch (Throwable e) {
        assertThat(String.format("Exception whilst deleting %s individually: %s",
          resourceName, e.toString()),
          true, is(false));
      }
    });
  }

  public List<JsonObject> getAll()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    return getByQuery("");
  }

  public List<JsonObject> getByQuery(String query) throws MalformedURLException,
    InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<Response> getFinished = new CompletableFuture<>();

    client.get(urlMaker.combine(query), StorageTestSuite.TENANT_ID,
      ResponseHandler.any(getFinished));

    Response response = getFinished.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Get all records failed: %s", response.getBody()),
      response.getStatusCode(), is(200));

    return JsonArrayHelper.toList(response.getJson()
      .getJsonArray(collectionArrayPropertyName));
  }

  public List<IndividualResource> getMany(String query, Object... queryParams)
    throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<Response> getFinished = new CompletableFuture<>();

    final String encodedQuery = StringUtil
      .urlEncode(String.format(query, queryParams));

    client.get(urlMaker.combine("?query=" + encodedQuery),
      StorageTestSuite.TENANT_ID, ResponseHandler.json(getFinished));

    Response response = getFinished.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Get all records failed: %s", response.getBody()),
      response.getStatusCode(), is(200));

    return JsonArrayHelper.toList(response.getJson()
      .getJsonArray(collectionArrayPropertyName)).stream()
      .map(IndividualResource::new)
      .collect(Collectors.toList());
  }

  @FunctionalInterface
  public interface UrlMaker {
    URL combine(String subPath) throws MalformedURLException;
  }
}
