package org.folio.rest.support.http;

import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import io.vertx.core.json.JsonObject;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.folio.rest.api.TestBase;
import org.folio.rest.support.HttpClient;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.JsonArrayHelper;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.builders.Builder;
import org.folio.util.PercentCodec;
import org.folio.util.StringUtil;

public final class ResourceClient {

  private final HttpClient client;
  private final UrlMaker urlMaker;
  private final String resourceName;
  private final String collectionArrayPropertyName;

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

  public static ResourceClient forHoldingsSource(HttpClient client) {
    return new ResourceClient(client, InterfaceUrls::holdingsSourceUrl,
      "holdingsRecordsSources");
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

  public static ResourceClient forBoundWithParts(HttpClient client) {
    return new ResourceClient(client, InterfaceUrls::boundWithPartsUrl,
      "bound with parts", "boundWithParts");
  }

  public static ResourceClient forLoanTypes(HttpClient client) {
    return new ResourceClient(client, InterfaceUrls::loanTypesStorageUrl,
      "loan types", "loantypes");
  }

  public static ResourceClient forLocations(HttpClient client) {
    return new ResourceClient(client, InterfaceUrls::shelfLocationsStorageUrl,
      "locations", "shelflocations");
  }

  public static ResourceClient forInstanceTypes(HttpClient client) {
    return new ResourceClient(client, InterfaceUrls::instanceTypesStorageUrl,
      "instance types", "instanceTypes");
  }

  public static ResourceClient forSubjectTypes(HttpClient client) {
    return new ResourceClient(client, InterfaceUrls::subjectTypesUrl,
      "subject types", "subjectTypes");
  }

  public static ResourceClient forSubjectSources(HttpClient client) {
    return new ResourceClient(client, InterfaceUrls::subjectSourcesUrl,
      "subject sources", "subjectSources");
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

  public static ResourceClient forIllPolicies(HttpClient client) {
    return new ResourceClient(client, InterfaceUrls::illPoliciesUrl,
      "Ill Policies", "illPolicies");
  }

  public static ResourceClient forStatisticalCodeTypes(HttpClient client) {
    return new ResourceClient(client, InterfaceUrls::statisticalCodeTypesUrl,
      "Statistical code types", "statisticalCodeTypes");
  }

  public static ResourceClient forStatisticalCodes(HttpClient client) {
    return new ResourceClient(client, InterfaceUrls::statisticalCodesUrl,
      "Statistical codes", "statisticalCodes");
  }

  public static ResourceClient forInventoryView(HttpClient client) {
    return new ResourceClient(client, InterfaceUrls::inventoryViewInstances,
      "Inventory view", "instances");
  }

  public static ResourceClient forInstanceReindex(HttpClient client) {
    return new ResourceClient(client, InterfaceUrls::instanceReindex,
      "Instance reindex", "reindex");
  }

  public IndividualResource create(Builder builder) {

    return create(builder.create());
  }

  public IndividualResource create(JsonObject request) {

    return create(request, TENANT_ID);
  }

  public IndividualResource create(JsonObject request, String tenantId) {
    return create(request, tenantId, Map.of());
  }

  public IndividualResource create(JsonObject request, String tenantId, Map<String, String> headers) {

    Response response = attemptToCreate("", request, tenantId, headers);

    assertThat(
      String.format("Failed to create %s: %s", resourceName, response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    return new IndividualResource(response);
  }

  public void createNoResponse(JsonObject request) {

    Response response = attemptToCreate(request);

    assertThat(
      String.format("Failed to create %s: %s", resourceName, response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
  }

  public Response attemptToCreate(JsonObject request) {
    return attemptToCreate("", request);
  }

  public Response attemptToCreate(String subPath, JsonObject request) {
    return attemptToCreate(subPath, request, TENANT_ID);
  }

  public Response attemptToCreate(String subPath, JsonObject request, String tenantId) {
    return attemptToCreate(subPath, request, tenantId, Map.of());
  }

  public Response attemptToCreate(String subPath, JsonObject request, String tenantId, Map<String, String> headers) {

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    try {
      client.post(urlMaker.combine(subPath), request, headers, tenantId,
        ResponseHandler.any(createCompleted));
    } catch (MalformedURLException e) {
      throw new RuntimeException(subPath + ": " + e.getMessage(), e);
    }

    return TestBase.get(createCompleted);
  }

  public void replace(UUID id, Builder builder) {

    replace(id, builder.create());
  }

  public void replace(UUID id, JsonObject request) {

    Response putResponse = attemptToReplace(id != null ? id.toString() : null, request);

    assertThat(
      String.format("Failed to update %s %s: %s", resourceName, id, putResponse.getBody()),
      putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
  }

  public Response attemptToReplace(UUID id, JsonObject request) {
    return attemptToReplace(id.toString(), request);
  }

  public Response attemptToReplace(String id, JsonObject request) {
    return attemptToReplace(id, request, TENANT_ID, new HashMap<>());
  }

  public Response attemptToReplace(String id, JsonObject request, String tenantId, Map<String, String> headers) {
    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    client.put(urlMakerWithId(id), request, headers, tenantId, ResponseHandler.any(putCompleted));

    return TestBase.get(putCompleted);
  }

  public Response getById(UUID id) {

    return getByIdIfPresent(id != null ? id.toString() : null);
  }

  public Response getByIdIfPresent(String id) {
    return TestBase.get(client.get(urlMakerWithId(id), TENANT_ID));
  }

  public Response deleteIfPresent(String id) {

    CompletableFuture<Response> deleteFinished = new CompletableFuture<>();

    client.delete(urlMakerWithId(id),
      TENANT_ID, ResponseHandler.any(deleteFinished));

    return TestBase.get(deleteFinished);
  }

  public void delete(UUID id) {
    Response response = deleteIfPresent(id != null ? id.toString() : null);

    assertThat(String.format(
        "Failed to delete %s %s: %s", resourceName, id, response.getBody()),
      response.getStatusCode(), is(204));
  }

  public Response attemptToDelete(UUID id) {

    return deleteIfPresent(id != null ? id.toString() : null);
  }

  public Response attemptDeleteAll() {
    CompletableFuture<Response> deleteAllFinished = new CompletableFuture<>();

    try {
      var cql = PercentCodec.encode("cql.allRecords=1");
      client.delete(urlMaker.combine("?query=" + cql), TENANT_ID,
        ResponseHandler.any(deleteAllFinished));
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }

    return TestBase.get(deleteAllFinished);
  }

  public void deleteAll() {
    final Response response = attemptDeleteAll();

    assertThat(String.format(
        "Failed to delete %s: %s", resourceName, response.getBody()),
      response.getStatusCode(), is(204));
  }

  public void deleteAllIndividually() {

    List<JsonObject> records = getAll();

    records.stream().forEach(record -> {
      try {
        CompletableFuture<Response> deleteFinished = new CompletableFuture<>();

        client.delete(urlMakerWithId(record.getString("id")), TENANT_ID,
          ResponseHandler.any(deleteFinished));

        Response deleteResponse = TestBase.get(deleteFinished);

        assertThat(String.format(
            "Failed to delete %s: %s", resourceName, deleteResponse.getBody()),
          deleteResponse.getStatusCode(), is(204));

      } catch (Throwable e) {
        assertThat(String.format("Exception whilst deleting %s individually: %s",
            resourceName, e),
          true, is(false));
      }
    });
  }

  public List<JsonObject> getAll() {

    return getByQuery("");
  }

  public List<JsonObject> getByQuery(String query) {

    CompletableFuture<Response> getFinished = new CompletableFuture<>();

    try {
      client.get(urlMaker.combine(query), TENANT_ID,
        ResponseHandler.any(getFinished));
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }

    Response response = TestBase.get(getFinished);

    assertThat(String.format("Get all records failed: %s", response.getBody()),
      response.getStatusCode(), is(200));

    return JsonArrayHelper.toList(response.getJson()
      .getJsonArray(collectionArrayPropertyName));
  }

  public List<IndividualResource> getMany(String query, Object... queryParams) {

    CompletableFuture<Response> getFinished = new CompletableFuture<>();

    final String encodedQuery = StringUtil
      .urlEncode(String.format(query, queryParams));

    try {
      client.get(urlMaker.combine("?query=" + encodedQuery),
        TENANT_ID, ResponseHandler.json(getFinished));
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }

    Response response = TestBase.get(getFinished);

    assertThat(String.format("Get all records failed: %s", response.getBody()),
      response.getStatusCode(), is(200));

    return JsonArrayHelper.toList(response.getJson()
        .getJsonArray(collectionArrayPropertyName)).stream()
      .map(IndividualResource::new)
      .toList();
  }

  /**
   * Return urlMaker.combine(String.format("/%s", id)).
   *
   * <p>Wrap MalformedURLException into RuntimeException.
   */
  private URL urlMakerWithId(String id) {
    try {
      return urlMaker.combine(String.format("/%s", id));
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  @FunctionalInterface
  public interface UrlMaker {
    URL combine(String subPath) throws MalformedURLException;
  }
}
