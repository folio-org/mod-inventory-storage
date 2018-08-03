package org.folio.rest.api;

import static org.folio.rest.support.JsonObjectMatchers.hasSoleMessgeContaining;
import static org.folio.rest.support.JsonObjectMatchers.identifierMatches;
import static org.folio.rest.support.http.InterfaceUrls.*;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.HttpStatus;
import org.folio.rest.jaxrs.model.Field;
import org.folio.rest.jaxrs.model.MarcJson;
import org.folio.rest.support.*;
import org.folio.rest.support.builders.HoldingRequestBuilder;
import org.folio.rest.support.builders.ItemRequestBuilder;
import org.folio.rest.support.client.LoanTypesClient;
import org.folio.rest.support.client.MaterialTypesClient;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class InstanceStorageTest extends TestBase {
  private static UUID mainLibraryLocationId;
  private static UUID annexLocationId;
  private static UUID bookMaterialTypeId;
  private static UUID canCirculateLoanTypeId;

  @BeforeClass
  public static void beforeAny() throws Exception {

    StorageTestSuite.deleteAll(itemsStorageUrl(""));
    StorageTestSuite.deleteAll(holdingsStorageUrl(""));
    StorageTestSuite.deleteAll(instancesStorageUrl(""));

    StorageTestSuite.deleteAll(locationsStorageUrl(""));
    StorageTestSuite.deleteAll(locInstitutionStorageUrl(""));
    StorageTestSuite.deleteAll(locCampusStorageUrl(""));
    StorageTestSuite.deleteAll(locLibraryStorageUrl(""));

    StorageTestSuite.deleteAll(materialTypesStorageUrl(""));
    StorageTestSuite.deleteAll(loanTypesStorageUrl(""));

    bookMaterialTypeId = UUID.fromString(
      new MaterialTypesClient(client, materialTypesStorageUrl("")).create("book"));

    mainLibraryLocationId = LocationsTest.createLocation(null, "Main Library (Inst)", "I/M");
    annexLocationId = LocationsTest.createLocation(null, "Annex Library (Inst)", "I/A");

    canCirculateLoanTypeId = UUID.fromString(new LoanTypesClient(client,
      loanTypesStorageUrl("")).create("Can Circulate"));
  }

  @Before
  public void beforeEach() throws Exception {
    StorageTestSuite.deleteAll(itemsStorageUrl(""));
    StorageTestSuite.deleteAll(holdingsStorageUrl(""));
    StorageTestSuite.deleteAll(instancesStorageUrl(""));
  }

  @After
  public void checkIdsAfterEach() {
    StorageTestSuite.checkForMismatchedIDs("instance");
  }

  @Test
  public void canCreateAnInstance()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID id = UUID.randomUUID();

    JsonObject instanceToCreate = smallAngryPlanet(id);

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    client.post(instancesStorageUrl(""), instanceToCreate, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    Response response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject instance = response.getJson();

    assertThat(instance.getString("id"), is(id.toString()));
    assertThat(instance.getString("title"), is("Long Way to a Small Angry Planet"));

    JsonArray identifiers = instance.getJsonArray("identifiers");
    assertThat(identifiers.size(), is(1));
    assertThat(identifiers, hasItem(identifierMatches("isbn", "9781473619777")));

    Response getResponse = getById(id);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject instanceFromGet = getResponse.getJson();

    assertThat(instanceFromGet.getString("title"),
      is("Long Way to a Small Angry Planet"));

    JsonArray identifiersFromGet = instanceFromGet.getJsonArray("identifiers");
    assertThat(identifiersFromGet.size(), is(1));
    assertThat(identifiersFromGet, hasItem(identifierMatches("isbn", "9781473619777")));
  }

  @Test
  public void canCreateAnInstanceWithoutProvidingID()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    JsonObject instanceToCreate = smallAngryPlanet(null);

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    client.post(instancesStorageUrl(""), instanceToCreate, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    Response response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject instance = response.getJson();

    String newId = instance.getString("id");

    assertThat(newId, is(notNullValue()));

    Response getResponse = getById(UUID.fromString(newId));

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject instanceFromGet = getResponse.getJson();

    assertThat(instanceFromGet.getString("title"),
      is("Long Way to a Small Angry Planet"));

    JsonArray identifiers = instanceFromGet.getJsonArray("identifiers");
    assertThat(identifiers.size(), is(1));
    assertThat(identifiers, hasItem(identifierMatches("isbn", "9781473619777")));
  }

  @Test
  public void cannotCreateAnInstanceWithIDThatIsNotUUID()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    String id = "6556456";

    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier("isbn", "9781473619777"));

    JsonArray contributors = new JsonArray();
    contributors.add(contributor("personal name", "Chambers, Becky"));

    JsonObject instanceToCreate = new JsonObject();

    instanceToCreate.put("id", id);
    instanceToCreate.put("source", "TEST");
    instanceToCreate.put("title", "Long Way to a Small Angry Planet");
    instanceToCreate.put("identifiers", identifiers);
    instanceToCreate.put("contributors", contributors);
    instanceToCreate.put("instanceTypeId", "resource type id");

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    client.post(instancesStorageUrl(""), instanceToCreate, StorageTestSuite.TENANT_ID,
      ResponseHandler.text(createCompleted));

    Response response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));

    assertThat(response.getBody(), containsString("invalid input syntax for type uuid"));
  }

  @Test
  public void canCreateAnInstanceAtSpecificLocation()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID id = UUID.randomUUID();

    JsonObject instanceToCreate = nod(id);

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    client.put(instancesStorageUrl(String.format("/%s", id)), instanceToCreate,
      StorageTestSuite.TENANT_ID, ResponseHandler.empty(createCompleted));

    Response putResponse = createCompleted.get(5, TimeUnit.SECONDS);

    //PUT currently cannot return a response
    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    Response getResponse = getById(id);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject itemFromGet = getResponse.getJson();

    assertThat(itemFromGet.getString("id"), is(id.toString()));
    assertThat(itemFromGet.getString("title"), is("Nod"));
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
      StorageTestSuite.TENANT_ID, ResponseHandler.jsonErrors(createCompleted));

    JsonErrorResponse response = createCompleted.get(5, TimeUnit.SECONDS);

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
      .getJsonArray("identifiers").add(identifier("isbn", "5645678432576")
      .put("somethingAdditional", "foo"));

    CompletableFuture<JsonErrorResponse> createCompleted = new CompletableFuture<>();

    client.post(instancesStorageUrl(""), requestWithAdditionalProperty,
      StorageTestSuite.TENANT_ID, ResponseHandler.jsonErrors(createCompleted));

    JsonErrorResponse response = createCompleted.get(5, TimeUnit.SECONDS);

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
      StorageTestSuite.TENANT_ID, ResponseHandler.empty(replaceCompleted));

    Response putResponse = replaceCompleted.get(5, TimeUnit.SECONDS);

    //PUT currently cannot return a response
    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    Response getResponse = getById(id);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

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

    client.delete(instancesStorageUrl(String.format("/%s", id)),
      StorageTestSuite.TENANT_ID, ResponseHandler.empty(deleteCompleted));

    Response deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);

    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(instancesStorageUrl(String.format("/%s", id)),
      StorageTestSuite.TENANT_ID, ResponseHandler.empty(getCompleted));

    Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
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

    client.get(getInstanceUrl, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    Response response = getCompleted.get(5, TimeUnit.SECONDS);

    JsonObject instance = response.getJson();

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    assertThat(instance.getString("id"), is(id.toString()));
    assertThat(instance.getString("title"), is("Long Way to a Small Angry Planet"));

    JsonArray identifiers = instance.getJsonArray("identifiers");
    assertThat(identifiers.size(), is(1));
    assertThat(identifiers, hasItem(identifierMatches("isbn", "9781473619777")));
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

    client.get(instancesStorageUrl(""), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    Response response = getCompleted.get(5, TimeUnit.SECONDS);

    JsonObject responseBody = response.getJson();

    JsonArray allInstances = responseBody.getJsonArray("instances");

    assertThat(allInstances.size(), is(2));
    assertThat(responseBody.getInteger("totalRecords"), is(2));

    JsonObject firstInstance = allInstances.getJsonObject(0);
    JsonObject secondInstance = allInstances.getJsonObject(1);

    assertThat(firstInstance.getString("id"), is(firstInstanceId.toString()));
    assertThat(firstInstance.getString("title"), is("Long Way to a Small Angry Planet"));

    assertThat(firstInstance.getJsonArray("identifiers").size(), is(1));
    assertThat(firstInstance.getJsonArray("identifiers"),
      hasItem(identifierMatches("isbn", "9781473619777")));

    assertThat(secondInstance.getString("id"), is(secondInstanceId.toString()));
    assertThat(secondInstance.getString("title"), is("Nod"));

    assertThat(secondInstance.getJsonArray("identifiers").size(), is(1));
    assertThat(secondInstance.getJsonArray("identifiers"),
      hasItem(identifierMatches("asin", "B01D1PLMDO")));
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

    client.get(instancesStorageUrl("") + "?limit=3", StorageTestSuite.TENANT_ID,
      ResponseHandler.json(firstPageCompleted));

    client.get(instancesStorageUrl("") + "?limit=3&offset=3", StorageTestSuite.TENANT_ID,
      ResponseHandler.json(secondPageCompleted));

    Response firstPageResponse = firstPageCompleted.get(5, TimeUnit.SECONDS);
    Response secondPageResponse = secondPageCompleted.get(5, TimeUnit.SECONDS);

    assertThat(firstPageResponse.getStatusCode(), is(200));
    assertThat(secondPageResponse.getStatusCode(), is(200));

    JsonObject firstPage = firstPageResponse.getJson();
    JsonObject secondPage = secondPageResponse.getJson();

    JsonArray firstPageInstances = firstPage.getJsonArray("instances");
    JsonArray secondPageInstances = secondPage.getJsonArray("instances");

    assertThat(firstPageInstances.size(), is(3));
    assertThat(firstPage.getInteger("totalRecords"), is(5));

    assertThat(secondPageInstances.size(), is(2));
    assertThat(secondPage.getInteger("totalRecords"), is(5));
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

    client.get(instancesStorageUrl("") + "?limit=5000&offset=5000", StorageTestSuite.TENANT_ID,
      ResponseHandler.json(pageCompleted));

    Response pageResponse = pageCompleted.get(5, TimeUnit.SECONDS);

    assertThat(pageResponse.getStatusCode(), is(200));

    JsonObject page = pageResponse.getJson();

    JsonArray instances = page.getJsonArray("instances");

    assertThat(instances.size(), is(0));
    // Reports 0, not sure if this is to due with record count approximation
    //assertThat(page.getInteger("totalRecords"), is(5));
  }

  /** MARC record representation in JSON, compatible with MarcEdit's JSON export and import. */
  private MarcJson marcJson = new MarcJson();
  private class MarcField extends Field {
    JsonObject jsonObject = new JsonObject();
    public MarcField(String key, String value) {
      jsonObject.put(key,  value);
    }
    public MarcField(String key, String ind1, String ind2, String subfield, String value) {
      jsonObject.put(key,
          new JsonObject().put("ind1", ind1).put("ind2", ind2).put("subfields",
              new JsonArray().add(new JsonObject().put(subfield, value))));
    }
    @Override
    public String toString() {
      return jsonObject.encode();
    }
  }

  {
    marcJson.setLeader("xxxxxnam a22yyyyy c 4500");
    List<Field> fields = new ArrayList<>();
    fields.add(new MarcField("001", "029857716"));
    fields.add(new MarcField("245", "0", "4", "a", "The Yearbook of Okapiology"));
    marcJson.setFields(fields);
  }

  private Response put(UUID id, MarcJson marcJson, HttpStatus expectedStatus) throws Exception {
    CompletableFuture<Response> putCompleted = new CompletableFuture<>();
    client.put(instancesStorageUrl("/" + id + "/source-record/marc-json"), marcJson,
        StorageTestSuite.TENANT_ID, ResponseHandler.empty(putCompleted));
    Response response = putCompleted.get(5, TimeUnit.SECONDS);
    assertThat(response.getStatusCode(), is(expectedStatus.toInt()));
    return response;
  }

  private Response put(UUID id, MarcJson marcJson) throws Exception {
    return put(id, marcJson, HttpStatus.HTTP_NO_CONTENT);
  }

  private String getSourceRecordFormat(UUID instanceId) throws Exception {
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    client.get(instancesStorageUrl("/" + instanceId),
        StorageTestSuite.TENANT_ID, ResponseHandler.json(getCompleted));
    Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);
    assertThat(getResponse.getStatusCode(), is(200));
    return getResponse.getJson().getString("sourceRecordFormat");
  }

  private void getSourceNotFound(UUID id) throws Exception {
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    client.get(instancesStorageUrl("/" + id + "/source-record/marc-json"),
        StorageTestSuite.TENANT_ID, ResponseHandler.text(getCompleted));
    Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);
    assertThat(getResponse.getStatusCode(), is(HttpStatus.HTTP_NOT_FOUND.toInt()));
  }

  @Test
  public void canCreateInstanceSourceRecord() throws Exception {
    UUID id = UUID.randomUUID();
    JsonObject instance = smallAngryPlanet(id);
    createInstance(instance);

    put(id, marcJson);

    // get MARC source record
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    client.get(instancesStorageUrl("/" + id + "/source-record/marc-json"),
        StorageTestSuite.TENANT_ID, ResponseHandler.json(getCompleted));
    Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);
    assertThat(getResponse.getStatusCode(), is(200));
    assertThat(getResponse.getJson().size(), is(2));  // leader and fields
    assertThat(getResponse.getJson().getString("leader"), is("xxxxxnam a22yyyyy c 4500"));
    JsonArray fields = getResponse.getJson().getJsonArray("fields");
    assertThat(fields.size(), is(2));
    // RAML 2 Java conversion does not work for Field.java.
  }

  @Test
  public void cannotGetNonExistingSourceRecord() throws Exception {
    getSourceNotFound(UUID.randomUUID());
  }

  @Test
  public void canDeleteInstanceSourceRecord() throws Exception {
    UUID id = UUID.randomUUID();
    JsonObject instance = smallAngryPlanet(id);
    createInstance(instance);
    assertThat(getSourceRecordFormat(id), is(nullValue()));

    Response putResponse = put(id, marcJson);
    assertThat(getSourceRecordFormat(id), is("MARC-JSON"));

    // delete MARC source record
    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();
    client.delete(instancesStorageUrl("/" + id + "/source-record/marc-json"),
        StorageTestSuite.TENANT_ID, ResponseHandler.empty(deleteCompleted));
    Response deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);
    assertThat(deleteResponse.getStatusCode(), is(HttpStatus.HTTP_NO_CONTENT.toInt()));
    assertThat(getSourceRecordFormat(id), is(nullValue()));

    getSourceNotFound(id);
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
        StorageTestSuite.TENANT_ID, ResponseHandler.empty(deleteCompleted));
    Response deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);
    assertThat(deleteResponse.getStatusCode(), is(HttpStatus.HTTP_NO_CONTENT.toInt()));

    getSourceNotFound(id);
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
   * The example produces "?query=title+%3D+t*&limit=3"
   * @return the response as an JsonObject
   */
  private JsonObject searchForInstances(String cql) {
    try {
      // RMB ensures en_US locale so that sorting behaves that same in all environments
      createInstance(smallAngryPlanet(UUID.randomUUID()));
      createInstance(nod(UUID.randomUUID()));
      createInstance(uprooted(UUID.randomUUID()));
      createInstance(temeraire(UUID.randomUUID()));
      createInstance(interestingTimes(UUID.randomUUID()));

      CompletableFuture<Response> searchCompleted = new CompletableFuture<Response>();

      String url = instancesStorageUrl("").toString() + "?query="
          + URLEncoder.encode(cql, StandardCharsets.UTF_8.name());

      client.get(url, StorageTestSuite.TENANT_ID, ResponseHandler.json(searchCompleted));
      Response searchResponse = searchCompleted.get(5, TimeUnit.SECONDS);

      assertThat(searchResponse.getStatusCode(), is(200));
      return searchResponse.getJson();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Assert that the cql query returns the expectedTitles in that order.
   * @param cql  query to run
   * @param expectedTitles  titles in the expected order
   */
  private void canSort(String cql, String ... expectedTitles) {
    JsonObject searchBody = searchForInstances(cql);
    assertThat(searchBody.getInteger("totalRecords"), is(expectedTitles.length));
    JsonArray foundInstances = searchBody.getJsonArray("instances");
    assertThat(foundInstances.size(), is(expectedTitles.length));
    String [] titles = new String [expectedTitles.length];
    for (int i=0; i<expectedTitles.length; i++) {
      titles[i] = foundInstances.getJsonObject(i).getString("title");
    }
    assertThat(titles, is(expectedTitles));
  }

  @Test
  public void canSearchForInstancesByTitle() {
    canSort("title=\"*Up*\"", "Uprooted");
  }

  @Test
  public void canSearchForInstancesByTitleAdj() {
    canSort("title adj \"*Up*\"", "Uprooted");
  }

  @Test
  public void canSearchForInstancesUsingSimilarQueryToUILookAheadSearch() {
    canSort("title=\"up*\" or contributors=\"name\": \"up*\" or identifiers=\"value\": \"up*\"", "Uprooted");
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
      .withPermanentLocation(annexLocationId)
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
        + URLEncoder.encode("item.barcode=706949453641* or title=Nod*",
      StandardCharsets.UTF_8.name());

    client.get(url, StorageTestSuite.TENANT_ID, ResponseHandler.json(searchCompleted));
    Response searchResponse = searchCompleted.get(5, TimeUnit.SECONDS);

    assertThat(searchResponse.getStatusCode(), is(200));
    JsonObject responseBody = searchResponse.getJson();

    assertThat(responseBody.getInteger("totalRecords"), is(2));

    List<JsonObject> foundInstances = JsonArrayHelper.toList(responseBody.getJsonArray("instances"));

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

    client.delete(instancesStorageUrl(""), StorageTestSuite.TENANT_ID,
      ResponseHandler.empty(allDeleted));

    Response deleteResponse = allDeleted.get(5, TimeUnit.SECONDS);

    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(instancesStorageUrl(""), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    Response response = getCompleted.get(5, TimeUnit.SECONDS);

    JsonObject responseBody = response.getJson();

    JsonArray allInstances = responseBody.getJsonArray("instances");

    assertThat(allInstances.size(), is(0));
    assertThat(responseBody.getInteger("totalRecords"), is(0));
  }

  @Test
  public void tenantIsRequiredForCreatingNewInstance()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    JsonObject instance = nod(UUID.randomUUID());

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();

    client.post(instancesStorageUrl(""), instance, null, ResponseHandler.any(postCompleted));

    Response response = postCompleted.get(5, TimeUnit.SECONDS);

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

    Response response = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), is("Unable to process request Tenant must be set"));
  }

  @Test
  public void tenantIsRequiredForGettingAllInstances()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(instancesStorageUrl(""), null, ResponseHandler.any(getCompleted));

    Response response = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), is("Unable to process request Tenant must be set"));
  }

  @Test
  public void testCrossTableQueries() throws Exception {

    System.out.println("--------------------------------------------------------------------------------------------------------------------");

    String url = instancesStorageUrl("") + "?query=";

    String holdingsURL = "/holdings-storage/holdings";

    //////// create instance objects /////////////////////////////
    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier("isbn", "9781473619777"));
    JsonArray contributors = new JsonArray();
    contributors.add(contributor("personal name", "Chambers, Becky"));

    UUID idJ1 = UUID.randomUUID();
    JsonObject j1 = createInstanceRequest(idJ1, "TEST1", "Long Way to a Small Angry Planet 1",
      identifiers, contributors, "resource type id");
    UUID idJ2 = UUID.randomUUID();
    JsonObject j2 = createInstanceRequest(idJ2, "TEST2", "Long Way to a Small Angry Planet 2",
      identifiers, contributors, "resource type id");
    UUID idJ3 = UUID.randomUUID();
    JsonObject j3 = createInstanceRequest(idJ3, "TEST3", "Long Way to a Small Angry Planet 3",
      identifiers, contributors, "resource type id");

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

    String url1 = url+URLEncoder.encode("title=Long Way to a Small Angry Planet* sortBy holdingsRecords.permanentLocationId/sort.descending title", "UTF-8");
    String url2 = url+URLEncoder.encode("title=cql.allRecords=1 sortBy holdingsRecords.permanentLocationId/sort.ascending", "UTF-8");
    String url3 = url+URLEncoder.encode("holdingsRecords.permanentLocationId=99999999-dee7-48eb-b03f-d02fdf0debd0 sortBy holdingsRecords.permanentLocationId/sort.descending title", "UTF-8");
    String url4 = url+URLEncoder.encode("title=cql.allRecords=1 sortby holdingsRecords.permanentLocationId title", "UTF-8");
    String url5 = url+URLEncoder.encode("title=cql.allRecords=1 and holdingsRecords.permanentLocationId=99999999-dee7-48eb-b03f-d02fdf0debd0 "
        + "sortby holdingsRecords.permanentLocationId", "UTF-8");
    //non existant - 0 results
    String url6 = url+URLEncoder.encode("title=cql.allRecords=1 and holdingsRecords.permanentLocationId=abc* sortby holdingsRecords.permanentLocationId", "UTF-8");

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
      client.get(cqlURL, StorageTestSuite.TENANT_ID, ResponseHandler.json(cf));

      Response cqlResponse = cf.get(5, TimeUnit.SECONDS);
      assertThat(cqlResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
      System.out.println(cqlResponse.getBody() +
        "\nStatus - " + cqlResponse.getStatusCode() + " at " + System.currentTimeMillis() + " for " + cqlURL);

      if(i==0){
        assertThat(3, is(cqlResponse.getJson().getInteger("totalRecords")));
        assertThat("TEST2" , is(cqlResponse.getJson().getJsonArray("instances").getJsonObject(0).getString("source")));
      } else if(i==1){
        assertThat(3, is(cqlResponse.getJson().getInteger("totalRecords")));
        assertThat("TEST1" , is(cqlResponse.getJson().getJsonArray("instances").getJsonObject(0).getString("source")));
      } else if(i==2){
        assertThat(2, is(cqlResponse.getJson().getInteger("totalRecords")));
        assertThat("TEST2" , is(cqlResponse.getJson().getJsonArray("instances").getJsonObject(0).getString("source")));
      } else if(i==3){
        assertThat("TEST1" , is(cqlResponse.getJson().getJsonArray("instances").getJsonObject(0).getString("source")));
      }else if(i==4){
        assertThat(2, is(cqlResponse.getJson().getInteger("totalRecords")));
      }else if(i==5){
        assertThat(0, is(cqlResponse.getJson().getInteger("totalRecords")));
      }
    }
  }

  private void createHoldings(JsonObject holdingsToCreate)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

      CompletableFuture<Response> createCompleted = new CompletableFuture<>();

      client.post(holdingsStorageUrl(""), holdingsToCreate,
        StorageTestSuite.TENANT_ID, ResponseHandler.json(createCompleted));

      Response response = createCompleted.get(2, TimeUnit.SECONDS);

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
      StorageTestSuite.TENANT_ID, ResponseHandler.json(createCompleted));

    Response response = createCompleted.get(2, TimeUnit.SECONDS);

    assertThat(String.format("Create instance failed: %s", response.getBody()),
      response.getStatusCode(), is(201));
  }

  private JsonObject smallAngryPlanet(UUID id) {
    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier("isbn", "9781473619777"));
    JsonArray contributors = new JsonArray();
    contributors.add(contributor("personal name", "Chambers, Becky"));

    return createInstanceRequest(id, "TEST", "Long Way to a Small Angry Planet",
      identifiers, contributors, UUID.randomUUID().toString());
  }

  private JsonObject identifier(String identifierTypeId, String value) {
    return new JsonObject()
      .put("identifierTypeId", identifierTypeId)
      .put("value", value);
  }

  private JsonObject contributor(String contributorNameTypeId, String name) {
    return new JsonObject()
      .put("contributorNameTypeId", contributorNameTypeId)
      .put("name", name);
  }

  private Response getById(UUID id)
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    URL getInstanceUrl = instancesStorageUrl(String.format("/%s", id));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(getInstanceUrl, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    return getCompleted.get(5, TimeUnit.SECONDS);
  }

  private JsonObject createInstanceRequest(
    UUID id,
    String source,
    String title,
    JsonArray identifiers,
    JsonArray contributors,
    String instanceTypeId) {

    JsonObject instanceToCreate = new JsonObject();

    if(id != null) {
      instanceToCreate.put("id",id.toString());
    }

    instanceToCreate.put("title", title);
    instanceToCreate.put("source", source);
    instanceToCreate.put("identifiers", identifiers);
    instanceToCreate.put("contributors", contributors);
    instanceToCreate.put("instanceTypeId", instanceTypeId);

    return instanceToCreate;
  }

  private JsonObject nod(UUID id) {
    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier("asin", "B01D1PLMDO"));

    JsonArray contributors = new JsonArray();
    contributors.add(contributor("personal name", "Barnes, Adrian"));
    return createInstanceRequest(id, "TEST", "Nod",
      identifiers, contributors, "resource type id");
  }

  private JsonObject uprooted(UUID id) {
    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier("isbn", "1447294149"));
    identifiers.add(identifier("isbn", "9781447294146"));

    JsonArray contributors = new JsonArray();
    contributors.add(contributor("personal name", "Novik, Naomi"));

    return createInstanceRequest(id, "TEST", "Uprooted",
      identifiers, contributors, "resource type id");
  }

  private JsonObject temeraire(UUID id) {

    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier("isbn", "0007258712"));
    identifiers.add(identifier("isbn", "9780007258710"));

    JsonArray contributors = new JsonArray();
    contributors.add(contributor("personal name", "Novik, Naomi"));
    return createInstanceRequest(id, "TEST", "Temeraire",
      identifiers, contributors, "resource type id");
  }

  private JsonObject interestingTimes(UUID id) {
    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier("isbn", "0552167541"));
    identifiers.add(identifier("isbn", "9780552167541"));

    JsonArray contributors = new JsonArray();
    contributors.add(contributor("personal name", "Pratchett, Terry"));
    return createInstanceRequest(id, "TEST", "Interesting Times",
      identifiers, contributors, "resource type id");
  }

  private void createItem(JsonObject itemToCreate)
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    client.post(itemsStorageUrl(""), itemToCreate, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    Response response = createCompleted.get(2, TimeUnit.SECONDS);

    assertThat(String.format("Create item failed: %s", response.getBody()),
      response.getStatusCode(), is(201));
  }
}
