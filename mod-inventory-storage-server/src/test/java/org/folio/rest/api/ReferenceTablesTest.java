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
import static org.folio.rest.support.http.InterfaceUrls.loanTypesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.natureOfContentTermsUrl;
import static org.folio.rest.support.http.InterfaceUrls.statisticalCodeTypesUrl;
import static org.folio.rest.support.http.InterfaceUrls.statisticalCodesUrl;
import static org.folio.utility.ModuleUtility.getClient;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.SneakyThrows;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.utility.ModuleUtility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReferenceTablesTest extends TestBase {

  @Test
  void alternativeTitleTypesLoaded()
    throws Exception {
    URL apiUrl = alternativeTitleTypesUrl("");
    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("alternative title types", searchResponse, 11, 40);
  }

  @Test
  void callNumberTypesLoaded()
    throws Exception {
    URL apiUrl = callNumberTypesUrl("");
    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("call number types", searchResponse, 5, 40);
  }

  @Test
  void classificationTypesLoaded()
    throws Exception {
    URL apiUrl = classificationTypesUrl("");
    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("classification types", searchResponse, 2, 20);
  }

  @Test
  void contributorNameTypesLoaded()
    throws Exception {
    URL apiUrl = contributorNameTypesUrl("");

    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("contributor name types", searchResponse, 3, 10);
  }

  @Test
  void contributorTypesLoaded()
    throws Exception {
    URL apiUrl = contributorTypesUrl("");

    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("contributor types", searchResponse, 20, 500);
  }

  @Test
  void electronicAccessRelationshipsLoaded()
    throws Exception {
    URL apiUrl = electronicAccessRelationshipsUrl("");

    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("electronic access relationship types", searchResponse, 2, 20);
  }

  @Test
  void holdingsNoteTypesLoaded()
    throws Exception {
    URL apiUrl = holdingsNoteTypesUrl("");

    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("holdings note types", searchResponse, 5, 20);
  }

  @Test
  void holdingsTypesLoaded()
    throws Exception {
    URL apiUrl = holdingsTypesUrl("");

    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("holdings types", searchResponse, 3, 20);
  }

  @Test
  void identifierTypesLoaded()
    throws Exception {
    URL apiUrl = identifierTypesUrl("");

    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("identifier types", searchResponse, 8, 30);
  }

  @Test
  void illPoliciesLoaded()
    throws Exception {
    URL apiUrl = illPoliciesUrl("");

    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("ILL policies", searchResponse, 5, 20);
  }

  @Test
  void instanceFormatsLoaded()
    throws Exception {
    URL apiUrl = instanceFormatsUrl("");

    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("instance formats", searchResponse, 20, 200);
  }

  @Test
  void natureOfContentTermsLoaded()
    throws Exception {
    URL apiUrl = natureOfContentTermsUrl("");

    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("nature-of-content terms", searchResponse, 20, 200);
  }

  @Test
  void instanceStatusesLoaded()
    throws Exception {
    URL apiUrl = instanceStatusesUrl("");

    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("instance statuses", searchResponse, 5, 20);
  }

  @Test
  void instanceTypesLoaded()
    throws Exception {
    URL apiUrl = instanceTypesUrl("");

    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("instance types (resource types)", searchResponse, 10, 100);
  }

  @Test
  void itemNoteTypesLoaded()
    throws Exception {
    URL apiUrl = itemNoteTypesUrl("");

    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("item note types", searchResponse, 5, 20);
  }

  @Test
  void instanceNoteTypesLoaded()
    throws Exception {
    URL apiUrl = instanceNoteTypesUrl("");

    Response searchResponse = getReferenceRecords(apiUrl);
    validateNumberOfReferenceRecords("instance note types", searchResponse, 50, 60);
  }

  @Test
  void statisticalCodeTypesLoaded()
    throws Exception {
    URL statisticalCodeTypesUrl = statisticalCodeTypesUrl("");

    Response searchResponseCodeTypes = getReferenceRecords(statisticalCodeTypesUrl);
    validateNumberOfReferenceRecords("statistical code types", searchResponseCodeTypes, 2, 50);
  }

  @Test
  void statisticalCodesLoaded()
    throws Exception {
    URL statisticalCodesUrl = statisticalCodesUrl("");

    Response searchResponseCodes = getReferenceRecords(statisticalCodesUrl);
    validateNumberOfReferenceRecords("statistical codes", searchResponseCodes, 10, 500);
  }

  @Test
  void loanTypesLoaded()
    throws Exception {
    URL loanTypesStorageUrl = loanTypesStorageUrl("");

    Response searchResponseCodes = getReferenceRecords(loanTypesStorageUrl);
    validateNumberOfReferenceRecords("loan types", searchResponseCodes, 4, 4);
    var loanTypesCollection = searchResponseCodes.getJson().getJsonArray("loantypes");
    for (int i = 0; i < loanTypesCollection.size(); i++) {
      var source = loanTypesCollection.getJsonObject(i).getString("source");
      assertEquals("folio", source);
    }
  }

  @ParameterizedTest
  @CsvSource({"1.0.0, 28.1.0"})
  @SneakyThrows
  void authorizedStaffServicePointIsLoadedFromReferenceData(String moduleFrom, String moduleTo) {
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
    assertNotNull(totalRecords, String.format("Could not retrieve record count for %s", dataDescription));
    assertTrue(max >= totalRecords, String.format("Expected <=%s \"%s\", found %s, response:%n %s",
      max, dataDescription, totalRecords, searchResponse.getJson().encode()));
    assertTrue(min <= totalRecords, String.format("Expected >=%s \"%s\", found %s, response:%n %s",
      min, dataDescription, totalRecords, searchResponse.getJson().encode()));
  }
}
