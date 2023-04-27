package org.folio.rest.api;

import static java.net.HttpURLConnection.HTTP_OK;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.rest.support.ResponseHandler.json;
import static org.folio.rest.support.http.InterfaceUrls.holdingsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.instancesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.recordBulkUrl;
import static org.folio.util.StringUtil.urlEncode;
import static org.folio.utility.ModuleUtility.getClient;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import lombok.SneakyThrows;
import org.folio.rest.jaxrs.model.RecordBulkIdsGetField;
import org.folio.rest.support.Response;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class RecordBulkTest extends TestBaseWithInventoryUtil {

  private static JsonObject createInstanceRequest(
    UUID id,
    String source,
    String title,
    JsonArray identifiers,
    JsonArray contributors,
    UUID instanceTypeId,
    JsonArray tags,
    String hrid,
    JsonArray languages) {

    JsonObject instanceToCreate = createInstanceRequest(
      id, source, title, identifiers, contributors, instanceTypeId, tags);

    if (hrid != null) {
      instanceToCreate.put("hrid", hrid);
    }

    if (languages != null) {
      instanceToCreate.put("languages", languages);
    }

    return instanceToCreate;
  }

  @SneakyThrows
  @Before
  public void beforeEach() {
    StorageTestSuite.deleteAll(itemsStorageUrl(""));
    StorageTestSuite.deleteAll(holdingsStorageUrl(""));
    StorageTestSuite.deleteAll(instancesStorageUrl(""));

    removeAllEvents();
  }

  @After
  public void checkIdsAfterEach() {
    StorageTestSuite.checkForMismatchedIds("instance");
    StorageTestSuite.checkForMismatchedIds("holdings_record");
  }

  @Test
  public void canGetInstanceBulkUsingDefaults()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    int totalMoons = 2;
    int expectedMatches = totalMoons;
    Map<String, JsonObject> moons = manyMoons(totalMoons);
    createManyMoons(moons);

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    URL getInstanceUrl = recordBulkUrl("/ids");

    getClient().get(getInstanceUrl, TENANT_ID, json(getCompleted));

    Response response = getCompleted.get(10, SECONDS);
    validateMoonsResponseWithTotal(response, expectedMatches, moons);
  }

  @Test
  public void canGetInstanceBulkOfId()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    int totalMoons = 2;
    int expectedMatches = totalMoons;
    Map<String, JsonObject> moons = manyMoons(totalMoons,
      RecordBulkIdsGetField.ID);
    createManyMoons(moons);

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    URL getInstanceUrl = recordBulkUrl("/ids?type=id");

    getClient().get(getInstanceUrl, TENANT_ID, json(getCompleted));

    Response response = getCompleted.get(10, SECONDS);
    validateMoonsResponseWithTotal(response, expectedMatches, moons);
  }

  @Test
  public void canGetInstanceBulkOfIdWithLimitAndOffset()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    int totalMoons = 20;
    int expectedMatches = 5;
    Map<String, JsonObject> moons = manyMoons(totalMoons,
      RecordBulkIdsGetField.ID);
    createManyMoons(moons);

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    URL getInstanceUrl = recordBulkUrl("/ids?type=id&limit=5&offset=0");

    getClient().get(getInstanceUrl, TENANT_ID, json(getCompleted));

    Response response = getCompleted.get(10, SECONDS);
    validateMoonsResponse(response, expectedMatches, moons);
  }

  @Test
  public void canGetInstanceIdsByEffectiveLocation() throws ExecutionException, InterruptedException, TimeoutException {
    var expectedInstanceId = UUID.randomUUID();
    var effectiveLocationId = MAIN_LIBRARY_LOCATION_ID;
    instancesClient.create(instance(expectedInstanceId));
    var holdingId = createHolding(expectedInstanceId, effectiveLocationId, null);
    createItem(buildItem(holdingId, null, null));

    var secondInstanceId = UUID.randomUUID();
    var secondLocationId = ANNEX_LIBRARY_LOCATION_ID;
    instancesClient.create(instance(secondInstanceId));
    var secondHoldingId = createHolding(expectedInstanceId, secondLocationId, null);
    createItem(buildItem(secondHoldingId, null, null));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    URL getInstanceUrl =
      recordBulkUrl(String.format("/ids?query=(items.effectiveLocationId==\"%s\")", effectiveLocationId));
    getClient().get(getInstanceUrl, TENANT_ID, json(getCompleted));
    Response response = getCompleted.get(10, SECONDS);

    assertThat(response.getStatusCode(), is(HTTP_OK));

    var ids = response.getJson().getJsonArray("ids");
    assertThat(ids.size(), is(1));

    var instanceId = ids.getJsonObject(0).getString("id");
    assertThat(instanceId, equalTo(expectedInstanceId.toString()));
  }

  @Test
  public void canGetInstanceBulkOfIdWithQueryExact()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    int totalMoons = 10;
    int expectedMatches = 1;
    Map<String, JsonObject> moons = manyMoons(totalMoons,
      RecordBulkIdsGetField.ID);
    createManyMoons(moons);

    String query = urlEncode("title all \"Moon #3\"");
    URL getInstanceUrl = recordBulkUrl("/ids?type=id&query=" + query);

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    getClient().get(getInstanceUrl, TENANT_ID, json(getCompleted));

    Response response = getCompleted.get(10, SECONDS);
    validateMoonsResponseWithTotal(response, expectedMatches, moons);
  }

  @Test
  public void cannotGetInstanceBulkOfNonExistentId()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    int totalMoons = 2;
    int expectedMatches = 0;
    Map<String, JsonObject> moons = manyMoons(totalMoons,
      RecordBulkIdsGetField.ID);
    createManyMoons(moons);

    String query = urlEncode("keyword all \"Planet #1*\"");
    URL getInstanceUrl = recordBulkUrl("/ids?type=id&query=" + query);

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    getClient().get(getInstanceUrl, TENANT_ID, json(getCompleted));

    Response response = getCompleted.get(10, SECONDS);
    validateMoonsResponseWithTotal(response, expectedMatches, moons);
  }

  @Test
  public void canGetHoldingsBulkOfId()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    int totalHoldingsIds = 2;
    List<String> holdingIds = createAndGetHoldingsIds(totalHoldingsIds);

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    URL getInstanceUrl = recordBulkUrl("/ids?recordType=HOLDING");

    getClient().get(getInstanceUrl, TENANT_ID, json(getCompleted));

    Response response = getCompleted.get(10, SECONDS);
    validateHoldingsResponseWithTotals(response, holdingIds, totalHoldingsIds);
  }

  @Test
  public void canGetHoldingsBulkOfIdWithLimitAndOffset()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    int totalHoldingsIds = 20;
    List<String> holdingIds = createAndGetHoldingsIds(totalHoldingsIds);

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    URL getInstanceUrl = recordBulkUrl("/ids?recordType=HOLDING&limit=5");

    getClient().get(getInstanceUrl, TENANT_ID, json(getCompleted));

    Response response = getCompleted.get(10, SECONDS);
    validateHoldingsResponse(response, holdingIds, 5);
  }

  private void validateMoonsResponseWithTotal(Response response, int expectedMatches,
                                              Map<String, JsonObject> moons) {
    assertThat(response.getStatusCode(), is(HTTP_OK));
    validateMoonsResponseWithTotal(response.getJson(), expectedMatches, moons);
  }

  private void validateMoonsResponseWithTotal(JsonObject collection,
                                              int expectedMatches, Map<String, JsonObject> moons) {
    JsonArray ids = collection.getJsonArray("ids");
    assertThat(ids.size(), is(expectedMatches));
    assertThat(collection.getInteger("totalRecords"), is(expectedMatches));

    for (int i = 0; i < ids.size(); i++) {
      JsonObject idObject = ids.getJsonObject(i);
      assertThat(moons.containsKey(idObject.getString("id")), is(true));
    }
  }

  private void validateMoonsResponse(Response response, int expectedMatches,
                                     Map<String, JsonObject> moons) {
    assertThat(response.getStatusCode(), is(HTTP_OK));
    validateMoonsResponse(response.getJson(), expectedMatches, moons);
  }

  private void validateMoonsResponse(JsonObject collection,
                                     int expectedMatches, Map<String, JsonObject> moons) {
    JsonArray ids = collection.getJsonArray("ids");
    assertThat(ids.size(), is(expectedMatches));
    for (int i = 0; i < ids.size(); i++) {
      JsonObject idObject = ids.getJsonObject(i);
      assertThat(moons.containsKey(idObject.getString("id")), is(true));
    }
  }

  private void validateHoldingsResponse(Response response, List<String> holdingsIds,
                                        int expectedMatches) {
    JsonArray ids = response.getJson().getJsonArray("ids");
    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertThat(ids.size(), is(expectedMatches));
    for (Object id : ids) {
      JsonObject idObject = (JsonObject) id;
      assertThat(holdingsIds.contains(idObject.getString("id")), is(true));
    }
  }

  private void validateHoldingsResponseWithTotals(Response response, List<String> holdingsIds,
                                                  int expectedMatches) {
    JsonArray ids = response.getJson().getJsonArray("ids");
    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertThat(ids.size(), is(expectedMatches));
    assertThat(response.getJson().getInteger("totalRecords"), is(expectedMatches));
    for (Object id : ids) {
      JsonObject idObject = (JsonObject) id;
      assertThat(holdingsIds.contains(idObject.getString("id")), is(true));
    }
  }

  private void createManyMoons(Map<String, JsonObject> instancesToCreate)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    for (String id : instancesToCreate.keySet()) {
      instancesClient.create(instancesToCreate.get(id));
    }
  }

  private Map<String, JsonObject> manyMoons(int total) {
    return manyMoons(total, RecordBulkIdsGetField.ID);
  }

  private Map<String, JsonObject> manyMoons(int total,
                                            RecordBulkIdsGetField field) {
    Map<String, JsonObject> moons = new HashMap<String, JsonObject>();

    for (int i = 0; i < total; i++) {
      UUID uuid = UUID.randomUUID();
      final String hrid = String.format("in%011d", i + 1);

      String id = "";
      if (field.compareTo(RecordBulkIdsGetField.ID) == 0) {
        id = uuid.toString();
      }

      JsonArray identifiers = new JsonArray();
      identifiers.add(identifier(UUID_ISBN, "9781473619777"));
      JsonArray contributors = new JsonArray();
      contributors.add(contributor(UUID_PERSONAL_NAME, "Chambers, Becky"));
      JsonArray tags = new JsonArray();
      tags.add("test-tag");

      JsonArray langs = new JsonArray();
      langs.add("eng");

      JsonObject jsonb = createInstanceRequest(uuid, "TEST", "Moon #" + i,
        identifiers, contributors, UUID_INSTANCE_TYPE, tags, hrid, langs);
      moons.put(id, jsonb);
    }

    return moons;
  }

  private List<String> createAndGetHoldingsIds(int totalCount) {
    List<String> holdingIds = new ArrayList<>();
    for (int i = 0; i < totalCount; i++) {
      String id = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID).toString();
      holdingIds.add(id);
    }
    return holdingIds;
  }
}
