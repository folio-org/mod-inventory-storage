package org.folio.rest.api;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import org.folio.rest.jaxrs.model.InstanceBulkIdsGetField;
import org.folio.rest.jaxrs.model.InstanceBulkIdsGetFormat;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static java.net.HttpURLConnection.HTTP_OK;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.support.ResponseHandler.json;
import static org.folio.rest.support.http.InterfaceUrls.*;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

@RunWith(VertxUnitRunner.class)
public class InstanceBulkTest extends TestBaseWithInventoryUtil {
  private static final Logger log =
    LoggerFactory.getLogger(InstanceBulkTest.class);

  private Set<String> instanceIdsToRemoveAfterTest = new HashSet<>();

  @Before
  public void beforeEach() {
    StorageTestSuite.deleteAll(itemsStorageUrl(""));
    StorageTestSuite.deleteAll(holdingsStorageUrl(""));
    StorageTestSuite.deleteAll(instancesStorageUrl(""));
  }

  @After
  public void resetInstanceHRID() {
    setInstanceSequence(1);
  }

  @After
  public void checkIdsAfterEach() {
    StorageTestSuite.checkForMismatchedIDs("instance");
  }

  @After
  public void removeGeneratedEntities(TestContext context) {
    final Async async = context.async();
    List<CompletableFuture<Response>> cfs =
      new ArrayList<CompletableFuture<Response>>();

    instanceIdsToRemoveAfterTest.forEach(id -> cfs.add(client
      .delete(instancesStorageUrl("/" + id), TENANT_ID)));
    CompletableFuture.allOf(cfs.toArray(new CompletableFuture[cfs.size()]))
      .thenAccept(v -> async.complete());
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
  public void canGetInstanceBulkOfRawId()
      throws MalformedURLException,
      InterruptedException,
      ExecutionException,
      TimeoutException {

    int totalMoons = 2;
    int expectedMatches = totalMoons;
    Map<String, JsonObject> moons = manyMoons(totalMoons,
      InstanceBulkIdsGetField.ID,
      InstanceBulkIdsGetFormat.RAW);
    createManyMoons(moons);

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    URL getInstanceUrl = instanceBulkUrl("/ids?type=id&format=raw");

    client.get(getInstanceUrl, TENANT_ID, json(getCompleted));

    Response response = getCompleted.get(5, SECONDS);
    validateMoonsResponse(response, expectedMatches, moons);
  }

  @Test
  public void canGetInstanceBulkOfBase64Id()
      throws MalformedURLException,
      InterruptedException,
      ExecutionException,
      TimeoutException {

    int totalMoons = 2;
    int expectedMatches = totalMoons;
    Map<String, JsonObject> moons = manyMoons(totalMoons,
      InstanceBulkIdsGetField.ID,
      InstanceBulkIdsGetFormat.BASE64);
    createManyMoons(moons);

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    URL getInstanceUrl = instanceBulkUrl("/ids?type=id&format=base64");

    client.get(getInstanceUrl, TENANT_ID, json(getCompleted));

    Response response = getCompleted.get(5, SECONDS);
    validateMoonsResponse(response, expectedMatches, moons);
  }

  @Test
  public void canGetInstanceBulkOfRawIdStream(TestContext context)
      throws MalformedURLException,
      InterruptedException,
      ExecutionException,
      TimeoutException {

    int totalMoons = 500;
    int expectedMatches = totalMoons;
    Map<String, JsonObject> moons = manyMoons(totalMoons);
    createManyMoons(moons);

    URL getInstanceUrl = instanceBulkUrl("/ids?type=id&format=raw");

    Buffer buffer = bufferedCheckURLs(context, getInstanceUrl, 200);

    JsonObject collection = new JsonObject(buffer);
    validateMoonsResponse(collection, expectedMatches, moons);
  }

  @Test
  public void canGetInstanceBulkOfBase64IdStream(TestContext context)
      throws MalformedURLException,
      InterruptedException,
      ExecutionException,
      TimeoutException {

    int totalMoons = 500;
    int expectedMatches = totalMoons;
    Map<String, JsonObject> moons = manyMoons(totalMoons,
      InstanceBulkIdsGetField.ID,
      InstanceBulkIdsGetFormat.BASE64);
    createManyMoons(moons);

    URL getInstanceUrl = instanceBulkUrl("/ids?type=id&format=base64");

    Buffer buffer = bufferedCheckURLs(context, getInstanceUrl, 200);

    JsonObject collection = new JsonObject(buffer);
    validateMoonsResponse(collection, expectedMatches, moons);
  }

  @Test
  public void canGetInstanceBulkOfRawIdWithQueryExact()
      throws MalformedURLException,
      InterruptedException,
      ExecutionException,
      TimeoutException {

    int totalMoons = 10;
    int expectedMatches = 1;
    Map<String, JsonObject> moons = manyMoons(totalMoons,
      InstanceBulkIdsGetField.ID,
      InstanceBulkIdsGetFormat.RAW);
    createManyMoons(moons);

    String query = "query=keyword%20all%20%22Moon%20%233%22";
    URL getInstanceUrl = instanceBulkUrl("/ids?type=id&format=raw&" + query);

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    client.get(getInstanceUrl, TENANT_ID, json(getCompleted));

    Response response = getCompleted.get(5, SECONDS);
    validateMoonsResponse(response, expectedMatches, moons);
  }

  @Test
  public void canGetInstanceBulkOfRawIdWithQueryWildcard()
      throws MalformedURLException,
      InterruptedException,
      ExecutionException,
      TimeoutException {

    int totalMoons = 20;
    int expectedMatches = 11;
    Map<String, JsonObject> moons = manyMoons(totalMoons,
      InstanceBulkIdsGetField.ID,
      InstanceBulkIdsGetFormat.RAW);
    createManyMoons(moons);

    String query = "query=keyword%20all%20%22Moon%20%231%2A%22";
    URL getInstanceUrl = instanceBulkUrl("/ids?type=id&format=raw&" + query);

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
    assertThat(collection.getInteger("totalRecords"), is (expectedMatches));

    for (int i = 0; i < ids.size(); i++) {
      JsonObject idObject = ids.getJsonObject(i);
      assertThat(moons.containsKey(idObject.getString("id")), is(true));
    }
  }

  private void setInstanceSequence(long sequenceNumber) {
    final Vertx vertx = StorageTestSuite.getVertx();
    final PostgresClient postgresClient =
        PostgresClient.getInstance(vertx, TENANT_ID);
    final CompletableFuture<Void> sequenceSet = new CompletableFuture<>();

    vertx.runOnContext(v -> {
      postgresClient.selectSingle("select setval('hrid_instances_seq',"
          + sequenceNumber + ",FALSE)", r -> {
            if (r.succeeded()) {
              sequenceSet.complete(null);
            } else {
              sequenceSet.completeExceptionally(r.cause());
            }
          });
    });

    try {
      sequenceSet.get(2, SECONDS);
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  private void createInstance(JsonObject instanceToCreate)
      throws MalformedURLException,
      InterruptedException,
      ExecutionException,
      TimeoutException {

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    client.post(instancesStorageUrl(""), instanceToCreate,
      TENANT_ID, json(createCompleted));

    Response response = createCompleted.get(2, SECONDS);

    assertThat(String.format("Create instance failed: %s", response.getBody()),
      response.getStatusCode(), is(201));
  }

  private void createManyMoons(Map<String, JsonObject> instancesToCreate)
      throws MalformedURLException,
      InterruptedException,
      ExecutionException,
      TimeoutException {

    for (String id : instancesToCreate.keySet()) {
      createInstance(instancesToCreate.get(id));
    }
  }

  private Map<String, JsonObject> manyMoons(int total) {
    return manyMoons(total, InstanceBulkIdsGetField.ID,
      InstanceBulkIdsGetFormat.RAW);
  }

  private Map<String, JsonObject> manyMoons(int total,
      InstanceBulkIdsGetField field, InstanceBulkIdsGetFormat format) {
      Map<String, JsonObject> moons = new HashMap<String, JsonObject>();
      Base64.Encoder encoder = Base64.getEncoder();

    for (int i = 0; i < total; i++) {
      UUID uuid = UUID.randomUUID();
      String hrid = String.format("in%011d", i + 1);

      String raw = "";
      if (field.compareTo(InstanceBulkIdsGetField.ID) == 0) {
        raw = uuid.toString();
      }

      String id = raw;
      if (format.compareTo(InstanceBulkIdsGetFormat.BASE64) == 0) {
        id = encoder.encodeToString(raw.getBytes());
      }

      JsonArray identifiers = new JsonArray();
      identifiers.add(identifier(UUID_ISBN, "9781473619777"));
      JsonArray contributors = new JsonArray();
      contributors.add(contributor(UUID_PERSONAL_NAME, "Chambers, Becky"));
      JsonArray tags = new JsonArray();
      tags.add("test-tag");

      JsonObject jsonb = createInstanceRequest(uuid, "TEST", "Moon #" + i,
        identifiers, contributors, UUID_INSTANCE_TYPE, tags, hrid);
      moons.put(id, jsonb);
    }

    return moons;
  }

  public Buffer bufferedCheckURLs(TestContext context, URL url,
      int codeExpected) {
    String accept = "application/json";
    return bufferedCheckURLs(context, url, codeExpected, accept);
  }

  private Buffer bufferedCheckURLs(TestContext context, URL url,
      int codeExpected, String accept) {
    Buffer res = Buffer.buffer();
    final Vertx vertx = StorageTestSuite.getVertx();

    try {
      Async async = context.async();
      io.vertx.core.http.HttpClient client = vertx.createHttpClient();
      HttpClientRequest request = client.getAbs(url.toString(),
          httpClientResponse -> {
        httpClientResponse.handler(res::appendBuffer);
        httpClientResponse.endHandler(x -> {
          log.info(httpClientResponse.statusCode() + ", "
            + codeExpected + " status expected: " + url);
          context.assertEquals(codeExpected, httpClientResponse.statusCode(),
            url.toString());
          log.info(res.toString());
          async.complete();
        });
      });
      request.exceptionHandler(error -> {
        context.fail(url + " - " + error.getMessage());
        async.complete();
      });
      request.headers().add("x-okapi-tenant", TENANT_ID);
      request.headers().add("Accept", accept);
      request.setChunked(true);
      request.end();
      async.await();
    } catch (Throwable e) {
      log.error(e.getMessage(), e);
      context.fail(e);
    }

    return res;
  }
}
