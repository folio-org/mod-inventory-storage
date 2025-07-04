package org.folio.rest.api;

import static org.folio.rest.support.http.InterfaceUrls.alternativeTitleTypesUrl;
import static org.folio.rest.support.http.InterfaceUrls.callNumberTypesUrl;
import static org.folio.rest.support.http.InterfaceUrls.classificationTypesUrl;
import static org.folio.rest.support.http.InterfaceUrls.contributorNameTypesUrl;
import static org.folio.rest.support.http.InterfaceUrls.contributorTypesUrl;
import static org.folio.rest.support.http.InterfaceUrls.electronicAccessRelationshipsUrl;
import static org.folio.rest.support.http.InterfaceUrls.holdingsNoteTypesUrl;
import static org.folio.rest.support.http.InterfaceUrls.holdingsTypesUrl;
import static org.folio.rest.support.http.InterfaceUrls.identifierTypesUrl;
import static org.folio.rest.support.http.InterfaceUrls.illPoliciesUrl;
import static org.folio.rest.support.http.InterfaceUrls.instanceFormatsUrl;
import static org.folio.rest.support.http.InterfaceUrls.instanceNoteTypesUrl;
import static org.folio.rest.support.http.InterfaceUrls.instanceStatusesUrl;
import static org.folio.rest.support.http.InterfaceUrls.instanceTypesUrl;
import static org.folio.rest.support.http.InterfaceUrls.itemNoteTypesUrl;
import static org.folio.rest.support.http.InterfaceUrls.natureOfContentTermsUrl;
import static org.folio.rest.support.http.InterfaceUrls.statisticalCodeTypesUrl;
import static org.folio.rest.support.http.InterfaceUrls.statisticalCodesUrl;
import static org.folio.utility.ModuleUtility.getClient;
import static org.folio.utility.ModuleUtility.vertxUrl;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import junitparams.JUnitParamsRunner;
import lombok.SneakyThrows;
import org.folio.rest.api.entities.ElectronicAccessRelationship;
import org.folio.rest.api.entities.HoldingsNoteType;
import org.folio.rest.api.entities.HoldingsType;
import org.folio.rest.api.entities.IllPolicy;
import org.folio.rest.api.entities.InstanceNoteType;
import org.folio.rest.api.entities.InstanceStatus;
import org.folio.rest.api.entities.ItemNoteType;
import org.folio.rest.api.entities.JsonEntity;
import org.folio.rest.api.entities.StatisticalCode;
import org.folio.rest.api.entities.StatisticalCodeType;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.utility.ModuleUtility;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class ReferenceTablesTest extends TestBase {

  @SneakyThrows
  @Before
  public void beforeEach() {
    clearData();
    removeAllEvents();
  }

  @Test
  public void alternativeTitleTypesLoaded()
    throws InterruptedException, TimeoutException, ExecutionException {
    URL apiUrl = alternativeTitleTypesUrl("");
    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("alternative title types", searchResponse, 11, 40);
  }

  @Test
  public void callNumberTypesLoaded()
    throws InterruptedException, TimeoutException, ExecutionException {
    URL apiUrl = callNumberTypesUrl("");
    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("call number types", searchResponse, 5, 40);
  }

  @Test
  public void classificationTypesLoaded()
    throws InterruptedException, TimeoutException, ExecutionException {
    URL apiUrl = classificationTypesUrl("");
    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("classification types", searchResponse, 2, 20);
  }

  @Test
  public void contributorNameTypesLoaded()
    throws InterruptedException, TimeoutException, ExecutionException {
    URL apiUrl = contributorNameTypesUrl("");

    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("contributor name types", searchResponse, 3, 10);
  }

  @Test
  public void contributorTypesLoaded()
    throws InterruptedException, TimeoutException, ExecutionException {
    URL apiUrl = contributorTypesUrl("");

    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("contributor types", searchResponse, 20, 500);
  }

  @Test
  public void electronicAccessRelationshipsLoaded()
    throws InterruptedException, TimeoutException, ExecutionException {
    URL apiUrl = electronicAccessRelationshipsUrl("");

    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("electronic access relationship types", searchResponse, 2, 20);
  }

  @Test
  public void electronicAccessRelationshipsBasicCrud()
    throws InterruptedException, TimeoutException, ExecutionException {
    String entityPath = "/electronic-access-relationships";
    ElectronicAccessRelationship entity = new ElectronicAccessRelationship("Test electronic access relationship type");

    Response postResponse = createReferenceRecord(entityPath, entity);
    assertThat(postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    String entityUuid = postResponse.getJson().getString("id");
    String updateProperty = ElectronicAccessRelationship.NAME_KEY;

    testGetPutDeletePost(entityPath, entityUuid, entity, updateProperty);
  }

  @Test
  public void canCreateAndGetElectronicAccessRelationshipWithSourceFieldPopulated()
    throws InterruptedException, TimeoutException, ExecutionException {
    String entityName = "Electronic access relationship with 'source' field";
    String entitySource = "Consortium";

    String apiUrl = "/electronic-access-relationships";
    ElectronicAccessRelationship entity = new ElectronicAccessRelationship(entityName);
    entity.put("source", entitySource);

    // post new electronic access relationship with 'source' field populated
    Response postResponse = createReferenceRecord(apiUrl, entity);
    assertThat(postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    assertThat(postResponse.getJson().getString("id"), notNullValue());
    assertThat(postResponse.getJson().getString("name"), is(entityName));
    assertThat(postResponse.getJson().getString("source"), is(entitySource));

    // get saved electronic access relationship by id and verify all fields have been populated
    String entityId = postResponse.getJson().getString("id");

    Response getResponse = getById(vertxUrl(apiUrl + "/" + entityId));
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(getResponse.getJson().getString("id"), is(entityId));
    assertThat(getResponse.getJson().getString("name"), is(entityName));
    assertThat(getResponse.getJson().getString("source"), is(entitySource));

    // delete created resource
    deleteReferenceRecordById(vertxUrl(apiUrl + "/" + entityId));
  }

  @Test
  public void holdingsNoteTypesLoaded()
    throws InterruptedException, TimeoutException, ExecutionException {
    URL apiUrl = holdingsNoteTypesUrl("");

    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("holdings note types", searchResponse, 5, 20);
  }

  @Test
  public void holdingsNoteTypesBasicCrud()
    throws InterruptedException, TimeoutException, ExecutionException {
    String entityPath = "/holdings-note-types";
    HoldingsNoteType entity = new HoldingsNoteType("Test holdings note type", "test source");

    Response postResponse = createReferenceRecord(entityPath, entity);
    assertThat(postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    String entityUuid = postResponse.getJson().getString("id");
    String updateProperty = HoldingsNoteType.NAME_KEY;

    testGetPutDeletePost(entityPath, entityUuid, entity, updateProperty);
  }

  @Test
  public void holdingsTypesLoaded()
    throws InterruptedException, TimeoutException, ExecutionException {
    URL apiUrl = holdingsTypesUrl("");

    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("holdings types", searchResponse, 3, 20);
  }

  @Test
  public void holdingsTypesBasicCrud()
    throws InterruptedException, TimeoutException, ExecutionException {
    String entityPath = "/holdings-types";
    HoldingsType entity = new HoldingsType("Test holdings note type", "test source");

    Response postResponse = createReferenceRecord(entityPath, entity);
    assertThat(postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    String entityUuid = postResponse.getJson().getString("id");
    String updateProperty = HoldingsType.NAME_KEY;

    testGetPutDeletePost(entityPath, entityUuid, entity, updateProperty);
  }

  @Test
  public void identifierTypesLoaded()
    throws InterruptedException, TimeoutException, ExecutionException {
    URL apiUrl = identifierTypesUrl("");

    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("identifier types", searchResponse, 8, 30);
  }

  @Test
  public void illPoliciesLoaded()
    throws InterruptedException, TimeoutException, ExecutionException {
    URL apiUrl = illPoliciesUrl("");

    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("ILL policies", searchResponse, 5, 20);
  }

  @Test
  public void illPoliciesBasicCrud()
    throws InterruptedException, TimeoutException, ExecutionException {
    String entityPath = "/ill-policies";
    IllPolicy entity = new IllPolicy("Test ILL policy", "Test source");

    Response postResponse = createReferenceRecord(entityPath, entity);
    assertThat(postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    String entityUuid = postResponse.getJson().getString("id");
    String updateProperty = IllPolicy.NAME_KEY;

    testGetPutDeletePost(entityPath, entityUuid, entity, updateProperty);
  }

  @Test
  public void instanceFormatsLoaded()
    throws InterruptedException, TimeoutException, ExecutionException {
    URL apiUrl = instanceFormatsUrl("");

    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("instance formats", searchResponse, 20, 200);
  }

  @Test
  public void natureOfContentTermsLoaded()
    throws InterruptedException, TimeoutException, ExecutionException {
    URL apiUrl = natureOfContentTermsUrl("");

    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("nature-of-content terms", searchResponse, 20, 200);
  }

  @Test
  public void instanceStatusesLoaded()
    throws InterruptedException, TimeoutException, ExecutionException {
    URL apiUrl = instanceStatusesUrl("");

    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("instance statuses", searchResponse, 5, 20);
  }

  @Test
  public void instanceStatusesBasicCrud()
    throws InterruptedException, TimeoutException, ExecutionException {
    String entityPath = "/instance-statuses";
    InstanceStatus entity = new InstanceStatus("Test instance status", "Test Code", "Test Source");

    Response postResponse = createReferenceRecord(entityPath, entity);
    assertThat(postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    String entityUuid = postResponse.getJson().getString("id");
    String updateProperty = InstanceStatus.NAME_KEY;

    testGetPutDeletePost(entityPath, entityUuid, entity, updateProperty);
  }

  @Test
  public void instanceTypesLoaded()
    throws InterruptedException, TimeoutException, ExecutionException {
    URL apiUrl = instanceTypesUrl("");

    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("instance types (resource types)", searchResponse, 10, 100);
  }

  @Test
  public void itemNoteTypesLoaded()
    throws InterruptedException, TimeoutException, ExecutionException {
    URL apiUrl = itemNoteTypesUrl("");

    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("item note types", searchResponse, 5, 20);
  }

  @Test
  public void itemNoteTypesBasicCrud()
    throws InterruptedException, TimeoutException, ExecutionException {
    String entityPath = "/item-note-types";
    ItemNoteType entity = new ItemNoteType("Test item note type", "Test source");

    Response postResponse = createReferenceRecord(entityPath, entity);
    assertThat(postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    String entityUuid = postResponse.getJson().getString("id");
    String updateProperty = ItemNoteType.NAME_KEY;

    testGetPutDeletePost(entityPath, entityUuid, entity, updateProperty);
  }

  @Test
  public void instanceNoteTypesLoaded()
    throws InterruptedException, TimeoutException, ExecutionException {
    URL apiUrl = instanceNoteTypesUrl("");

    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("instance note types", searchResponse, 50, 60);
  }

  @Test
  public void instanceNoteTypesBasicCrud()
    throws InterruptedException, TimeoutException, ExecutionException {
    String entityPath = "/instance-note-types";
    InstanceNoteType entity = new InstanceNoteType("Test instance note type", "Test source");

    Response postResponse = createReferenceRecord(entityPath, entity);
    assertThat(postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    String entityUuid = postResponse.getJson().getString("id");
    String updateProperty = InstanceNoteType.NAME_KEY;

    testGetPutDeletePost(entityPath, entityUuid, entity, updateProperty);
  }

  @Test
  public void statisticalCodeTypesLoaded()
    throws InterruptedException, TimeoutException, ExecutionException {
    URL statisticalCodeTypesUrl = statisticalCodeTypesUrl("");

    Response searchResponseCodeTypes = getReferenceRecords(statisticalCodeTypesUrl);
    validateNumberOfReferenceRecords("statistical code types", searchResponseCodeTypes, 2, 50);
  }

  @Test
  public void statisticalCodesLoaded()
    throws InterruptedException, TimeoutException, ExecutionException {
    URL statisticalCodesUrl = statisticalCodesUrl("");

    Response searchResponseCodes = getReferenceRecords(statisticalCodesUrl);
    validateNumberOfReferenceRecords("statistical codes", searchResponseCodes, 10, 500);
  }

  @Test
  public void statisticalCodesAndCodeTypesBasicCrud()
    throws InterruptedException, TimeoutException, ExecutionException {
    String statisticalCodeTypesPath = "/statistical-code-types";
    String statisticalCodesPath = "/statistical-codes";

    String statisticalCodeTypeId = "8c5b634a-0a4a-47ec-b9b2-d66980656ffd";
    StatisticalCodeType statisticalCodeType =
      new StatisticalCodeType(statisticalCodeTypeId, "Test statistical code type", "Test source");
    StatisticalCode statisticalCode =
      new StatisticalCode("Test statistical name", "Test statistical code", statisticalCodeTypeId, "Test source");

    Response postResponseCodeType = createReferenceRecord(statisticalCodeTypesPath, statisticalCodeType);
    assertThat(postResponseCodeType.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    Response postResponseCode = createReferenceRecord(statisticalCodesPath, statisticalCode);
    assertThat(postResponseCode.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    String entityUuidCode = postResponseCode.getJson().getString("id");
    String updatePropertyCode = StatisticalCode.NAME_KEY;

    testGetPutDeletePost(statisticalCodesPath, entityUuidCode, statisticalCode, updatePropertyCode);

    String entityUuidCodeType = postResponseCodeType.getJson().getString("id");
    String updatePropertyCodeType = StatisticalCodeType.NAME_KEY;

    testGetPutDeletePost(statisticalCodeTypesPath, entityUuidCodeType, statisticalCodeType, updatePropertyCodeType);
  }

  @ParameterizedTest
  @CsvSource({"1.0.0, 28.1.0"})
  @SneakyThrows
  public void authorizedStaffServicePointIsLoadedFromReferenceData(String moduleFrom, String moduleTo) {
    ModuleUtility.prepareTenant(TENANT_ID, moduleFrom, moduleTo, false);
    int statusCode = servicePointsClient.getById(
        UUID.fromString("32c6f0c7-26e4-4350-8c29-1e11c2e3efc4"))
      .getStatusCode();
    assertThat(statusCode, is(HttpURLConnection.HTTP_OK));
  }

  private Response getReferenceRecords(URL baseUrl)
    throws InterruptedException, TimeoutException, ExecutionException {
    CompletableFuture<Response> searchCompleted = new CompletableFuture<>();
    String url = baseUrl.toString() + "?limit=400&query="
                 + URLEncoder.encode("cql.allRecords=1", StandardCharsets.UTF_8);
    getClient().get(url, TENANT_ID, ResponseHandler.json(searchCompleted));
    return searchCompleted.get(TIMEOUT, TimeUnit.SECONDS);
  }

  private void validateNumberOfReferenceRecords(String dataDescription, Response searchResponse, int min, int max) {
    Integer totalRecords = searchResponse.getJson().getInteger("totalRecords");
    assertNotNull(String.format("Could not retrieve record count for %s", dataDescription), totalRecords);
    assertTrue(String.format("Expected <=%s \"%s\", found %s, response:%n %s", max, dataDescription, totalRecords,
      searchResponse.getJson().encode()), max >= totalRecords);
    assertTrue(String.format("Expected >=%s \"%s\", found %s, response:%n %s", min, dataDescription, totalRecords,
      searchResponse.getJson().encode()), min <= totalRecords);
  }

  private Response createReferenceRecord(String path, JsonEntity referenceObject)
    throws ExecutionException, InterruptedException, TimeoutException {

    URL referenceUrl = vertxUrl(path);
    CompletableFuture<Response> createCompleted = new CompletableFuture<>();
    getClient().post(
      referenceUrl,
      referenceObject.getJson(),
      TENANT_ID,
      ResponseHandler.any(createCompleted)
    );
    return createCompleted.get(TIMEOUT, TimeUnit.SECONDS);
  }

  private Response getById(URL getByIdUrl) throws InterruptedException,
    ExecutionException, TimeoutException {

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    getClient().get(getByIdUrl, TENANT_ID,
      ResponseHandler.any(getCompleted));

    return getCompleted.get(TIMEOUT, TimeUnit.SECONDS);
  }

  private Response getByQuery(URL getByQueryUrl) throws InterruptedException,
    ExecutionException, TimeoutException {
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    getClient().get(getByQueryUrl, TENANT_ID,
      ResponseHandler.any(getCompleted));

    return getCompleted.get(TIMEOUT, TimeUnit.SECONDS);
  }

  private Response deleteReferenceRecordById(URL entityUrl)
    throws ExecutionException, InterruptedException, TimeoutException {
    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();
    getClient().delete(
      entityUrl,
      TENANT_ID,
      ResponseHandler.any(deleteCompleted)
    );
    return deleteCompleted.get(TIMEOUT, TimeUnit.SECONDS);
  }

  private Response updateRecord(URL entityUrl, JsonEntity referenceObject)
    throws ExecutionException, InterruptedException, TimeoutException {
    CompletableFuture<Response> updateCompleted = new CompletableFuture<>();
    getClient().put(
      entityUrl,
      referenceObject.getJson(),
      TENANT_ID,
      ResponseHandler.any(updateCompleted)
    );
    return updateCompleted.get(TIMEOUT, TimeUnit.SECONDS);
  }

  private void testGetPutDeletePost(String path, String entityId, JsonEntity entity, String updateProperty)
    throws ExecutionException,
    InterruptedException,
    TimeoutException {

    entity.put(updateProperty, entity.getString(updateProperty) + " UPDATED");

    final URL url = vertxUrl(path + "/" + entityId);
    final URL urlWithBadUuid = vertxUrl(path + "/baduuid");
    final URL urlWithBadParameter = vertxUrl(path + "?offset=-3");
    final URL urlWithBadCql = vertxUrl(path + "?query=badcql");

    Response putResponse = updateRecord(url, entity);
    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    Response getResponse = getById(url);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(getResponse.getJson().getString(updateProperty), is(entity.getString(updateProperty)));

    entity.put("id", entityId);
    Response postResponse1 = createReferenceRecord(path, entity);
    assertThat(postResponse1.getStatusCode(), is(422));

    Response badParameterResponse = getByQuery(urlWithBadParameter);
    assertThat(badParameterResponse.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));

    Response badQueryResponse = getByQuery(urlWithBadCql);
    assertThat(badQueryResponse.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));

    Response putResponse2 = updateRecord(urlWithBadUuid, entity);
    assertThat(putResponse2.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));

    Response deleteResponse = deleteReferenceRecordById(url);

    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    Response getResponse2 = getById(url);

    assertThat(getResponse2.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));

    Response deleteResponse2 = deleteReferenceRecordById(url);

    assertThat(deleteResponse2.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));

    Response deleteResponse3 = deleteReferenceRecordById(urlWithBadUuid);

    assertThat(deleteResponse3.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));

    entity.put("id", "baduuid");
    Response postResponse2 = createReferenceRecord(path, entity);
    assertThat(postResponse2.getStatusCode(), is(422));
  }
}
