package org.folio.rest.api;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.rest.support.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.folio.rest.support.JsonObjectMatchers.hasSoleMessgeContaining;
import static org.folio.rest.support.JsonObjectMatchers.identifierMatches;
import static org.folio.rest.support.http.InterfaceUrls.*;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;

public class InstanceStorageTest extends TestBase {
  @Before
  public void beforeEach() throws MalformedURLException {
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
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    UUID id = UUID.randomUUID();

    JsonObject instanceToCreate = smallAngryPlanet(id);

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    client.post(instancesStorageUrl(""), instanceToCreate, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject instance = response.getJson();

    assertThat(instance.getString("id"), is(id.toString()));
    assertThat(instance.getString("title"), is("Long Way to a Small Angry Planet"));

    JsonArray identifiers = instance.getJsonArray("identifiers");
    assertThat(identifiers.size(), is(1));
    assertThat(identifiers, hasItem(identifierMatches("isbn", "9781473619777")));

    JsonResponse getResponse = getById(id);

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
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    JsonObject instanceToCreate = smallAngryPlanet(null);

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    client.post(instancesStorageUrl(""), instanceToCreate, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject instance = response.getJson();

    String newId = instance.getString("id");

    assertThat(newId, is(notNullValue()));

    JsonResponse getResponse = getById(UUID.fromString(newId));

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
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

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

    CompletableFuture<TextResponse> createCompleted = new CompletableFuture<>();

    client.post(instancesStorageUrl(""), instanceToCreate, StorageTestSuite.TENANT_ID,
      ResponseHandler.text(createCompleted));

    TextResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));

    assertThat(response.getBody(), is("ID must be a UUID"));
  }

  @Test
  public void canCreateAnItemAtSpecificLocation()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    UUID id = UUID.randomUUID();

    JsonObject instanceToCreate = nod(id);

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    client.put(instancesStorageUrl(String.format("/%s", id)), instanceToCreate,
      StorageTestSuite.TENANT_ID, ResponseHandler.empty(createCompleted));

    Response putResponse = createCompleted.get(5, TimeUnit.SECONDS);

    //PUT currently cannot return a response
    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    JsonResponse getResponse = getById(id);

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
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

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

    JsonResponse getResponse = getById(id);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject itemFromGet = getResponse.getJson();

    assertThat(itemFromGet.getString("id"), is(id.toString()));
    assertThat(itemFromGet.getString("title"), is("A Long Way to a Small Angry Planet"));
  }

  @Test
  public void canDeleteAnInstance() throws InterruptedException,
    MalformedURLException, TimeoutException, ExecutionException {

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
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    UUID id = UUID.randomUUID();

    createInstance(smallAngryPlanet(id));

    URL getInstanceUrl = instancesStorageUrl(String.format("/%s", id));

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();

    client.get(getInstanceUrl, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    JsonResponse response = getCompleted.get(5, TimeUnit.SECONDS);

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

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();

    client.get(instancesStorageUrl(""), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    JsonResponse response = getCompleted.get(5, TimeUnit.SECONDS);

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

    CompletableFuture<JsonResponse> firstPageCompleted = new CompletableFuture<>();
    CompletableFuture<JsonResponse> secondPageCompleted = new CompletableFuture<>();

    client.get(instancesStorageUrl("") + "?limit=3", StorageTestSuite.TENANT_ID,
      ResponseHandler.json(firstPageCompleted));

    client.get(instancesStorageUrl("") + "?limit=3&offset=3", StorageTestSuite.TENANT_ID,
      ResponseHandler.json(secondPageCompleted));

    JsonResponse firstPageResponse = firstPageCompleted.get(5, TimeUnit.SECONDS);
    JsonResponse secondPageResponse = secondPageCompleted.get(5, TimeUnit.SECONDS);

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

      CompletableFuture<JsonResponse> searchCompleted = new CompletableFuture<JsonResponse>();

      String url = instancesStorageUrl("").toString() + "?query="
          + URLEncoder.encode(cql, StandardCharsets.UTF_8.name());

      client.get(url, StorageTestSuite.TENANT_ID, ResponseHandler.json(searchCompleted));
      JsonResponse searchResponse = searchCompleted.get(5, TimeUnit.SECONDS);
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

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();

    client.get(instancesStorageUrl(""), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    JsonResponse response = getCompleted.get(5, TimeUnit.SECONDS);

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

    CompletableFuture<TextResponse> postCompleted = new CompletableFuture<>();

    client.post(instancesStorageUrl(""), instance,
      ResponseHandler.text(postCompleted));

    TextResponse response = postCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), is("Unable to process request Tenant must be set"));
  }

  @Test
  public void tenantIsRequiredForGettingAnInstance()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    URL getInstanceUrl = instancesStorageUrl(String.format("/%s",
      UUID.randomUUID().toString()));

    CompletableFuture<TextResponse> getCompleted = new CompletableFuture<>();

    client.get(getInstanceUrl, ResponseHandler.text(getCompleted));

    TextResponse response = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), is("Unable to process request Tenant must be set"));
  }

  @Test
  public void tenantIsRequiredForGettingAllInstances()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    CompletableFuture<TextResponse> getCompleted = new CompletableFuture<>();

    client.get(instancesStorageUrl(""), ResponseHandler.text(getCompleted));

    TextResponse response = getCompleted.get(5, TimeUnit.SECONDS);

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
    ShelfLocationsTest.createShelfLocation(loc1, "location1");
    UUID loc2 = UUID.fromString("99999999-dee7-48eb-b03f-d02fdf0debd0");
    ShelfLocationsTest.createShelfLocation(loc2, "location2");
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

    CompletableFuture<JsonResponse> cqlCF1 = new CompletableFuture<>();
    CompletableFuture<JsonResponse> cqlCF2 = new CompletableFuture<>();
    CompletableFuture<JsonResponse> cqlCF3 = new CompletableFuture<>();
    CompletableFuture<JsonResponse> cqlCF4 = new CompletableFuture<>();
    CompletableFuture<JsonResponse> cqlCF5 = new CompletableFuture<>();
    CompletableFuture<JsonResponse> cqlCF6 = new CompletableFuture<>();

    String[] urls = new String[]{url1, url2, url3, url4, url5, url6};
    @SuppressWarnings("unchecked")
    CompletableFuture<JsonResponse>[] cqlCF = new CompletableFuture[]{cqlCF1, cqlCF2, cqlCF3, cqlCF4, cqlCF5, cqlCF6};

    for(int i=0; i<6; i++){
      CompletableFuture<JsonResponse> cf = cqlCF[i];
      String cqlURL = urls[i];
      client.get(cqlURL, StorageTestSuite.TENANT_ID, ResponseHandler.json(cf));

      JsonResponse cqlResponse = cf.get(5, TimeUnit.SECONDS);
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

  private void createHoldings(JsonObject holdingsToCreate) {

      CompletableFuture<TextResponse> createCompleted = new CompletableFuture<>();

      try {
        client.post(holdingsStorageUrl(""), holdingsToCreate,
          StorageTestSuite.TENANT_ID, ResponseHandler.text(createCompleted));

        TextResponse response = createCompleted.get(2, TimeUnit.SECONDS);

        if (response.getStatusCode() != 201) {
          System.out.println("WARNING!!!!! Create holdings preparation failed: "
            + response.getBody());
        }
      }
      catch(Exception e) {
        System.out.println("WARNING!!!!! Create holdings preparation failed: "
          + e.getMessage());
      }
    }

  private void createInstance(JsonObject instanceToCreate) {
    CompletableFuture<TextResponse> createCompleted = new CompletableFuture<>();

    try {

      client.post(instancesStorageUrl(""), instanceToCreate,
        StorageTestSuite.TENANT_ID, ResponseHandler.text(createCompleted));

      TextResponse response = createCompleted.get(2, TimeUnit.SECONDS);

      if (response.getStatusCode() != 201) {
        System.out.println("WARNING!!!!! Create instance preparation failed: "
          + response.getBody());
      }
    }
    catch(Exception e) {
      System.out.println("WARNING!!!!! Create instance preparation failed: "
        + e.getMessage());
    }
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

  private JsonResponse getById(UUID id)
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    URL getInstanceUrl = instancesStorageUrl(String.format("/%s", id));

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();

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
}
