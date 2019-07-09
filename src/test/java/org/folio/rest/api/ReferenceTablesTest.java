/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.folio.rest.api;

import static org.folio.rest.api.TestBase.client;
import static org.folio.rest.support.http.InterfaceUrls.*;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.rest.api.entities.AlternativeTitleType;
import org.folio.rest.api.entities.CallNumberType;
import org.folio.rest.api.entities.ClassificationType;
import org.folio.rest.api.entities.ContributorNameType;
import org.folio.rest.api.entities.ContributorType;
import org.folio.rest.api.entities.ElectronicAccessRelationship;
import org.folio.rest.api.entities.HoldingsNoteType;
import org.folio.rest.api.entities.HoldingsType;
import org.folio.rest.api.entities.IdentifierType;
import org.folio.rest.api.entities.IllPolicy;
import org.folio.rest.api.entities.InstanceFormat;
import org.folio.rest.api.entities.InstanceNoteType;
import org.folio.rest.api.entities.InstanceStatus;
import org.folio.rest.api.entities.InstanceType;
import org.folio.rest.api.entities.ItemNoteType;
import org.folio.rest.api.entities.JsonEntity;
import org.folio.rest.api.entities.ModeOfIssuance;
import org.folio.rest.api.entities.NatureOfContentTerm;
import org.folio.rest.api.entities.StatisticalCode;
import org.folio.rest.api.entities.StatisticalCodeType;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.junit.Test;

/**
 *
 * @author ne
 */
public class ReferenceTablesTest extends TestBase {

  @Test
  public void alternativeTitleTypesLoaded()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    URL apiUrl = alternativeTitleTypesUrl("");
    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("alternative title types", searchResponse, 5, 40);
  }

  @Test
  public void alternativeTitleTypesBasicCrud()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {

    String entityPath = "/alternative-title-types";

    AlternativeTitleType entity =
            new AlternativeTitleType("Test alternative title type", "test source");

    Response postResponse = createReferenceRecord(entityPath, entity);

    assertThat(postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    String entityUUID = postResponse.getJson().getString("id");

    String updateProperty = AlternativeTitleType.NAME_KEY;

    testGetPutDeletePost(entityPath, entityUUID, entity, updateProperty);

  }

  @Test
  public void callNumberTypesLoaded()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    URL apiUrl = callNumberTypesUrl("");
    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("call number types", searchResponse, 5, 40);
  }

  @Test
  public void callNumberTypesBasicCrud()

          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {

    String entityPath = "/call-number-types";
    CallNumberType entity = new CallNumberType("Test call number type", "test source");
    Response postResponse = createReferenceRecord(entityPath, entity);
    assertThat(postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    String entityUUID = postResponse.getJson().getString("id");

    String updateProperty = CallNumberType.NAME_KEY;

    testGetPutDeletePost(entityPath, entityUUID, entity, updateProperty);
  }

  @Test
  public void classificationTypesLoaded()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    URL apiUrl = classificationTypesUrl("");
    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("classification types", searchResponse, 2, 20);
  }

  @Test
  public void classificationTypesBasicCrud()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {

    String entityPath = "/classification-types";
    ClassificationType entity = new ClassificationType("Test classfication type");
    Response postResponse = createReferenceRecord(entityPath, entity);

    assertThat(postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    String entityUUID = postResponse.getJson().getString("id");
    String updateProperty = ClassificationType.NAME_KEY;

    testGetPutDeletePost(entityPath, entityUUID, entity, updateProperty);
  }

  @Test
  public void contributorNameTypesLoaded()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    URL apiUrl = contributorNameTypesUrl("");

    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("contributor name types", searchResponse, 3, 10);
  }

  @Test
  public void contributorNameTypesBasicCrud()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    String entityPath = "/contributor-name-types";
    ContributorNameType entity = new ContributorNameType("Test contributor name type", "100");

    Response postResponse = createReferenceRecord(entityPath, entity);
    assertThat(postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    String entityUUID = postResponse.getJson().getString("id");
    String updateProperty = ContributorNameType.NAME_KEY;

    testGetPutDeletePost(entityPath, entityUUID, entity, updateProperty);
  }

  @Test
  public void contributorTypesLoaded()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    URL apiUrl = contributorTypesUrl("");

    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("contributor types", searchResponse, 20, 500);
  }

  @Test
  public void contributorTypesBasicCrud()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    String entityPath = "/contributor-types";
    ContributorType entity = new ContributorType("Test contributor type", "Test Code", "Test Source");

    Response postResponse = createReferenceRecord(entityPath, entity);
    assertThat(postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    String entityUUID = postResponse.getJson().getString("id");
    String updateProperty = ContributorType.NAME_KEY;

    testGetPutDeletePost(entityPath, entityUUID, entity, updateProperty);
  }

  @Test
  public void electronicAccessRelationshipsLoaded()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    URL apiUrl = electronicAccessRelationshipsUrl("");

    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("electronic access relationship types", searchResponse, 2, 20);
  }

  @Test
  public void electronicAccessRelationshipsBasicCrud()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    String entityPath = "/electronic-access-relationships";
    ElectronicAccessRelationship entity = new ElectronicAccessRelationship("Test electronic access relationship type");

    Response postResponse = createReferenceRecord(entityPath, entity);
    assertThat(postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    String entityUUID = postResponse.getJson().getString("id");
    String updateProperty = ElectronicAccessRelationship.NAME_KEY;

    testGetPutDeletePost(entityPath, entityUUID, entity, updateProperty);
  }

  @Test
  public void holdingsNoteTypesLoaded()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    URL apiUrl = holdingsNoteTypesUrl("");

    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("holdings note types", searchResponse, 5, 20);
  }

  @Test
  public void holdingsNoteTypesBasicCrud()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    String entityPath = "/holdings-note-types";
    HoldingsNoteType entity = new HoldingsNoteType("Test holdings note type", "test source");

    Response postResponse = createReferenceRecord(entityPath, entity);
    assertThat(postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    String entityUUID = postResponse.getJson().getString("id");
    String updateProperty = HoldingsNoteType.NAME_KEY;

    testGetPutDeletePost(entityPath, entityUUID, entity, updateProperty);
  }

  @Test
  public void holdingsTypesLoaded()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    URL apiUrl = holdingsTypesUrl("");

    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("holdings types", searchResponse, 3, 20);
  }

  @Test
  public void holdingsTypesBasicCrud()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    String entityPath = "/holdings-types";
    HoldingsType entity = new HoldingsType("Test holdings note type", "test source");

    Response postResponse = createReferenceRecord(entityPath, entity);
    assertThat(postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    String entityUUID = postResponse.getJson().getString("id");
    String updateProperty = HoldingsType.NAME_KEY;

    testGetPutDeletePost(entityPath, entityUUID, entity, updateProperty);

  }

  @Test
  public void identifierTypesLoaded()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    URL apiUrl = identifierTypesUrl("");

    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("identifier types", searchResponse, 8, 30);
  }

  @Test
  public void identifierTypesBasicCrud()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    String entityPath = "/identifier-types";
    IdentifierType entity = new IdentifierType("Test identifier type") ;

    Response postResponse = createReferenceRecord(entityPath, entity);

    assertThat(postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    String entityUUID = postResponse.getJson().getString("id");
    String updateProperty = IdentifierType.NAME_KEY;

    testGetPutDeletePost(entityPath, entityUUID, entity, updateProperty);
  }

  @Test
  public void illPoliciesLoaded()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    URL apiUrl = illPoliciesUrl("");

    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("ILL policies", searchResponse, 5, 20);
  }

  @Test
  public void illPoliciesBasicCrud()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    String entityPath = "/ill-policies";
    IllPolicy entity = new IllPolicy("Test ILL policy", "Test source");

    Response postResponse = createReferenceRecord(entityPath, entity);
    assertThat(postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    String entityUUID = postResponse.getJson().getString("id");
    String updateProperty = IllPolicy.NAME_KEY;

    testGetPutDeletePost(entityPath, entityUUID, entity, updateProperty);
  }

  @Test
  public void instanceFormatsLoaded()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    URL apiUrl = instanceFormatsUrl("");

    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("instance formats", searchResponse, 20, 200);
  }

  @Test
  public void instanceFormatsBasicCrud()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    String entityPath = "/instance-formats";
    InstanceFormat entity = new InstanceFormat("Test instance format", "Test Code", "Test Source");

    Response postResponse = createReferenceRecord(entityPath, entity);

    assertThat(postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    String entityUUID = postResponse.getJson().getString("id");
    String updateProperty = InstanceFormat.NAME_KEY;

    testGetPutDeletePost(entityPath, entityUUID, entity, updateProperty);
  }

  @Test
  public void natureOfContentTermsLoaded()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    URL apiUrl = natureOfContentTermsUrl("");

    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("nature-of-content terms", searchResponse, 20, 200);
  }

  @Test
  public void natureOfContentTermsBasicCrud()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    String entityPath = "/nature-of-content-terms";
    NatureOfContentTerm entity = new NatureOfContentTerm("Test Term", "Test Source");

    Response postResponse = createReferenceRecord(entityPath, entity);

    assertThat(postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    String entityUUID = postResponse.getJson().getString("id");
    String updateProperty = InstanceFormat.NAME_KEY;

    testGetPutDeletePost(entityPath, entityUUID, entity, updateProperty);
  }


  @Test
  public void instanceStatusesLoaded()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    URL apiUrl = instanceStatusesUrl("");

    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("instance statuses", searchResponse, 5, 20);
  }

  @Test
  public void instanceStatusesBasicCrud()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    String entityPath = "/instance-statuses";
    InstanceStatus entity = new InstanceStatus("Test instance status", "Test Code", "Test Source");

    Response postResponse = createReferenceRecord(entityPath, entity);
    assertThat(postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    String entityUUID = postResponse.getJson().getString("id");
    URL entityUrl = instanceStatusesUrl("/" + entityUUID);
    String updateProperty = InstanceStatus.NAME_KEY;

    testGetPutDeletePost(entityPath, entityUUID, entity, updateProperty);
  }

  @Test
  public void instanceTypesLoaded()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    URL apiUrl = instanceTypesUrl("");

    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("instance types (resource types)", searchResponse, 10, 100);
  }

  @Test
  public void instanceTypesBasicCrud()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    String entityPath = "/instance-types";
    InstanceType entity = new InstanceType("Test instance type", "Test Code", "Test Source");

    Response postResponse = createReferenceRecord(entityPath, entity);
    assertThat(postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    String entityUUID = postResponse.getJson().getString("id");
    String updateProperty = InstanceType.NAME_KEY;

    testGetPutDeletePost(entityPath, entityUUID, entity, updateProperty);
  }

  @Test
  public void itemNoteTypesLoaded()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    URL apiUrl = itemNoteTypesUrl("");

    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("item note types", searchResponse, 5, 20);
  }

  @Test
  public void itemNoteTypesBasicCrud()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    String entityPath = "/item-note-types";
    ItemNoteType entity = new ItemNoteType("Test item note type", "Test source");

    Response postResponse = createReferenceRecord(entityPath, entity);
    assertThat(postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    String entityUUID = postResponse.getJson().getString("id");
    String updateProperty = ItemNoteType.NAME_KEY;

    testGetPutDeletePost(entityPath, entityUUID, entity, updateProperty);
  }

  @Test
  public void instanceNoteTypesLoaded()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    URL apiUrl = instanceNoteTypesUrl("");

    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("instance note types", searchResponse, 50, 60);
  }

  @Test
  public void instanceNoteTypesBasicCrud()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    String entityPath = "/instance-note-types";
    InstanceNoteType entity = new InstanceNoteType("Test instance note type", "Test source");

    Response postResponse = createReferenceRecord(entityPath, entity);
    assertThat(postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    String entityUUID = postResponse.getJson().getString("id");
    String updateProperty = InstanceNoteType.NAME_KEY;

    testGetPutDeletePost(entityPath, entityUUID, entity, updateProperty);
  }


  @Test
  public void modesOfIssuanceLoaded()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    URL apiUrl = modesOfIssuanceUrl("");

    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("modes of issuance", searchResponse, 4, 10);
  }

  @Test
  public void modesOfIssuanceBasicCrud()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    String entityPath = "/modes-of-issuance";
    ModeOfIssuance entity = new ModeOfIssuance("Test mode of issuance");

    Response postResponse = createReferenceRecord(entityPath, entity);
    assertThat(postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    String entityUUID = postResponse.getJson().getString("id");
    String updateProperty = ModeOfIssuance.NAME_KEY;

    testGetPutDeletePost(entityPath, entityUUID, entity, updateProperty);
  }

  @Test
  public void statisticalCodeTypesLoaded()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    URL statisticalCodeTypesUrl = statisticalCodeTypesUrl("");

    Response searchResponseCodeTypes = getReferenceRecords(statisticalCodeTypesUrl);
    validateNumberOfReferenceRecords("statistical code types", searchResponseCodeTypes, 2, 50);
  }

  @Test
  public void statisticalCodesLoaded()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    URL statisticalCodesUrl = statisticalCodesUrl("");

    Response searchResponseCodes = getReferenceRecords(statisticalCodesUrl);
    validateNumberOfReferenceRecords("statistical codes", searchResponseCodes, 10, 500);
  }

  @Test
  public void statisticalCodesAndCodeTypesBasicCrud()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {

    String statisticalCodeTypesPath = "/statistical-code-types";
    String statisticalCodesPath = "/statistical-codes";

    String statisticalCodeTypeId = "8c5b634a-0a4a-47ec-b9b2-d66980656ffd";
    StatisticalCodeType statisticalCodeType = new StatisticalCodeType(statisticalCodeTypeId, "Test statistical code type", "Test source");
    StatisticalCode statisticalCode = new StatisticalCode("Test statistical name", "Test statistical code", statisticalCodeTypeId, "Test source");

    Response postResponseCodeType = createReferenceRecord(statisticalCodeTypesPath, statisticalCodeType);
    assertThat(postResponseCodeType.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    Response postResponseCode = createReferenceRecord(statisticalCodesPath, statisticalCode);
    assertThat(postResponseCode.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    String entityUUIDCode = postResponseCode.getJson().getString("id");
    String updatePropertyCode = StatisticalCode.NAME_KEY;

    testGetPutDeletePost(statisticalCodesPath, entityUUIDCode, statisticalCode, updatePropertyCode);

    String entityUUIDCodeType = postResponseCodeType.getJson().getString("id");
    String updatePropertyCodeType = StatisticalCodeType.NAME_KEY;

    testGetPutDeletePost(statisticalCodeTypesPath, entityUUIDCodeType, statisticalCodeType, updatePropertyCodeType);

  }

  private Response getReferenceRecords(URL baseUrl)
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {

    CompletableFuture<Response> searchCompleted = new CompletableFuture<>();
    String url = baseUrl.toString() + "?limit=400&query="
            + URLEncoder.encode("cql.allRecords=1", StandardCharsets.UTF_8.name());
    client.get(url, StorageTestSuite.TENANT_ID, ResponseHandler.json(searchCompleted));
    Response searchResponse = searchCompleted.get(5, TimeUnit.SECONDS);
    return searchResponse;
  }

  private void validateNumberOfReferenceRecords(String dataDescription, Response searchResponse, int min, int max) {
    Integer totalRecords = searchResponse.getJson().getInteger("totalRecords");
    assertTrue(String.format("Could not retrieve record count for %s", dataDescription), totalRecords != null);
    assertTrue(String.format("Expected <=%s \"%s\", found %s", max, dataDescription, totalRecords), max >= totalRecords);
    assertTrue(String.format("Expected >=%s \"%s\", found %s", min, dataDescription, totalRecords), min <= totalRecords);
  }

  private Response createReferenceRecord(String path, JsonEntity referenceObject)
  throws ExecutionException, InterruptedException, TimeoutException, MalformedURLException {

    URL referenceUrl = StorageTestSuite.storageUrl(path);
    CompletableFuture<Response> createCompleted = new CompletableFuture<>();
    client.post(
            referenceUrl,
            referenceObject.getJson(),
            StorageTestSuite.TENANT_ID,
            ResponseHandler.any(createCompleted)
    );
    Response postResponse = createCompleted.get(5, TimeUnit.SECONDS);
    return postResponse;
  }

  private Response getById(URL getByIdUrl)
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(getByIdUrl, StorageTestSuite.TENANT_ID,
      ResponseHandler.any(getCompleted));

    Response getByIdResponse = getCompleted.get(5, TimeUnit.SECONDS);

    return getByIdResponse;
  }

  private Response getByQuery(URL getByQueryUrl)
          throws MalformedURLException, InterruptedException,
          ExecutionException, TimeoutException {
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(getByQueryUrl, StorageTestSuite.TENANT_ID,
      ResponseHandler.any(getCompleted));

    Response getByQueryResponse = getCompleted.get(5, TimeUnit.SECONDS);

    return getByQueryResponse;

  }

  private Response deleteReferenceRecordById (URL entityUrl)
  throws ExecutionException, InterruptedException, TimeoutException {

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();
    client.delete(
            entityUrl,
            StorageTestSuite.TENANT_ID,
            ResponseHandler.any(deleteCompleted)
    );
    Response deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);
    return deleteResponse;
  }

  private Response updateRecord (URL entityUrl, JsonEntity referenceObject)
  throws ExecutionException, InterruptedException, TimeoutException {
    CompletableFuture<Response> updateCompleted = new CompletableFuture<>();
    client.put(
            entityUrl,
            referenceObject.getJson(),
            StorageTestSuite.TENANT_ID,
            ResponseHandler.any(updateCompleted)
    );
    Response putResponse = updateCompleted.get(5, TimeUnit.SECONDS);
    return putResponse;
  }

  private void testGetPutDeletePost (String path, String entityId, JsonEntity entity, String updateProperty)
          throws ExecutionException,
          InterruptedException,
          MalformedURLException,
          TimeoutException {

    entity.put(updateProperty, entity.getString(updateProperty)+" UPDATED");

    URL url = StorageTestSuite.storageUrl(path + "/" + entityId);
    URL urlWithBadUUID = StorageTestSuite.storageUrl(path + "/baduuid");
    URL urlWithBadParameter = StorageTestSuite.storageUrl(path+"?offset=-3");
    URL urlWithBadCql = StorageTestSuite.storageUrl(path + "?query=badcql");

    Response putResponse = updateRecord(url, entity);
    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    Response getResponse = getById(url);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(getResponse.getJson().getString(updateProperty), is(entity.getString(updateProperty)));

    entity.put("id", entityId);
    Response postResponse1 = createReferenceRecord(path, entity);
    if (Arrays.asList("/electronic-access-relationships", "/instance-statuses", "/modes-of-issuance", "/statistical-code-types").contains(path)) {
      assertThat(postResponse1.getStatusCode(), is(422));
    } else {
      assertThat(postResponse1.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
    }

    Response badParameterResponse = getByQuery(urlWithBadParameter);
    assertThat(badParameterResponse.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));

    Response badQueryResponse = getByQuery(urlWithBadCql);
    assertThat(badQueryResponse.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));

    Response putResponse2 = updateRecord(urlWithBadUUID, entity);
    assertThat(putResponse2.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));

    Response deleteResponse = deleteReferenceRecordById (url);

    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    Response getResponse2 = getById(url);

    assertThat(getResponse2.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));

    Response deleteResponse2 = deleteReferenceRecordById (url);

    assertThat(deleteResponse2.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));

    Response deleteResponse3 = deleteReferenceRecordById (urlWithBadUUID);

    assertThat(deleteResponse3.getStatusCode(), (is(HttpURLConnection.HTTP_BAD_REQUEST)));

    entity.put("id", "baduuid");
    Response postResponse2 = createReferenceRecord(path, entity);
    if (Arrays.asList("/instance-note-types", "/nature-of-content-terms").contains(path)) {
      assertThat(postResponse2.getStatusCode(), is(422)); // unprocessable entity, fails UUID pattern
    } else {
      assertThat(postResponse2.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
    }
  }
}
