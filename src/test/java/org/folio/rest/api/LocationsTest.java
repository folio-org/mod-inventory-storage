package org.folio.rest.api;

import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.loanTypesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.locationsStorageUrl;
import static org.folio.utility.LocationUtility.clearServicePointIds;
import static org.folio.utility.LocationUtility.createLocation;
import static org.folio.utility.LocationUtility.createLocationUnits;
import static org.folio.utility.LocationUtility.getCampusId;
import static org.folio.utility.LocationUtility.getInstitutionId;
import static org.folio.utility.LocationUtility.getLibraryId;
import static org.folio.utility.LocationUtility.getServicePointIds;
import static org.folio.utility.ModuleUtility.getVertx;
import static org.folio.utility.RestUtility.send;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.folio.rest.support.AdditionalHttpStatusCodes;
import org.folio.rest.support.HttpClient;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.client.LoanTypesClient;
import org.junit.Before;
import org.junit.Test;

/* TODO: Missing tests
   - Bad inst/camp/lib in PUT
 */
public class LocationsTest extends TestBaseWithInventoryUtil {
  private static final String SUPPORTED_CONTENT_TYPE_JSON_DEF = "application/json";

  @SneakyThrows
  @Before
  public void beforeEach() {
    clearData();

    canCirculateLoanTypeID = new LoanTypesClient(
      new HttpClient(getVertx()),
      loanTypesStorageUrl("")).create("Can Circulate");

    setupMaterialTypes();
    clearServicePointIds();
    createLocationUnits(true);
    removeAllEvents();
  }

  @Test
  public void canCreateLocation() {
    Response response = createLocation(null, "Main Library", getInstitutionId(),
      getCampusId(), getLibraryId(), "PI/CC/ML/X", getServicePointIds());
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    assertThat(response.getJson().getString("id"), notNullValue());
    assertThat(response.getJson().getString("name"), is("Main Library"));
  }

  @Test
  public void cannotCreateLocationWithoutUnits() {
    Response response = createLocation(null, "Main Library", null, null, null,
      "PI/CC/ML/X", getServicePointIds());
    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void cannotCreateLocationWithoutCode() {
    Response response = createLocation(null, "Main Library", getInstitutionId(),
      getCampusId(), getLibraryId(), null, getServicePointIds());
    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void cannotCreateLocationWithSameName() {
    createLocation(null, "Main Library", "PI/CC/ML/X");
    Response response = createLocation(null, "Main Library", getInstitutionId(),
      getCampusId(), getLibraryId(), "AA/BB", getServicePointIds());
    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void cannotCreateLocationWithSameCode() {
    createLocation(null, "Main Library", "PI/CC/ML/X");
    Response response = createLocation(null, "Some Other Library", getInstitutionId(),
      getCampusId(), getLibraryId(), "PI/CC/ML/X",
      getServicePointIds());
    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void cannotCreateLocationWithSameId() {
    UUID id = UUID.randomUUID();
    createLocation(id, "Main Library", "PI/CC/ML/X");
    Response response = createLocation(id, "Some Other Library", getInstitutionId(),
      getCampusId(), getLibraryId(), "AA/BB", getServicePointIds());
    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void canGetLocationById() {

    UUID id = UUID.randomUUID();
    createLocation(id, "Main Library", "PI/CC/ML/X");
    Response getResponse = getById(id);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonObject item = getResponse.getJson();
    assertThat(item.getString("id"), is(id.toString()));
    assertThat(item.getString("name"), is("Main Library"));
    assertThat(item.getJsonObject("metadata").getString("createdByUserId"),
      is("test_user"));  // The userId header triggers creation of metadata
  }

  @Test
  public void canListLocations() {

    createLocation(null, "Main Library", "PI/CC/ML");
    createLocation(null, "Annex Library", "PI/CC/AL");
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    send(locationsStorageUrl("/"), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(getCompleted));
    Response getResponse = get(getCompleted);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonObject item = getResponse.getJson();
    assertThat(item.getInteger("totalRecords"), is(2));
  }

  @Test
  public void canUpdateLocation() {

    UUID id = UUID.randomUUID();
    createLocation(id, "Main Library", "PI/CC/ML/X");
    JsonObject updateRequest = new JsonObject()
      .put("id", id.toString())
      .put("name", "Annex Library")
      .put("institutionId", getInstitutionId().toString())
      .put("campusId", getCampusId().toString())
      .put("libraryId", getLibraryId().toString())
      .put("isActive", true)
      .put("code", "AA/BB")
      .put("primaryServicePoint", getServicePointIds().get(0).toString())
      .put("servicePointIds", new JsonArray(getServicePointIds()));
    CompletableFuture<Response> updated = new CompletableFuture<>();
    send(locationsStorageUrl("/" + id), HttpMethod.PUT,
      updateRequest.toString(), SUPPORTED_CONTENT_TYPE_JSON_DEF,
      ResponseHandler.any(updated));
    Response updateResponse = get(updated);
    assertThat(updateResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
    Response getResponse = getById(id);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonObject item = getResponse.getJson();
    assertThat(item.getString("id"), is(id.toString()));
    assertThat(item.getString("name"), is("Annex Library"));
  }

  @Test
  public void cannotUpdateId() {

    UUID id = UUID.randomUUID();
    createLocation(id, "Main Library", "PI/CC/ML/X");
    JsonObject updateRequest = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("name", "Annex Library")
      .put("institutionId", getInstitutionId().toString())
      .put("campusId", getCampusId().toString())
      .put("libraryId", getLibraryId().toString())
      .put("isActive", true)
      .put("code", "AA/BB").put("primaryServicePoint", getServicePointIds().get(0).toString())
      .put("servicePointIds", new JsonArray(getServicePointIds()));
    CompletableFuture<Response> updated = new CompletableFuture<>();
    send(locationsStorageUrl("/" + id), HttpMethod.PUT,
      updateRequest.toString(), SUPPORTED_CONTENT_TYPE_JSON_DEF,
      ResponseHandler.any(updated));
    Response updateResponse = get(updated);
    assertThat(updateResponse.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void canDeleteLocation() {
    UUID id = UUID.randomUUID();
    createLocation(id, "Main Library", "PI/CC/ML/X");
    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();
    send(locationsStorageUrl("/" + id), HttpMethod.DELETE, null,
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(deleteCompleted));
    Response deleteResponse = get(deleteCompleted);
    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
  }

  @Test
  public void cannotDeleteLocationAssociatedWithAnItem() {

    UUID id = UUID.randomUUID();
    createLocation(id, "Main Library", "PI/CC/ML/X");
    UUID holdingsRecordId = createInstanceAndHolding(id);
    JsonObject item = createItemRequest(holdingsRecordId.toString(), id.toString());
    CompletableFuture<Response> createItemCompleted = new CompletableFuture<>();
    send(itemsStorageUrl(""), HttpMethod.POST, item.toString(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(createItemCompleted));
    Response createItemResponse = get(createItemCompleted);
    assertThat(createItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();
    send(locationsStorageUrl("/" + id),
      HttpMethod.DELETE, null, SUPPORTED_CONTENT_TYPE_JSON_DEF,
      ResponseHandler.any(deleteCompleted));
    Response deleteResponse = get(deleteCompleted);
    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
  }

  @Test
  public void canSearchByPrimaryServicePoint() throws Exception {
    final UUID firstServicePointId = UUID.randomUUID();
    final UUID secondServicePointId = UUID.randomUUID();
    final UUID expectedLocationId = UUID.randomUUID();

    createLocation(expectedLocationId, "Main", getInstitutionId(), getCampusId(), getLibraryId(), "main",
      Collections.singletonList(firstServicePointId));
    createLocation(null, "Main two", getInstitutionId(), getCampusId(), getLibraryId(), "main/tw",
      Collections.singletonList(secondServicePointId));

    final List<JsonObject> locations = getMany("primaryServicePoint==\"%s\"", firstServicePointId);

    assertThat(locations.size(), is(1));
    assertThat(locations.get(0).getString("id"), is(expectedLocationId.toString()));
  }

  private List<JsonObject> getMany(String cql, Object... args) {

    final CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    send(locationsStorageUrl("?query=" + String.format(cql, args)),
      HttpMethod.GET, null, SUPPORTED_CONTENT_TYPE_JSON_DEF,
      ResponseHandler.json(getCompleted));

    return get(getCompleted).getJson()
      .getJsonArray("locations").stream()
      .map(obj -> (JsonObject) obj)
      .collect(Collectors.toList());
  }

  private JsonObject createItemRequest(String holdingsRecordId, String temporaryLocationId) {
    JsonObject item = new JsonObject();

    item.put("status", new JsonObject().put("name", "Available"));
    item.put("holdingsRecordId", holdingsRecordId);
    item.put("barcode", "12345");
    item.put("permanentLoanTypeId", canCirculateLoanTypeID);
    item.put("materialTypeId", journalMaterialTypeID);
    item.put("temporaryLocationId", temporaryLocationId);
    return item;
  }

  private Response getById(UUID id) {

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    send(locationsStorageUrl("/" + id.toString()), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(getCompleted));

    return get(getCompleted);
  }

}
