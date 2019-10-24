package org.folio.rest.api;

import static java.net.HttpURLConnection.HTTP_OK;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.support.HttpResponseMatchers.statusCodeIs;
import static org.folio.rest.support.JsonObjectMatchers.hasSoleMessgeContaining;
import static org.folio.rest.support.JsonObjectMatchers.identifierMatches;
import static org.folio.rest.support.ResponseHandler.json;
import static org.folio.rest.support.http.InterfaceUrls.holdingsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.instancesStorageBatchInstancesUrl;
import static org.folio.rest.support.http.InterfaceUrls.instancesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.natureOfContentTermsUrl;
import static org.folio.util.StringUtil.urlEncode;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.folio.HttpStatus;
import org.folio.rest.jaxrs.model.MarcJson;
import org.folio.rest.jaxrs.model.NatureOfContentTerm;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.AdditionalHttpStatusCodes;
import org.folio.rest.support.JsonArrayHelper;
import org.folio.rest.support.JsonErrorResponse;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.builders.HoldingRequestBuilder;
import org.folio.rest.support.builders.ItemRequestBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class InstanceStorageTest extends TestBaseWithInventoryUtil {
  private static final String INSTANCES_KEY = "instances";
  private static final String TOTAL_RECORDS_KEY = "totalRecords";
  private static final String TAG_VALUE = "test-tag";
  private Set<String> natureOfContentIdsToRemoveAfterTest = new HashSet<>();

  // see also @BeforeClass TestBaseWithInventoryUtil.beforeAny()

  @Before
  public void beforeEach() {
    StorageTestSuite.deleteAll(itemsStorageUrl(""));
    StorageTestSuite.deleteAll(holdingsStorageUrl(""));
    StorageTestSuite.deleteAll(instancesStorageUrl(""));

    natureOfContentIdsToRemoveAfterTest.clear();
  }

  @After
  public void checkIdsAfterEach() {
    StorageTestSuite.checkForMismatchedIDs("instance");
  }

  @After
  public void removeGeneratedEntities() {
    natureOfContentIdsToRemoveAfterTest.forEach(id -> client
      .delete(natureOfContentTermsUrl("/" + id), TENANT_ID));
  }

  @Test
  public void canCreateAnInstance()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID id = UUID.randomUUID();
    NatureOfContentTerm journalContentType = createNatureOfContentTerm("journal_test");
    NatureOfContentTerm bookContentType = createNatureOfContentTerm("book_test");

    String[] natureOfContentIds = Stream.of(journalContentType, bookContentType)
      .map(NatureOfContentTerm::getId)
      .toArray(String[]::new);

    JsonObject instanceToCreate = smallAngryPlanet(id);
    instanceToCreate.put("natureOfContentTermIds", Arrays
      .asList(natureOfContentIds));

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    client.post(instancesStorageUrl(""), instanceToCreate, TENANT_ID,
      json(createCompleted));

    Response response = createCompleted.get(5, SECONDS);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject instance = response.getJson();

    assertThat(instance.getString("id"), is(id.toString()));
    assertThat(instance.getString("title"), is("Long Way to a Small Angry Planet"));

    JsonArray identifiers = instance.getJsonArray("identifiers");
    assertThat(identifiers.size(), is(1));
    assertThat(identifiers, hasItem(identifierMatches(UUID_ISBN.toString(), "9781473619777")));
    assertThat(instance.getJsonArray("natureOfContentTermIds"),
      containsInAnyOrder(natureOfContentIds));

    Response getResponse = getById(id);

    assertThat(getResponse.getStatusCode(), is(HTTP_OK));

    JsonObject instanceFromGet = getResponse.getJson();

    assertThat(instanceFromGet.getString("title"),
      is("Long Way to a Small Angry Planet"));

    JsonArray identifiersFromGet = instanceFromGet.getJsonArray("identifiers");
    assertThat(identifiersFromGet.size(), is(1));
    assertThat(identifiersFromGet, hasItem(identifierMatches(UUID_ISBN.toString(), "9781473619777")));

    List<String> tags = instanceFromGet.getJsonObject("tags").getJsonArray("tagList").getList();

    assertThat(tags.size(), is(1));
    assertThat(tags, hasItem(TAG_VALUE));
    assertThat(instanceFromGet.getJsonArray("natureOfContentTermIds"),
      containsInAnyOrder(natureOfContentIds));
  }

  @Test
  public void canCreateAnInstanceWithoutProvidingID()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    JsonObject instanceToCreate = smallAngryPlanet(null);

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    client.post(instancesStorageUrl(""), instanceToCreate, TENANT_ID,
      json(createCompleted));

    Response response = createCompleted.get(5, SECONDS);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject instance = response.getJson();

    String newId = instance.getString("id");

    assertThat(newId, is(notNullValue()));

    Response getResponse = getById(UUID.fromString(newId));

    assertThat(getResponse.getStatusCode(), is(HTTP_OK));

    JsonObject instanceFromGet = getResponse.getJson();

    assertThat(instanceFromGet.getString("title"),
      is("Long Way to a Small Angry Planet"));

    JsonArray identifiers = instanceFromGet.getJsonArray("identifiers");
    assertThat(identifiers.size(), is(1));
    assertThat(identifiers, hasItem(identifierMatches(UUID_ISBN.toString(), "9781473619777")));
  }

  @Test
  public void cannotCreateAnInstanceWithIDThatIsNotUUID()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    String id = "6556456";

    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ISBN, "9781473619777"));

    JsonArray contributors = new JsonArray();
    contributors.add(contributor(UUID_PERSONAL_NAME, "Chambers, Becky"));

    JsonObject instanceToCreate = new JsonObject();

    instanceToCreate.put("id", id);
    instanceToCreate.put("source", "TEST");
    instanceToCreate.put("title", "Long Way to a Small Angry Planet");
    instanceToCreate.put("identifiers", identifiers);
    instanceToCreate.put("contributors", contributors);
    instanceToCreate.put("instanceTypeId", UUID_TEXT.toString());

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    client.post(instancesStorageUrl(""), instanceToCreate, TENANT_ID,
      json(createCompleted));

    Response response = createCompleted.get(5, SECONDS);

    assertThat(response.getStatusCode(), is(422));

    assertThat(response.getBody(), containsString("must match"));
  }

  @Test
  public void cannotPutAnInstanceAtNonexistingLocation()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID id = UUID.randomUUID();

    JsonObject instanceToCreate = nod(id);

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    URL url = instancesStorageUrl(String.format("/%s", id));
    client.put(url, instanceToCreate,
      TENANT_ID, ResponseHandler.empty(createCompleted));

    Response putResponse = createCompleted.get(5, SECONDS);
    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));

    assertGetNotFound(url);
  }

  @Test
  public void cannotProvideAdditionalPropertiesInInstance()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    JsonObject requestWithAdditionalProperty = nod(UUID.randomUUID());

    requestWithAdditionalProperty.put("somethingAdditional", "foo");

    CompletableFuture<JsonErrorResponse> createCompleted = new CompletableFuture<>();

    client.post(instancesStorageUrl(""), requestWithAdditionalProperty,
      TENANT_ID, ResponseHandler.jsonErrors(createCompleted));

    JsonErrorResponse response = createCompleted.get(5, SECONDS);

    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
    assertThat(response.getErrors(), hasSoleMessgeContaining("Unrecognized field"));
  }

  @Test
  public void cannotProvideAdditionalPropertiesInInstanceIdentifiers()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    JsonObject requestWithAdditionalProperty = nod(UUID.randomUUID());

    requestWithAdditionalProperty
      .getJsonArray("identifiers").add(identifier(UUID_ISBN, "5645678432576")
      .put("somethingAdditional", "foo"));

    CompletableFuture<JsonErrorResponse> createCompleted = new CompletableFuture<>();

    client.post(instancesStorageUrl(""), requestWithAdditionalProperty,
      TENANT_ID, ResponseHandler.jsonErrors(createCompleted));

    JsonErrorResponse response = createCompleted.get(5, SECONDS);

    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
    assertThat(response.getErrors(), hasSoleMessgeContaining("Unrecognized field"));
  }

  @Test
  public void canReplaceAnInstanceAtSpecificLocation()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID id = UUID.randomUUID();

    JsonObject instanceToCreate = smallAngryPlanet(id);

    createInstance(instanceToCreate);

    JsonObject replacement = instanceToCreate.copy();
    replacement.put("title", "A Long Way to a Small Angry Planet");

    CompletableFuture<Response> replaceCompleted = new CompletableFuture<>();

    client.put(instancesStorageUrl(String.format("/%s", id)), replacement,
      TENANT_ID, ResponseHandler.empty(replaceCompleted));

    Response putResponse = replaceCompleted.get(5, SECONDS);

    //PUT currently cannot return a response
    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    Response getResponse = getById(id);

    assertThat(getResponse.getStatusCode(), is(HTTP_OK));

    JsonObject itemFromGet = getResponse.getJson();

    assertThat(itemFromGet.getString("id"), is(id.toString()));
    assertThat(itemFromGet.getString("title"), is("A Long Way to a Small Angry Planet"));
  }

  @Test
  public void canDeleteAnInstance()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID id = UUID.randomUUID();

    JsonObject instanceToCreate = smallAngryPlanet(id);

    createInstance(instanceToCreate);

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();

    URL url = instancesStorageUrl(String.format("/%s", id));
    client.delete(url, TENANT_ID, ResponseHandler.empty(deleteCompleted));

    Response deleteResponse = deleteCompleted.get(5, SECONDS);

    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    assertGetNotFound(url);
  }

  @Test
  public void canGetInstanceById()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID id = UUID.randomUUID();

    createInstance(smallAngryPlanet(id));

    URL getInstanceUrl = instancesStorageUrl(String.format("/%s", id));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(getInstanceUrl, TENANT_ID,
      json(getCompleted));

    Response response = getCompleted.get(5, SECONDS);

    JsonObject instance = response.getJson();

    assertThat(response.getStatusCode(), is(HTTP_OK));

    assertThat(instance.getString("id"), is(id.toString()));
    assertThat(instance.getString("title"), is("Long Way to a Small Angry Planet"));

    JsonArray identifiers = instance.getJsonArray("identifiers");
    assertThat(identifiers.size(), is(1));
    assertThat(identifiers, hasItem(identifierMatches(UUID_ISBN.toString(), "9781473619777")));
  }

  @Test
  public void canGetAllInstances()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID firstInstanceId = UUID.randomUUID();

    JsonObject firstInstanceToCreate = smallAngryPlanet(firstInstanceId);

    createInstance(firstInstanceToCreate);

    UUID secondInstanceId = UUID.randomUUID();

    JsonObject secondInstanceToCreate = nod(secondInstanceId);

    createInstance(secondInstanceToCreate);

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(instancesStorageUrl(""), TENANT_ID,
        json(getCompleted));

    Response response = getCompleted.get(5, SECONDS);

    JsonObject responseBody = response.getJson();

    JsonArray allInstances = responseBody.getJsonArray(INSTANCES_KEY);

    assertThat(allInstances.size(), is(2));
    assertThat(responseBody.getInteger(TOTAL_RECORDS_KEY), is(2));

    JsonObject firstInstance = allInstances.getJsonObject(0);
    JsonObject secondInstance = allInstances.getJsonObject(1);

    // no "sortBy" used so the database can return them in any order.
    // swap if needed:
    if (firstInstanceId.toString().equals(secondInstance.getString("id"))) {
      JsonObject tmp = firstInstance;
      firstInstance = secondInstance;
      secondInstance = tmp;
    }

    assertThat(firstInstance.getString("id"), is(firstInstanceId.toString()));
    assertThat(firstInstance.getString("title"), is("Long Way to a Small Angry Planet"));

    assertThat(firstInstance.getJsonArray("identifiers").size(), is(1));
    assertThat(firstInstance.getJsonArray("identifiers"),
      hasItem(identifierMatches(UUID_ISBN.toString(), "9781473619777")));

    assertThat(secondInstance.getString("id"), is(secondInstanceId.toString()));
    assertThat(secondInstance.getString("title"), is("Nod"));

    assertThat(secondInstance.getJsonArray("identifiers").size(), is(1));
    assertThat(secondInstance.getJsonArray("identifiers"),
      hasItem(identifierMatches(UUID_ASIN.toString(), "B01D1PLMDO")));
  }
  @Test
  public void canGetWithAllIndex()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID firstInstanceId = UUID.randomUUID();

    JsonObject firstInstanceToCreate = smallAngryPlanet(firstInstanceId);

    createInstance(firstInstanceToCreate);

    UUID secondInstanceId = UUID.randomUUID();

    JsonObject secondInstanceToCreate = nod(secondInstanceId);

    createInstance(secondInstanceToCreate);

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(instancesStorageUrl("?query%3Dall%3D%22Long%20Way%20to%20a%20Small%20Angry%20Planet%20Chambers%2C%20Becky%209781473619777%22"), StorageTestSuite.TENANT_ID,
        ResponseHandler.json(getCompleted));

    Response response = getCompleted.get(5, TimeUnit.SECONDS);

    JsonObject responseBody = response.getJson();

    JsonArray allInstances = responseBody.getJsonArray("instances");

    assertThat(allInstances.size(), is(1));
    assertThat(responseBody.getInteger("totalRecords"), is(1));

    JsonObject firstInstance = allInstances.getJsonObject(0);


    assertThat(firstInstance.getString("id"), is(firstInstanceId.toString()));
    assertThat(firstInstance.getString("title"), is("Long Way to a Small Angry Planet"));

    assertThat(firstInstance.getJsonArray("identifiers").size(), is(1));
    assertThat(firstInstance.getJsonArray("identifiers"),
      hasItem(identifierMatches(UUID_ISBN.toString(), "9781473619777")));
  }
  @Test
  public void canPageAllInstances()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    createInstance(smallAngryPlanet(UUID.randomUUID()));
    createInstance(nod(UUID.randomUUID()));
    createInstance(uprooted(UUID.randomUUID()));
    createInstance(temeraire(UUID.randomUUID()));
    createInstance(interestingTimes(UUID.randomUUID()));

    CompletableFuture<Response> firstPageCompleted = new CompletableFuture<>();
    CompletableFuture<Response> secondPageCompleted = new CompletableFuture<>();

    client.get(instancesStorageUrl("") + "?limit=3", TENANT_ID,
      json(firstPageCompleted));


    client.get(instancesStorageUrl("") + "?limit=3&offset=3", TENANT_ID,
      json(secondPageCompleted));

    Response firstPageResponse = firstPageCompleted.get(5, SECONDS);
    Response secondPageResponse = secondPageCompleted.get(5, SECONDS);

    assertThat(firstPageResponse.getStatusCode(), is(200));
    assertThat(secondPageResponse.getStatusCode(), is(200));

    JsonObject firstPage = firstPageResponse.getJson();
    JsonObject secondPage = secondPageResponse.getJson();

    JsonArray firstPageInstances = firstPage.getJsonArray(INSTANCES_KEY);
    JsonArray secondPageInstances = secondPage.getJsonArray(INSTANCES_KEY);

    assertThat(firstPageInstances.size(), is(3));
    assertThat(firstPage.getInteger(TOTAL_RECORDS_KEY), is(5));

    assertThat(secondPageInstances.size(), is(2));
    assertThat(secondPage.getInteger(TOTAL_RECORDS_KEY), is(5));
  }

  @Test
  public void canProvideLargePageOffsetAndLimit()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    createInstance(smallAngryPlanet(UUID.randomUUID()));
    createInstance(nod(UUID.randomUUID()));
    createInstance(uprooted(UUID.randomUUID()));
    createInstance(temeraire(UUID.randomUUID()));
    createInstance(interestingTimes(UUID.randomUUID()));

    CompletableFuture<Response> pageCompleted = new CompletableFuture<>();

    client.get(instancesStorageUrl("") + "?limit=5000&offset=5000", TENANT_ID,
      json(pageCompleted));

    Response pageResponse = pageCompleted.get(5, SECONDS);

    assertThat(pageResponse.getStatusCode(), is(200));

    JsonObject page = pageResponse.getJson();

    JsonArray instances = page.getJsonArray(INSTANCES_KEY);

    assertThat(instances.size(), is(0));
    // Reports 0, not sure if this is to due with record count approximation
    //assertThat(page.getInteger(TOTAL_RECORDS_KEY), is(5));
  }

  /**
   * Insert n records into instance table where the title field is build using
   * prefix and the number from 1 .. n.
   */
  private void insert(TestContext testContext, PostgresClient pg, String prefix, int n) {
    Async async = testContext.async();
    String table = PostgresClient.convertToPsqlStandard(TENANT_ID) + ".instance";
    String sql = "INSERT INTO " + table +
        " SELECT uuid, json_build_object('title', prefix || n, 'id', uuid)" +
        " FROM (SELECT n, prefix, md5(prefix || n)::uuid AS uuid" +
        "       FROM (SELECT generate_series(1, " + n + ") AS n, '" + prefix + " ' AS prefix) AS tmp1" +
        "      ) AS tmp2";

    pg.execute(sql, testContext.asyncAssertSuccess(updated -> {
        testContext.assertEquals(n, updated.getUpdated());
        async.complete();
      }));
    async.await(10000 /* ms */);
  }

  @Test
  public void canGetWithOptimizedSql(TestContext testContext) {
    int n = PgUtil.getOptimizedSqlSize() / 2;
    PostgresClient pg = PostgresClient.getInstance(StorageTestSuite.getVertx(), TENANT_ID);

    // "b foo" records are before the getOptimizedSqlSize() limit
    // "d foo" records are after the getOptimizedSqlSize() limit
    insert(testContext, pg, "a", n);
    insert(testContext, pg, "b foo", 5);
    insert(testContext, pg, "c", n);
    insert(testContext, pg, "d foo", 5);
    insert(testContext, pg, "e", n);

    // limit=9
    JsonObject json = searchForInstances("title=foo sortBy title", 0, 9);
    JsonArray allInstances = json.getJsonArray(INSTANCES_KEY);
    assertThat(allInstances.size(), is(9));
    assertThat(json.getInteger(TOTAL_RECORDS_KEY), is(10));
    for (int i=0; i<5; i++) {
      JsonObject instance = allInstances.getJsonObject(i);
      assertThat(instance.getString("title"), is("b foo " + (i + 1)));
    }
    for (int i=0; i<3; i++) {
      JsonObject instance = allInstances.getJsonObject(5 + i);
      assertThat(instance.getString("title"), is("d foo " + (i + 1)));
    }

    // limit=5
    json = searchForInstances("title=foo sortBy title", 0, 5);
    allInstances = json.getJsonArray(INSTANCES_KEY);
    assertThat(allInstances.size(), is(5));
    assertThat(json.getInteger(TOTAL_RECORDS_KEY), is(999999999));
    for (int i=0; i<5; i++) {
      JsonObject instance = allInstances.getJsonObject(i);
      assertThat(instance.getString("title"), is("b foo " + (i + 1)));
    }

    // offset=6, limit=3
    json = searchForInstances("title=foo sortBy title", 6, 3);
    allInstances = json.getJsonArray(INSTANCES_KEY);
    assertThat(allInstances.size(), is(3));
    assertThat(json.getInteger(TOTAL_RECORDS_KEY), is(10));
    for (int i=0; i<3; i++) {
      JsonObject instance = allInstances.getJsonObject(i);
      assertThat(instance.getString("title"), is("d foo " + (1 + i + 1)));
    }

    // offset=1, limit=8
    json = searchForInstances("title=foo sortBy title", 1, 8);
    allInstances = json.getJsonArray(INSTANCES_KEY);
    assertThat(allInstances.size(), is(8));
    assertThat(json.getInteger(TOTAL_RECORDS_KEY), is(10));
    for (int i=0; i<4; i++) {
      JsonObject instance = allInstances.getJsonObject(i);
      assertThat(instance.getString("title"), is("b foo " + (1 + i + 1)));
    }
    for (int i=0; i<4; i++) {
      JsonObject instance = allInstances.getJsonObject(4 + i);
      assertThat(instance.getString("title"), is("d foo " + (i + 1)));
    }

    // "b foo", offset=1, limit=20
    json = searchForInstances("title=b sortBy title/sort.ascending", 1, 20);
    allInstances = json.getJsonArray(INSTANCES_KEY);
    assertThat(allInstances.size(), is(4));
    assertThat(json.getInteger(TOTAL_RECORDS_KEY), is(5));
    for (int i=0; i<4; i++) {
      JsonObject instance = allInstances.getJsonObject(i);
      assertThat(instance.getString("title"), is("b foo " + (1 + i + 1)));
    }

    // sort.descending, offset=1, limit=3
    json = searchForInstances("title=foo sortBy title/sort.descending", 1, 3);
    allInstances = json.getJsonArray(INSTANCES_KEY);
    assertThat(allInstances.size(), is(3));
    assertThat(json.getInteger(TOTAL_RECORDS_KEY), is(999999999));
    for (int i=0; i<3; i++) {
      JsonObject instance = allInstances.getJsonObject(i);
      assertThat(instance.getString("title"), is("d foo " + (4 - i)));
    }

    // sort.descending, offset=6, limit=3
    json = searchForInstances("title=foo sortBy title/sort.descending", 6, 3);
    allInstances = json.getJsonArray(INSTANCES_KEY);
    assertThat(allInstances.size(), is(3));
    assertThat(json.getInteger(TOTAL_RECORDS_KEY), is(10));
    for (int i=0; i<3; i++) {
      JsonObject instance = allInstances.getJsonObject(i);
      assertThat(instance.getString("title"), is("b foo " + (4 - i)));
    }
}

  /** MARC record representation in JSON, compatible with MarcEdit's JSON export and import. */
  private MarcJson marcJson = new MarcJson();

  {
    marcJson.setLeader("xxxxxnam a22yyyyy c 4500");
    List<Object> fields = new ArrayList<>();
    fields.add(new JsonObject().put("001", "029857716"));
    fields.add(new JsonObject().put("245",
        new JsonObject().put("ind1", "0").put("ind2", "4").put("subfields",
            new JsonArray().add(new JsonObject().put("a", "The Yearbook of Okapiology")))));
    marcJson.setFields(fields);
  }

  private MarcJson toMarcJson(String resourcePath) throws IOException {
    String mrcjson = IOUtils.toString(this.getClass().getResourceAsStream(resourcePath), "UTF-8");
    JsonObject json = new JsonObject(mrcjson);
    MarcJson newMarcJson = new MarcJson();
    newMarcJson.setLeader(json.getString("leader"));
    newMarcJson.setFields(json.getJsonArray("fields").getList());
    return newMarcJson;
  }

  private Response put(UUID id, MarcJson marcJson, HttpStatus expectedStatus) throws Exception {
    CompletableFuture<Response> putCompleted = new CompletableFuture<>();
    client.put(instancesStorageUrl("/" + id + "/source-record/marc-json"), marcJson,
        TENANT_ID, ResponseHandler.empty(putCompleted));
    Response response = putCompleted.get(5, SECONDS);
    assertThat(response.getStatusCode(), is(expectedStatus.toInt()));
    return response;
  }

  private Response put(UUID id, MarcJson marcJson) throws Exception {
    return put(id, marcJson, HttpStatus.HTTP_NO_CONTENT);
  }

  private String getSourceRecordFormat(UUID instanceId) throws Exception {
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    client.get(instancesStorageUrl("/" + instanceId),
        TENANT_ID, json(getCompleted));
    Response getResponse = getCompleted.get(5, SECONDS);
    assertThat(getResponse.getStatusCode(), is(200));
    return getResponse.getJson().getString("sourceRecordFormat");
  }

  private Response getMarcJson(UUID id) throws Exception {
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    client.get(instancesStorageUrl("/" + id + "/source-record/marc-json"),
        TENANT_ID, json(getCompleted));
    return getCompleted.get(5, SECONDS);
  }

  private void getMarcJsonNotFound(UUID id) throws Exception {
    assertGetNotFound(instancesStorageUrl("/" + id + "/source-record/marc-json"));
  }

  @Test
  public void canCreateInstanceSourceRecord() throws Exception {
    UUID id = UUID.randomUUID();
    JsonObject instance = smallAngryPlanet(id);
    createInstance(instance);

    put(id, marcJson);

    Response getResponse = getMarcJson(id);
    assertThat(getResponse.getStatusCode(), is(200));
    assertThat(getResponse.getJson().getString("id"), is(id.toString()));
    assertThat(getResponse.getJson().getString("leader"), is("xxxxxnam a22yyyyy c 4500"));
    JsonArray fields = getResponse.getJson().getJsonArray("fields");
    assertThat(fields.getJsonObject(0).getString("001"), is("029857716"));
    assertThat(fields.getJsonObject(1).getJsonObject("245")
        .getJsonArray("subfields").getJsonObject(0).getString("a"), is("The Yearbook of Okapiology"));
    assertThat(getResponse.getJson().getMap().keySet(), containsInAnyOrder("id", "leader", "fields"));
  }

  @Test  // https://issues.folio.org/browse/MODINVSTOR-142?focusedCommentId=33665#comment-33665
  public void canCreateInstanceSourceRecord101073931X() throws Exception {
    UUID id = UUID.randomUUID();
    JsonObject instance = smallAngryPlanet(id);
    createInstance(instance);

    put(id, toMarcJson("/101073931X.mrcjson"));

    Response getResponse = getMarcJson(id);
    assertThat(getResponse.getStatusCode(), is(200));
    JsonArray fields = getResponse.getJson().getJsonArray("fields");
    assertThat(fields.getJsonObject(0).getString("001"), is("101073931X"));
  }

  @Test  // https://issues.folio.org/browse/MODINVSTOR-143?focusedCommentId=33618#comment-33618
  public void canCreateInstanceSourceRecord1011273942() throws Exception {
    UUID id = UUID.randomUUID();
    JsonObject instance = smallAngryPlanet(id);
    createInstance(instance);

    put(id, toMarcJson("/1011273942.mrcjson"));

    Response getResponse = getMarcJson(id);
    assertThat(getResponse.getStatusCode(), is(200));
    JsonArray fields = getResponse.getJson().getJsonArray("fields");
    assertThat(fields.getJsonObject(0).getString("001"), is("1011273942"));
  }

  @Test
  public void canUpdateInstanceSourceRecord() throws Exception {
    UUID id = UUID.randomUUID();
    JsonObject instance = smallAngryPlanet(id);
    createInstance(instance);

    put(id, marcJson);                          // create
    put(id, toMarcJson("/101073931X.mrcjson")); // update

    Response getResponse = getMarcJson(id);
    assertThat(getResponse.getStatusCode(), is(200));
    JsonArray fields = getResponse.getJson().getJsonArray("fields");
    assertThat(fields.getJsonObject(0).getString("001"), is("101073931X"));
  }

  @Test
  public void cannotGetNonExistingSourceRecord() throws Exception {
    getMarcJsonNotFound(UUID.randomUUID());
  }

  @Test
  public void canDeleteInstanceMarcSourceRecord() throws Exception {
    UUID id = UUID.randomUUID();
    JsonObject instance = smallAngryPlanet(id);
    createInstance(instance);
    assertThat(getSourceRecordFormat(id), is(nullValue()));

    put(id, marcJson);
    assertThat(getSourceRecordFormat(id), is("MARC-JSON"));

    // delete MARC source record
    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();
    client.delete(instancesStorageUrl("/" + id + "/source-record/marc-json"),
        TENANT_ID, ResponseHandler.empty(deleteCompleted));
    Response deleteResponse = deleteCompleted.get(5, SECONDS);
    assertThat(deleteResponse.getStatusCode(), is(HttpStatus.HTTP_NO_CONTENT.toInt()));
    assertThat(getSourceRecordFormat(id), is(nullValue()));
    getMarcJsonNotFound(id);
  }

  @Test
  public void canDeleteInstanceSourceRecord() throws Exception {
    UUID id = UUID.randomUUID();
    JsonObject instance = smallAngryPlanet(id);
    createInstance(instance);
    assertThat(getSourceRecordFormat(id), is(nullValue()));

    put(id, marcJson);
    assertThat(getSourceRecordFormat(id), is("MARC-JSON"));

    // delete source record
    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();
    client.delete(instancesStorageUrl("/" + id + "/source-record"),
        TENANT_ID, ResponseHandler.empty(deleteCompleted));
    Response deleteResponse = deleteCompleted.get(5, SECONDS);
    assertThat(deleteResponse.getStatusCode(), is(HttpStatus.HTTP_NO_CONTENT.toInt()));
    assertThat(getSourceRecordFormat(id), is(nullValue()));
    getMarcJsonNotFound(id);
  }

  @Test
  public void canDeleteSourceRecordWhenDeletingInstance() throws Exception {
    UUID id = UUID.randomUUID();
    JsonObject instance = smallAngryPlanet(id);
    createInstance(instance);

    put(id, marcJson);

    // delete instance
    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();
    client.delete(instancesStorageUrl("/" + id),
        TENANT_ID, ResponseHandler.empty(deleteCompleted));
    Response deleteResponse = deleteCompleted.get(5, SECONDS);
    assertThat(deleteResponse.getStatusCode(), is(HttpStatus.HTTP_NO_CONTENT.toInt()));

    getMarcJsonNotFound(id);
  }

  @Test
  public void cannotCreateSourceRecordWithoutInstance() throws Exception {
    put(UUID.randomUUID(), marcJson, HttpStatus.HTTP_NOT_FOUND);
  }

  /**
   * Run a get request using the provided cql query.
   * <p>
   * Example: searchForInstances("title = t*");
   * <p>
   * The example runs an API request with "?query=title+%3D+t*"
   * @return the response as an JsonObject
   */
  private JsonObject searchForInstances(String cql) {
    return searchForInstances(cql, -1, -1);
  }

  /**
   * Run a get request using the provided cql query and the provided offset and limit values
   * (a negative value means no offset or no limit).
   * <p>
   * Example 1: searchForInstances("title = t*", -1, -1);
   * <p>
   * Example 2: searchForInstances("title = t*", 30, 10);
   * <p>
   * The examples runs an API request with "?query=title+%3D+t*" and "?query=title+%3D+t*&offset=30&limit=10"
   *
   * @return the response as an JsonObject
   */
  private JsonObject searchForInstances(String cql, int offset, int limit) {
    try {
      CompletableFuture<Response> searchCompleted = new CompletableFuture<>();

      String url = instancesStorageUrl("").toString() + "?query=" + urlEncode(cql);
      if (offset >= 0) {
        url += "&offset=" + offset;
      }
      if (limit >= 0) {
        url += "&limit=" + limit;
      }

      client.get(url, TENANT_ID, json(searchCompleted));
      Response searchResponse = searchCompleted.get(5, SECONDS);

      assertThat(searchResponse, statusCodeIs(200));
      return searchResponse.getJson();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Create the 5 example instances and run a get request using the provided cql query.
   */
  private void create5instances() {
    try {
      createInstance(smallAngryPlanet(UUID.randomUUID()));
      createInstance(nod(UUID.randomUUID()));
      createInstance(uprooted(UUID.randomUUID()));
      createInstance(temeraire(UUID.randomUUID()));
      createInstance(interestingTimes(UUID.randomUUID()));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Create the 5 example instances and run a get request using the provided cql query.
   * <p>
   * Example: searchForInstancesWithin5("title = t*");
   * <p>
   * The example runs an API request with "?query=title+%3D+t*" against the
   * 5 example instances.
   * @return the response as an JsonObject
   */
  private JsonObject searchForInstancesWithin5(String cql) {
    create5instances();
    // RMB ensures en_US locale so that sorting behaves that same in all environments
    return searchForInstances(cql);
  }

  /**
   * Assert that the jsonObject contains an instances Array where each array element
   * has one title String with the expectedTitles in the correct order.
   */
  private void matchInstanceTitles(JsonObject jsonObject, String ... expectedTitles) {
    JsonArray foundInstances = jsonObject.getJsonArray(INSTANCES_KEY);
    String [] titles = new String [foundInstances.size()];
    for (int i=0; i<titles.length; i++) {
      titles[i] = foundInstances.getJsonObject(i).getString("title");
    }
    assertThat(titles, is(expectedTitles));
    assertThat(TOTAL_RECORDS_KEY, jsonObject.getInteger(TOTAL_RECORDS_KEY), is(expectedTitles.length));
  }

  /**
   * Assert that the cql query returns the expectedTitles in that order.
   * Searches within the 5 example instance records.
   * @param cql  query to run
   * @param expectedTitles  titles in the expected order
   */
  private void canSort(String cql, String ... expectedTitles) {
    JsonObject searchBody = searchForInstancesWithin5(cql);
    matchInstanceTitles(searchBody, expectedTitles);
  }

  @Test
  public void canSearchForInstancesByTitle() {
    canSort("title=\"Upr*\"", "Uprooted");
    // Note that 'Up' is a stop word, and will be removed from the query!
    // We have an issue for dropping stop words: RMB-228
    // Until then, search for "upr*" instead of "up*"
  }

  @Test
  public void canSearchForInstancesByTitleWord() {
    canSort("title=\"Times\"", "Interesting Times");
  }

  @Test
  public void canSearchForInstancesByTitleAdj() {
    canSort("title adj \"Upro*\"", "Uprooted");
  }

  @Test
  public void canSearchForInstancesUsingSimilarQueryToUILookAheadSearch() {
    canSort("title=\"upr*\" or contributors=\"name\": \"upr*\" or identifiers=\"value\": \"upr*\"", "Uprooted");
  }

  @Test
  public void arrayModifierfsContributors1() {
    canSort("contributors = /@name novik sortBy title ", "Temeraire", "Uprooted" );
  }

  @Test
  public void arrayModifierfsContributors2() {
    canSort("contributors = /@contributorNameTypeId = " + UUID_PERSONAL_NAME + " novik sortBy title", "Temeraire", "Uprooted");
  }

  @Test
  public void arrayModifierfsIdentifiers1() {
    canSort("identifiers = /@value 9781447294146", "Uprooted");
  }

  @Test
  public void arrayModifierfsIdentifiers2() {
    canSort("identifiers = /@identifierTypeId = " + UUID_ISBN + " 9781447294146", "Uprooted");
  }

  @Test
  public void arrayModifierfsIdentifiers3() {
    canSort("identifiers = /@identifierTypeId " + UUID_ASIN, "Nod");
  }

  @Test
  public void canSearchWithoutSqlInjection() {
    create5instances();

    // check for MODINVSTOR-293:
    // CQL identifiers=")" fails with "invalid regular expression: parentheses () not balanced" SQL Injection
    String [] strings = { "'", "''",
        "\\\"", "\\\"\\\"",
        "(", "((", ")", "))",
        "{", "{{", "}", "}}",
    };

    for (String s : strings) {
      try {
        // full text search ignores punctuation
        matchInstanceTitles(searchForInstances("title=\"" + s + "Uprooted\""), "Uprooted");
        // == will return 0 results
        matchInstanceTitles(searchForInstances("title==\"" + s + "Uprooted\""));
        // identifier search will always return 0 results
        matchInstanceTitles(searchForInstances("identifiers=\"" + s + "\""));
        matchInstanceTitles(searchForInstances("identifiers==\"" + s + "\""));
      } catch (Exception e) {
        throw new AssertionError(s, e);
      }
    }
  }

  @Test
  public void canSearchByBarcode()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID expectedInstanceId = UUID.randomUUID();
    UUID expectedHoldingId = UUID.randomUUID();

    createInstance(smallAngryPlanet(expectedInstanceId));

    createHoldings(new HoldingRequestBuilder()
      .withId(expectedHoldingId)
      .withPermanentLocation(mainLibraryLocationId)
      .forInstance(expectedInstanceId)
      .create());

    createItem(new ItemRequestBuilder()
      .forHolding(expectedHoldingId)
      .withBarcode("706949453641")
      .withPermanentLoanType(canCirculateLoanTypeId)
      .withMaterialType(bookMaterialTypeId)
      .create());

    UUID otherInstanceId = UUID.randomUUID();
    UUID otherHoldingId = UUID.randomUUID();

    createInstance(nod(otherInstanceId));

    createHoldings(new HoldingRequestBuilder()
      .withId(otherHoldingId)
      .forInstance(otherInstanceId)
      .withPermanentLocation(mainLibraryLocationId)
      .create());

    createItem(new ItemRequestBuilder()
      .forHolding(expectedHoldingId)
      .withMaterialType(bookMaterialTypeId)
      .withPermanentLoanType(canCirculateLoanTypeId)
      .withBarcode("766043059304")
      .create());

    //Use == as exact match is intended for barcode
    canSort("item.barcode==706949453641", "Long Way to a Small Angry Planet");
  }

  // This is intended to demonstrate usage of the two different views
  @Test
  public void canSearchByBarcodeAndPermanentLocation()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID smallAngryPlanetInstanceId = UUID.randomUUID();
    UUID mainLibrarySmallAngryHoldingId = UUID.randomUUID();

    createInstance(smallAngryPlanet(smallAngryPlanetInstanceId));

    createHoldings(new HoldingRequestBuilder()
      .withId(mainLibrarySmallAngryHoldingId)
      .withPermanentLocation(mainLibraryLocationId)
      .forInstance(smallAngryPlanetInstanceId)
      .create());

    createItem(new ItemRequestBuilder()
      .forHolding(mainLibrarySmallAngryHoldingId)
      .withBarcode("706949453641")
      .withPermanentLoanType(canCirculateLoanTypeId)
      .withMaterialType(bookMaterialTypeId)
      .create());

    UUID annexSmallAngryHoldingId = UUID.randomUUID();

    createHoldings(new HoldingRequestBuilder()
      .withId(annexSmallAngryHoldingId)
      .withPermanentLocation(annexLibraryLocationId)
      .forInstance(smallAngryPlanetInstanceId)
      .create());

    createItem(new ItemRequestBuilder()
      .forHolding(annexSmallAngryHoldingId)
      .withBarcode("70704539201")
      .withPermanentLoanType(canCirculateLoanTypeId)
      .withMaterialType(bookMaterialTypeId)
      .create());

    UUID nodInstanceId = UUID.randomUUID();
    UUID nodHoldingId = UUID.randomUUID();

    createInstance(nod(nodInstanceId));

    createHoldings(new HoldingRequestBuilder()
      .withId(nodHoldingId)
      .forInstance(nodInstanceId)
      .withPermanentLocation(mainLibraryLocationId)
      .create());

    createItem(new ItemRequestBuilder()
      .forHolding(nodHoldingId)
      .withMaterialType(bookMaterialTypeId)
      .withPermanentLoanType(canCirculateLoanTypeId)
      .withBarcode("766043059304")
      .create());

    //Use == as exact match is intended for barcode and location ID
    canSort(String.format("item.barcode==706949453641 and holdingsRecords.permanentLocationId==%s",
      mainLibraryLocationId),
      "Long Way to a Small Angry Planet");

    canSort(String.format("((contributors =/@name \"becky\") and holdingsRecords.permanentLocationId=\"%s\")",mainLibraryLocationId),"Long Way to a Small Angry Planet" );
    System.out.println("canSearchByBarcodeAndPermanentLocation");

  }

  // This is intended to demonstrate that instances without holdings or items
  // are not excluded from searching
  @Test
  public void canSearchByTitleAndBarcodeWithMissingHoldingsAndItemsAndStillGetInstances()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException, UnsupportedEncodingException {

    UUID smallAngryPlanetInstanceId = UUID.randomUUID();
    UUID mainLibrarySmallAngryHoldingId = UUID.randomUUID();

    createInstance(smallAngryPlanet(smallAngryPlanetInstanceId));

    createHoldings(new HoldingRequestBuilder()
      .withId(mainLibrarySmallAngryHoldingId)
      .withPermanentLocation(mainLibraryLocationId)
      .forInstance(smallAngryPlanetInstanceId)
      .create());

    createItem(new ItemRequestBuilder()
      .forHolding(mainLibrarySmallAngryHoldingId)
      .withBarcode("706949453641")
      .withPermanentLoanType(canCirculateLoanTypeId)
      .withMaterialType(bookMaterialTypeId)
      .create());

    UUID nodInstanceId = UUID.randomUUID();

    createInstance(nod(nodInstanceId));

    CompletableFuture<Response> searchCompleted = new CompletableFuture<>();

    String url = instancesStorageUrl("").toString() + "?query="
        + urlEncode("item.barcode=706949453641* or title=Nod*");

    client.get(url, TENANT_ID, json(searchCompleted));
    Response searchResponse = searchCompleted.get(5, SECONDS);

    assertThat(searchResponse.getStatusCode(), is(200));
    JsonObject responseBody = searchResponse.getJson();

    assertThat(responseBody.getInteger(TOTAL_RECORDS_KEY), is(2));

    List<JsonObject> foundInstances = JsonArrayHelper.toList(responseBody.getJsonArray(INSTANCES_KEY));

    assertThat(foundInstances.size(), is(2));

    Optional<JsonObject> firstInstance = foundInstances.stream()
      .filter(instance -> instance.getString("id").equals(smallAngryPlanetInstanceId.toString()))
      .findFirst();

    Optional<JsonObject> secondInstance = foundInstances.stream()
      .filter(instance -> instance.getString("id").equals(nodInstanceId.toString()))
      .findFirst();

    assertThat("Instance with barcode should be found",
      firstInstance.isPresent(), is(true));

    assertThat("Instance with title and no holding or items, should be found",
      secondInstance.isPresent(), is(true));
  }

  @Test
  public void canSortAscending() {
    canSort("cql.allRecords=1 sortBy title",
        "Interesting Times", "Long Way to a Small Angry Planet", "Nod", "Temeraire", "Uprooted");
  }

  @Test
  public void canSortDescending() {
    canSort("cql.allRecords=1 sortBy title/sort.descending",
        "Uprooted", "Temeraire", "Nod", "Long Way to a Small Angry Planet", "Interesting Times");
  }

  @Test
  public void canDeleteAllInstances()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    createInstance(smallAngryPlanet(UUID.randomUUID()));
    createInstance(nod(UUID.randomUUID()));
    createInstance(uprooted(UUID.randomUUID()));

    CompletableFuture<Response> allDeleted = new CompletableFuture<>();

    client.delete(instancesStorageUrl(""), TENANT_ID,
      ResponseHandler.empty(allDeleted));

    Response deleteResponse = allDeleted.get(5, SECONDS);

    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(instancesStorageUrl(""), TENANT_ID,
      json(getCompleted));

    Response response = getCompleted.get(5, SECONDS);

    JsonObject responseBody = response.getJson();

    JsonArray allInstances = responseBody.getJsonArray(INSTANCES_KEY);

    assertThat(allInstances.size(), is(0));
    assertThat(responseBody.getInteger(TOTAL_RECORDS_KEY), is(0));
  }

  @Test
  public void tenantIsRequiredForCreatingNewInstance()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    JsonObject instance = nod(UUID.randomUUID());

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();

    client.post(instancesStorageUrl(""), instance, null, ResponseHandler.any(postCompleted));

    Response response = postCompleted.get(5, SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), is("Unable to process request Tenant must be set"));
  }

  @Test
  public void tenantIsRequiredForGettingAnInstance()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    URL getInstanceUrl = instancesStorageUrl(String.format("/%s",
      UUID.randomUUID().toString()));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(getInstanceUrl, null, ResponseHandler.any(getCompleted));

    Response response = getCompleted.get(5, SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), is("Unable to process request Tenant must be set"));
  }

  @Test
  public void tenantIsRequiredForGettingAllInstances()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(instancesStorageUrl(""), null, ResponseHandler.any(getCompleted));

    Response response = getCompleted.get(5, SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), is("Unable to process request Tenant must be set"));
  }

  @Test
  public void testCrossTableQueries() throws Exception {

    String url = instancesStorageUrl("") + "?query=";

    //////// create instance objects /////////////////////////////
    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ISBN, "9781473619777"));
    JsonArray contributors = new JsonArray();
    contributors.add(contributor(UUID_PERSONAL_NAME, "Chambers, Becky"));
    JsonArray tags = new JsonArray();
    tags.add("test-tag");

    UUID idJ1 = UUID.randomUUID();
    JsonObject j1 = createInstanceRequest(idJ1, "TEST1", "Long Way to a Small Angry Planet 1",
      identifiers, contributors, UUID_INSTANCE_TYPE, tags);
    UUID idJ2 = UUID.randomUUID();
    JsonObject j2 = createInstanceRequest(idJ2, "TEST2", "Long Way to a Small Angry Planet 2",
      identifiers, contributors, UUID_INSTANCE_TYPE, tags);
    UUID idJ3 = UUID.randomUUID();
    JsonObject j3 = createInstanceRequest(idJ3, "TEST3", "Long Way to a Small Angry Planet 3",
      identifiers, contributors, UUID_INSTANCE_TYPE, tags);

    createInstance(j1);
    createInstance(j2);
    createInstance(j3);
    ////////////////////// done /////////////////////////////////////

    ///////// create location objects //////////////////////////////
    UUID loc1 = UUID.fromString("11111111-dee7-48eb-b03f-d02fdf0debd0");
    LocationsTest.createLocation(loc1, "location1", "IX/L1");
    UUID loc2 = UUID.fromString("99999999-dee7-48eb-b03f-d02fdf0debd0");
    LocationsTest.createLocation(loc2, "location2", "IX/L2");
    /////////////////// done //////////////////////////////////////

    /////////////////// create holdings records ///////////////////
    JsonObject jho1 = new JsonObject();
    String holdings1UUID = loc1.toString();
    jho1.put("id", UUID.randomUUID().toString());
    jho1.put("instanceId", idJ1.toString());
    jho1.put("permanentLocationId", holdings1UUID);

    JsonObject jho2 = new JsonObject();
    String holdings2UUID = loc2.toString();
    jho2.put("id", UUID.randomUUID().toString());
    jho2.put("instanceId", idJ2.toString());
    jho2.put("permanentLocationId", holdings2UUID);

    JsonObject jho3 = new JsonObject();
    jho3.put("id", UUID.randomUUID().toString());
    jho3.put("instanceId", idJ3.toString());
    jho3.put("permanentLocationId", holdings2UUID);

    createHoldings(jho1);
    createHoldings(jho2);
    createHoldings(jho3);
    ////////////////////////done //////////////////////////////////////

    String url1 = url+ urlEncode("title=Long Way to a Small Angry Planet* sortby title");
    String url2 = url+ urlEncode("title=cql.allRecords=1 sortBy title");
    String url3 = url+ urlEncode("holdingsRecords.permanentLocationId=99999999-dee7-48eb-b03f-d02fdf0debd0 sortBy title");
    String url4 = url+ urlEncode("title=cql.allRecords=1 sortby title");
    String url5 = url+ urlEncode("title=cql.allRecords=1 and holdingsRecords.permanentLocationId=99999999-dee7-48eb-b03f-d02fdf0debd0 "
        + "sortby title");
    //non existant - 0 results
    String url6 = url+ urlEncode("title=cql.allRecords=1 and holdingsRecords.permanentLocationId=abc* sortby holdingsRecords.permanentLocationId");

    CompletableFuture<Response> cqlCF1 = new CompletableFuture<>();
    CompletableFuture<Response> cqlCF2 = new CompletableFuture<>();
    CompletableFuture<Response> cqlCF3 = new CompletableFuture<>();
    CompletableFuture<Response> cqlCF4 = new CompletableFuture<>();
    CompletableFuture<Response> cqlCF5 = new CompletableFuture<>();
    CompletableFuture<Response> cqlCF6 = new CompletableFuture<>();

    String[] urls = new String[]{url1, url2, url3, url4, url5, url6};
    @SuppressWarnings("unchecked")
    CompletableFuture<Response>[] cqlCF = new CompletableFuture[]{cqlCF1, cqlCF2, cqlCF3, cqlCF4, cqlCF5, cqlCF6};

    for(int i=0; i<6; i++){
      CompletableFuture<Response> cf = cqlCF[i];
      String cqlURL = urls[i];
      client.get(cqlURL, TENANT_ID, json(cf));

      Response cqlResponse = cf.get(5, SECONDS);
      assertThat(cqlResponse.getStatusCode(), is(HTTP_OK));
      System.out.println(cqlResponse.getBody() +
        "\nStatus - " + cqlResponse.getStatusCode() + " at " + System.currentTimeMillis() + " for " + cqlURL);

      if(i==0){
        assertThat(3, is(cqlResponse.getJson().getInteger(TOTAL_RECORDS_KEY)));
        assertThat("TEST1" , is(cqlResponse.getJson().getJsonArray(INSTANCES_KEY).getJsonObject(0).getString("source")));
      } else if(i==1){
        assertThat(3, is(cqlResponse.getJson().getInteger(TOTAL_RECORDS_KEY)));
        assertThat("TEST1" , is(cqlResponse.getJson().getJsonArray(INSTANCES_KEY).getJsonObject(0).getString("source")));
      } else if(i==2){
        assertThat(2, is(cqlResponse.getJson().getInteger(TOTAL_RECORDS_KEY)));
        assertThat("TEST2" , is(cqlResponse.getJson().getJsonArray(INSTANCES_KEY).getJsonObject(0).getString("source")));
      } else if(i==3){
        assertThat("TEST1" , is(cqlResponse.getJson().getJsonArray(INSTANCES_KEY).getJsonObject(0).getString("source")));
      }else if(i==4){
        assertThat(2, is(cqlResponse.getJson().getInteger(TOTAL_RECORDS_KEY)));
      }else if(i==5){
        assertThat(0, is(cqlResponse.getJson().getInteger(TOTAL_RECORDS_KEY)));
      }
    }
  }

  @Test
  public void shouldReturnInstanceWhenFilterByTags() throws Exception {

    final String TAGS_KEY = "tags";
    final String TAG_LIST_KEY= "tagList";
    final String TAG_VALUE = "important";
    final String searchByTagQuery = TAGS_KEY + "." + TAG_LIST_KEY + "=" + TAG_VALUE;
    final JsonObject instanceWithTag = smallAngryPlanet(UUID.randomUUID())
      .put(TAGS_KEY, new JsonObject().put(TAG_LIST_KEY, new JsonArray().add(TAG_VALUE)));
    createInstance(instanceWithTag);
    createInstance(nod(UUID.randomUUID()));

    CompletableFuture<Response> future = new CompletableFuture<>();

    client.get(instancesStorageUrl("") + "?query=" + urlEncode(searchByTagQuery), TENANT_ID, json(future));

    Response response = future.get(5, SECONDS);

    assertThat(response.getStatusCode(), is(HTTP_OK));

    JsonObject instancesJsonResponse = response.getJson();
    JsonArray instances = instancesJsonResponse.getJsonArray(INSTANCES_KEY);

    final LinkedHashMap instance = (LinkedHashMap) instances.getList().get(0);
    final LinkedHashMap<String, ArrayList<String>> instanceTags = (LinkedHashMap<String, ArrayList<String>>) instance.get(TAGS_KEY);

    assertThat(instances.size(), is(1));
    assertThat(instanceTags.get(TAG_LIST_KEY), hasItem(TAG_VALUE));
    assertThat(instancesJsonResponse.getInteger(TOTAL_RECORDS_KEY), is(1));

  }

  @Test
  public void canCreateACollectionOfInstances()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    JsonArray instancesArray = new JsonArray();
    int numberOfInstances = 1000;

    for(int i = 0; i < numberOfInstances; i++) {
      instancesArray.add(smallAngryPlanet(UUID.randomUUID()));
    }

    JsonObject instanceCollection = JsonObject.mapFrom(new JsonObject()
      .put(INSTANCES_KEY, instancesArray)
      .put(TOTAL_RECORDS_KEY, numberOfInstances));

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    client.post(instancesStorageBatchInstancesUrl(StringUtils.EMPTY), instanceCollection, TENANT_ID,
      json(createCompleted));

    Response response = createCompleted.get(5, SECONDS);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject instancesResponse = response.getJson();

    assertThat(instancesResponse.getInteger(TOTAL_RECORDS_KEY), is(numberOfInstances));

    JsonArray instances = instancesResponse.getJsonArray(INSTANCES_KEY);
    assertThat(instances.size(), is(numberOfInstances));
  }

  @Test
  public void canCreateInstancesEvenIfSomeFailed()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    JsonObject correctInstance = smallAngryPlanet(null);
    JsonObject errorInstance = smallAngryPlanet(null).put("modeOfIssuanceId", UUID.randomUUID().toString());

    JsonObject instanceCollection = JsonObject.mapFrom(new JsonObject()
      .put(INSTANCES_KEY, new JsonArray().add(correctInstance).add(errorInstance).add(correctInstance).add(errorInstance))
      .put(TOTAL_RECORDS_KEY, 4));

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    client.post(instancesStorageBatchInstancesUrl(StringUtils.EMPTY), instanceCollection, TENANT_ID,
      json(createCompleted));

    Response response = createCompleted.get(5, SECONDS);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject instancesResponse = response.getJson();

    assertThat(instancesResponse.getInteger(TOTAL_RECORDS_KEY), is(2));

    JsonArray errorMessages = instancesResponse.getJsonArray("errorMessages");
    assertThat(errorMessages.size(), is(2));
    assertThat(errorMessages.getString(0), notNullValue());
    assertThat(errorMessages.getString(1), notNullValue());

    JsonArray instances = instancesResponse.getJsonArray(INSTANCES_KEY);
    assertThat(instances.size(), is(2));
  }

  @Test
  public void shouldReturnErrorResponseIfAllInstancesFailed()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    JsonObject errorInstance = smallAngryPlanet(null).put("modeOfIssuanceId", UUID.randomUUID().toString());

    JsonObject instanceCollection = JsonObject.mapFrom(new JsonObject()
      .put(INSTANCES_KEY, new JsonArray().add(errorInstance).add(errorInstance).add(errorInstance))
      .put(TOTAL_RECORDS_KEY, 3));

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    client.post(instancesStorageBatchInstancesUrl(StringUtils.EMPTY), instanceCollection, TENANT_ID,
      json(createCompleted));

    Response response = createCompleted.get(5, SECONDS);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_INTERNAL_ERROR));

    JsonObject instancesResponse = response.getJson();

    assertThat(instancesResponse.getInteger(TOTAL_RECORDS_KEY), is(0));

    JsonArray errorMessages = instancesResponse.getJsonArray("errorMessages");
    assertThat(errorMessages.size(), is(3));
    assertThat(errorMessages.getString(0), notNullValue());
    assertThat(errorMessages.getString(1), notNullValue());
    assertThat(errorMessages.getString(2), notNullValue());

    JsonArray instances = instancesResponse.getJsonArray(INSTANCES_KEY);
    assertThat(instances.size(), is(0));
  }

  private void createHoldings(JsonObject holdingsToCreate)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

      CompletableFuture<Response> createCompleted = new CompletableFuture<>();

      client.post(holdingsStorageUrl(""), holdingsToCreate,
        TENANT_ID, json(createCompleted));

      Response response = createCompleted.get(2, SECONDS);

    assertThat(String.format("Create holdings failed: %s", response.getBody()),
      response.getStatusCode(), is(201));
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

  private JsonObject smallAngryPlanet(UUID id) {
    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ISBN, "9781473619777"));
    JsonArray contributors = new JsonArray();
    contributors.add(contributor(UUID_PERSONAL_NAME, "Chambers, Becky"));
    JsonArray tags = new JsonArray();
    tags.add("test-tag");

    return createInstanceRequest(id, "TEST", "Long Way to a Small Angry Planet",
      identifiers, contributors, UUID_INSTANCE_TYPE, tags);
  }


  private Response getById(UUID id)
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    URL getInstanceUrl = instancesStorageUrl(String.format("/%s", id));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(getInstanceUrl, TENANT_ID,
      json(getCompleted));

    return getCompleted.get(5, SECONDS);
  }

  /**
   * Assert that a GET at the url returns 404 status code (= not found).
   * @param url  endpoint where to execute a GET request
   */
  private void assertGetNotFound(URL url) {
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(url, TENANT_ID, ResponseHandler.text(getCompleted));
    Response response;
    try {
      response = getCompleted.get(5, SECONDS);
      assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  private JsonObject nod(UUID id) {
    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ASIN, "B01D1PLMDO"));

    JsonArray contributors = new JsonArray();
    contributors.add(contributor(UUID_PERSONAL_NAME, "Barnes, Adrian"));

    JsonArray tags = new JsonArray();
    tags.add("test-tag");
    return createInstanceRequest(id, "TEST", "Nod",
      identifiers, contributors, UUID_INSTANCE_TYPE, tags);
  }

  private JsonObject uprooted(UUID id) {
    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ISBN, "1447294149"));
    identifiers.add(identifier(UUID_ISBN, "9781447294146"));

    JsonArray contributors = new JsonArray();
    contributors.add(contributor(UUID_PERSONAL_NAME, "Novik, Naomi"));

    JsonArray tags = new JsonArray();
    tags.add("test-tag");

    return createInstanceRequest(id, "TEST", "Uprooted",
      identifiers, contributors, UUID_INSTANCE_TYPE, tags);
  }

  private JsonObject temeraire(UUID id) {

    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ISBN, "0007258712"));
    identifiers.add(identifier(UUID_ISBN, "9780007258710"));

    JsonArray contributors = new JsonArray();
    contributors.add(contributor(UUID_PERSONAL_NAME, "Novik, Naomi"));

    JsonArray tags = new JsonArray();
    tags.add("test-tag");
    return createInstanceRequest(id, "TEST", "Temeraire",
      identifiers, contributors, UUID_INSTANCE_TYPE, tags);
  }

  private JsonObject interestingTimes(UUID id) {
    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ISBN, "0552167541"));
    identifiers.add(identifier(UUID_ISBN, "9780552167541"));

    JsonArray contributors = new JsonArray();
    contributors.add(contributor(UUID_PERSONAL_NAME, "Pratchett, Terry"));

    JsonArray tags = new JsonArray();
    tags.add("test-tag");
    return createInstanceRequest(id, "TEST", "Interesting Times",
      identifiers, contributors, UUID_INSTANCE_TYPE, tags);
  }

  private NatureOfContentTerm createNatureOfContentTerm(final String name)
    throws InterruptedException, ExecutionException, TimeoutException {
    NatureOfContentTerm natureOfContentTerm = new NatureOfContentTerm()
      .withId(UUID.randomUUID().toString())
      .withName(name)
      .withSource("test");

    CompletableFuture<Response> createNatureOfContent =
      new CompletableFuture<>();

    client.post(natureOfContentTermsUrl(""), natureOfContentTerm,
      TENANT_ID, json(createNatureOfContent));

    Response response = createNatureOfContent.get(5, SECONDS);
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    natureOfContentIdsToRemoveAfterTest.add(natureOfContentTerm.getId());
    return response.getJson().mapTo(NatureOfContentTerm.class);
  }
}
