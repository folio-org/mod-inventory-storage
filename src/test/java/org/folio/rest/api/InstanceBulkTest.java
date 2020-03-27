package org.folio.rest.api;

import static java.net.HttpURLConnection.HTTP_OK;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.support.ResponseHandler.json;
import static org.folio.rest.support.http.InterfaceUrls.holdingsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.instanceBulkUrl;
import static org.folio.rest.support.http.InterfaceUrls.instancesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.folio.util.StringUtil.urlEncode;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.rest.jaxrs.model.InstanceBulkIdsGetField;
import org.folio.rest.support.Response;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class InstanceBulkTest extends TestBaseWithInventoryUtil {

  @Before
  public void beforeEach() {
    StorageTestSuite.deleteAll(itemsStorageUrl(""));
    StorageTestSuite.deleteAll(holdingsStorageUrl(""));
    StorageTestSuite.deleteAll(instancesStorageUrl(""));
  }

  @After
  public void checkIdsAfterEach() {
    StorageTestSuite.checkForMismatchedIDs("instance");
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
    URL getInstanceUrl = instanceBulkUrl("/ids");

    client.get(getInstanceUrl, TENANT_ID, json(getCompleted));

    Response response = getCompleted.get(5, SECONDS);
    validateMoonsResponse(response, expectedMatches, moons);
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
      InstanceBulkIdsGetField.ID);
    createManyMoons(moons);

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    URL getInstanceUrl = instanceBulkUrl("/ids?type=id");

    client.get(getInstanceUrl, TENANT_ID, json(getCompleted));

    Response response = getCompleted.get(5, SECONDS);
    validateMoonsResponse(response, expectedMatches, moons);
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
      InstanceBulkIdsGetField.ID);
    createManyMoons(moons);

    String query = urlEncode("keyword all \"Moon #3\"");
    URL getInstanceUrl = instanceBulkUrl("/ids?type=id&query=" + query);

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    client.get(getInstanceUrl, TENANT_ID, json(getCompleted));

    Response response = getCompleted.get(5, SECONDS);
    validateMoonsResponse(response, expectedMatches, moons);
  }

  @Test
  public void canGetInstanceBulkOfIdWithQueryWildcard()
      throws MalformedURLException,
      InterruptedException,
      ExecutionException,
      TimeoutException {

    int totalMoons = 20;
    int expectedMatches = 11;
    Map<String, JsonObject> moons = manyMoons(totalMoons,
      InstanceBulkIdsGetField.ID);
    createManyMoons(moons);

    String query = urlEncode("keyword all \"Moon #1*\"");
    URL getInstanceUrl = instanceBulkUrl("/ids?type=id&query=" + query);

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    client.get(getInstanceUrl, TENANT_ID, json(getCompleted));

    Response response = getCompleted.get(5, SECONDS);
    validateMoonsResponse(response, expectedMatches, moons);
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
      InstanceBulkIdsGetField.ID);
    createManyMoons(moons);

    String query = urlEncode("keyword all \"Planet #1*\"");
    URL getInstanceUrl = instanceBulkUrl("/ids?type=id&query=" + query);

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    client.get(getInstanceUrl, TENANT_ID, json(getCompleted));

    Response response = getCompleted.get(5, SECONDS);
    validateMoonsResponse(response, expectedMatches, moons);
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
    assertThat(collection.getInteger("totalRecords"), is(expectedMatches));

    for (int i = 0; i < ids.size(); i++) {
      JsonObject idObject = ids.getJsonObject(i);
      assertThat(moons.containsKey(idObject.getString("id")), is(true));
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
    return manyMoons(total, InstanceBulkIdsGetField.ID);
  }

  private Map<String, JsonObject> manyMoons(int total,
      InstanceBulkIdsGetField field) {
    Map<String, JsonObject> moons = new HashMap<String, JsonObject>();

    for (int i = 0; i < total; i++) {
      UUID uuid = UUID.randomUUID();
      String hrid = String.format("in%011d", i + 1);

      String id = "";
      if (field.compareTo(InstanceBulkIdsGetField.ID) == 0) {
        id = uuid.toString();
      }

      JsonArray identifiers = new JsonArray();
      identifiers.add(identifier(UUID_ISBN, "9781473619777"));
      JsonArray contributors = new JsonArray();
      contributors.add(contributor(UUID_PERSONAL_NAME, "Chambers, Becky"));
      JsonArray tags = new JsonArray();
      tags.add("test-tag");

      JsonArray langs = new JsonArray();
      langs.add(new String("eng"));

      JsonObject jsonb = createInstanceRequest(uuid, "TEST", "Moon #" + i,
        identifiers, contributors, UUID_INSTANCE_TYPE, tags, hrid, langs);
      moons.put(id, jsonb);
    }

    return moons;
  }

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
}
