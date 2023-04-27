package org.folio.rest.api;

import static org.folio.rest.support.HttpResponseMatchers.statusCodeIs;
import static org.folio.rest.support.http.InterfaceUrls.holdingsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.instancesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.loanTypesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.locCampusStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.locInstitutionStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.locLibraryStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.locationsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.materialTypesStorageUrl;
import static org.folio.utility.LocationUtility.createCampus;
import static org.folio.utility.LocationUtility.createInstitution;
import static org.folio.utility.LocationUtility.createLibrary;
import static org.folio.utility.RestUtility.send;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import java.net.HttpURLConnection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.SneakyThrows;
import org.folio.rest.support.AdditionalHttpStatusCodes;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.junit.Before;
import org.junit.Test;

// Missing tests:
// - Add/update a campus that points to a non-existing inst
// - delete an inst that is in use by a campus
// - Add/update a library that points to a non-existing campus
// - delete a campus that is in use by a library
// (The code for these checks is missing!)
//
public class LocationUnitTest extends TestBase {

  private static final String SUPPORTED_CONTENT_TYPE_JSON_DEF = "application/json";

  @SneakyThrows
  @Before
  public void beforeEach() {
    StorageTestSuite.deleteAll(itemsStorageUrl(""));
    StorageTestSuite.deleteAll(holdingsStorageUrl(""));
    StorageTestSuite.deleteAll(instancesStorageUrl(""));
    StorageTestSuite.deleteAll(locationsStorageUrl(""));
    StorageTestSuite.deleteAll(locLibraryStorageUrl(""));
    StorageTestSuite.deleteAll(locCampusStorageUrl(""));
    StorageTestSuite.deleteAll(locInstitutionStorageUrl(""));
    StorageTestSuite.deleteAll(loanTypesStorageUrl(""));
    StorageTestSuite.deleteAll(materialTypesStorageUrl(""));

    removeAllEvents();
  }

  /////////////////////// Inst tests
  @Test
  public void canCreateAnInst() {

    Response response = createInstitution(null, "Institute of MetaPhysics", "MPI");

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    assertThat(response.getJson().getString("id"), notNullValue());
    assertThat(response.getJson().getString("name"), is("Institute of MetaPhysics"));
    assertThat(response.getJson().getString("code"), is("MPI"));
  }

  @Test
  public void cannotCreateInstWithSameName() {

    createInstitution(null, "Institute of MetaPhysics", "MPI");
    Response response = createInstitution(null, "Institute of MetaPhysics", "MPI");
    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void cannotCreateInstSameId() {

    UUID id = UUID.randomUUID();
    createInstitution(id, "Institute of MetaPhysics", "MPI");
    Response response = createInstitution(id, "The Other Institute", "MPI");
    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void cannotCreateAnInstWithoutCode() {

    UUID id = UUID.randomUUID();
    Response response = createInstitution(id, "Institute of MetaPhysics", null);

    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void canGetAnInstById() {

    UUID id = UUID.randomUUID();
    createInstitution(id, "Institute of MetaPhysics", "MPI");
    Response getResponse = getInstById(id);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonObject item = getResponse.getJson();
    assertThat(item.getString("id"), is(id.toString()));
    assertThat(item.getString("name"), is("Institute of MetaPhysics"));
    assertThat(item.getJsonObject("metadata").getString("createdByUserId"),
      is("test_user"));  // The userId header triggers creation of metadata
  }

  @Test
  public void cannotGetAnInstByWrongId() {
    createInstitution(null, "Institute of MetaPhysics", "MPI");
    UUID id = UUID.randomUUID();
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    send(locInstitutionStorageUrl("/" + id), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(getCompleted));
    Response getResponse = get(getCompleted);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @Test
  public void canListInsts() {

    createInstitution(null, "Institute of MetaPhysics", "MPI");
    createInstitution(null, "The Other Institute", "OI");

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    send(locInstitutionStorageUrl("/"), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(getCompleted));

    Response getResponse = get(getCompleted);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonObject item = getResponse.getJson();
    assertThat(item.getInteger("totalRecords"), is(2));
  }

  @Test
  public void canQueryInsts() {

    createInstitution(null, "Institute of MetaPhysics", "MPI");
    createInstitution(null, "The Other Institute", "OI");
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    send(locInstitutionStorageUrl("/?query=name=Other"), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(getCompleted));
    Response getResponse = get(getCompleted);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonObject item = getResponse.getJson();
    assertThat(item.getInteger("totalRecords"), is(1));
  }

  @Test
  public void badQueryInsts() {

    createInstitution(null, "Institute of MetaPhysics", "MPI");
    createInstitution(null, "The Other Institute", "OI");
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    send(locInstitutionStorageUrl("/?query=invalidCQL"), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(getCompleted));
    Response getResponse;
    getResponse = get(getCompleted);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
  }

  @Test
  public void canUpdateAnInst() {

    UUID id = UUID.randomUUID();
    createInstitution(id, "Institute of MetaPhysics", "MPI");

    JsonObject updateRequest = new JsonObject()
      .put("id", id.toString())
      .put("name", "The Other Institute")
      .put("code", "MPA");

    CompletableFuture<Response> updated = new CompletableFuture<>();

    send(locInstitutionStorageUrl("/" + id), HttpMethod.PUT,
      updateRequest.toString(), SUPPORTED_CONTENT_TYPE_JSON_DEF,
      ResponseHandler.any(updated));

    Response updateResponse = get(updated);

    assertThat(updateResponse, statusCodeIs(HttpURLConnection.HTTP_NO_CONTENT));
    assertThat(updateResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
    Response getResponse = getInstById(id);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonObject item = getResponse.getJson();
    assertThat(item.getString("id"), is(id.toString()));
    assertThat(item.getString("name"), is("The Other Institute"));
    assertThat(item.getString("code"), is("MPA"));
  }

  @Test
  public void cannotUpdateAnInstId() {

    UUID id = UUID.randomUUID();
    createInstitution(id, "Institute of MetaPhysics", "MPI");
    JsonObject updateRequest = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("name", "The Other Institute")
      .put("code", "MPA");
    CompletableFuture<Response> updated = new CompletableFuture<>();
    send(locInstitutionStorageUrl("/" + id), HttpMethod.PUT,
      updateRequest.toString(), SUPPORTED_CONTENT_TYPE_JSON_DEF,
      ResponseHandler.any(updated));
    Response updateResponse = get(updated);
    assertThat(updateResponse, statusCodeIs(HttpURLConnection.HTTP_BAD_REQUEST));
  }

  @Test
  public void canDeleteAnInst() {

    UUID id = UUID.randomUUID();
    createInstitution(id, "Institute of MetaPhysics", "MPI");
    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();
    send(locInstitutionStorageUrl("/" + id), HttpMethod.DELETE, null,
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(deleteCompleted));
    Response deleteResponse = get(deleteCompleted);
    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
  }

  ////////////// Campus tests
  @Test
  public void canCreateCamp() {

    UUID instId = UUID.randomUUID();
    createInstitution(instId, "Institute of MetaPhysics", "MPI");

    Response response = createCampus(null, "Riverside Campus", "RS", instId);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    assertThat(response.getJson().getString("id"), notNullValue());
    assertThat(response.getJson().getString("name"), is("Riverside Campus"));
    assertThat(response.getJson().getString("code"), is("RS"));
  }

  @Test
  public void cannotCreateCampWithSameName() {

    UUID instId = UUID.randomUUID();
    createInstitution(instId, "Institute of MetaPhysics", "MPI");

    createCampus(null, "Riverside Campus", "RS", instId);
    Response response = createCampus(null, "Riverside Campus", "RS", instId);
    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void cannotCreateCampSameId() {

    UUID instId = UUID.randomUUID();
    createInstitution(instId, "Institute of MetaPhysics", "MPI");

    UUID id = UUID.randomUUID();
    createCampus(id, "Riverside Campus", "RS", instId);
    Response response = createCampus(id, "Campus on the other Side of the River", "OS", instId);
    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void cannotCreateCampWithoutInst() {

    Response response = createCampus(null, "Campus on the other Side of the River", "OS", null);
    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void cannotCreateCampWithoutCode() {

    UUID instId = UUID.randomUUID();
    createInstitution(instId, "Institute of MetaPhysics", "MPI");

    UUID id = UUID.randomUUID();
    Response response = createCampus(id, "Campus on the other Side of the River", null, instId);

    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void canGetCampById() {

    UUID instId = UUID.randomUUID();
    createInstitution(instId, "Institute of MetaPhysics", "MPI");

    UUID id = UUID.randomUUID();
    createCampus(id, "Riverside Campus", "RS", instId);
    Response getResponse = getCampById(id);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonObject item = getResponse.getJson();
    assertThat(item.getString("id"), is(id.toString()));
    assertThat(item.getString("name"), is("Riverside Campus"));
    assertThat(item.getJsonObject("metadata").getString("createdByUserId"),
      is("test_user"));  // The userId header triggers creation of metadata
  }

  @Test
  public void cannotGetCampWrongId() {
    UUID instId = UUID.randomUUID();
    createInstitution(instId, "Institute of MetaPhysics", "MPI");

    createCampus(null, "Riverside Campus", "RS", instId);

    UUID id = UUID.randomUUID();
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    send(locCampusStorageUrl("/" + id), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(getCompleted));
    Response getResponse = get(getCompleted);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @Test
  public void canListCamps() {

    UUID instId = UUID.randomUUID();
    createInstitution(instId, "Institute of MetaPhysics", "MPI");
    createCampus(null, "Riverside Campus", "RSC", instId);
    createCampus(null, "Other Side Campus", "OSC", instId);
    createCampus(null, "Underwater Location", "OSC", instId);

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    send(locCampusStorageUrl("/?query=name=Campus"), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(getCompleted));

    Response getResponse = get(getCompleted);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonObject item = getResponse.getJson();
    assertThat(item.getInteger("totalRecords"), is(2));
  }

  @Test
  public void canUpdateCamp() {

    UUID instId = UUID.randomUUID();
    createInstitution(instId, "Institute of MetaPhysics", "MPI");

    UUID id = UUID.randomUUID();
    createCampus(id, "Riverside Campus", "MPI", instId);

    JsonObject updateRequest = new JsonObject()
      .put("id", id.toString())
      .put("name", "The Other Campus")
      .put("institutionId", instId.toString())
      .put("code", "MPA");

    CompletableFuture<Response> updated = new CompletableFuture<>();

    send(locCampusStorageUrl("/" + id), HttpMethod.PUT,
      updateRequest.toString(), SUPPORTED_CONTENT_TYPE_JSON_DEF,
      ResponseHandler.any(updated));
    Response updateResponse = get(updated);
    assertThat(updateResponse, statusCodeIs(HttpURLConnection.HTTP_NO_CONTENT));
    assertThat(updateResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    Response getResponse = getCampById(id);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonObject item = getResponse.getJson();
    assertThat(item.getString("id"), is(id.toString()));
    assertThat(item.getString("name"), is("The Other Campus"));
    assertThat(item.getString("code"), is("MPA"));
  }

  @Test
  public void cannotUpdateCampId() {

    UUID instId = UUID.randomUUID();
    createInstitution(instId, "Institute of MetaPhysics", "MPI");

    UUID id = UUID.randomUUID();
    createCampus(id, "Riverside Campus", "MPI", instId);
    JsonObject updateRequest = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("name", "The Other Campus")
      .put("institutionId", instId.toString())
      .put("code", "MPA");

    CompletableFuture<Response> updated = new CompletableFuture<>();
    send(locCampusStorageUrl("/" + id), HttpMethod.PUT,
      updateRequest.toString(), SUPPORTED_CONTENT_TYPE_JSON_DEF,
      ResponseHandler.any(updated));
    Response updateResponse = get(updated);
    assertThat(updateResponse, statusCodeIs(HttpURLConnection.HTTP_BAD_REQUEST));
  }

  @Test
  public void canDeleteCamp() {

    UUID instId = UUID.randomUUID();
    createInstitution(instId, "Institute of MetaPhysics", "MPI");
    UUID id = UUID.randomUUID();
    createCampus(id, "Riverside Campus", "RS", instId);
    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();
    send(locCampusStorageUrl("/" + id), HttpMethod.DELETE, null,
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(deleteCompleted));
    Response deleteResponse = get(deleteCompleted);
    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
  }

  @Test
  public void cannotDeleteInstUsedByCamp() {

    UUID instId = UUID.randomUUID();
    createInstitution(instId, "Institute of MetaPhysics", "MPI");
    createCampus(null, "Riverside Campus", "RS", instId);
    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();
    send(locInstitutionStorageUrl("/" + instId), HttpMethod.DELETE, null,
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(deleteCompleted));
    Response deleteResponse = get(deleteCompleted);
    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
  }

  @Test
  public void canCreateLib() {

    UUID instId = UUID.randomUUID();
    createInstitution(instId, "Institute of MetaPhysics", "MPI");
    UUID campId = UUID.randomUUID();
    createCampus(campId, "Riverside Campus", "RS", instId);

    Response response = createLibrary(null, "Main Library", "RS", campId);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    assertThat(response.getJson().getString("id"), notNullValue());
    assertThat(response.getJson().getString("name"), is("Main Library"));
    assertThat(response.getJson().getString("code"), is("RS"));
  }

  @Test
  public void cannotCreateLibWithSameName() {

    UUID instId = UUID.randomUUID();
    createInstitution(instId, "Institute of MetaPhysics", "MPI");
    UUID campId = UUID.randomUUID();
    createCampus(campId, "Riverside Campus", "RS", instId);

    createLibrary(null, "Main Library", "RS", campId);
    Response response = createLibrary(null, "Main Library", "RS", campId);
    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void cannotCreateLibSameId() {

    UUID instId = UUID.randomUUID();
    createInstitution(instId, "Institute of MetaPhysics", "MPI");
    UUID campId = UUID.randomUUID();
    createCampus(campId, "Riverside Campus", "RS", instId);

    UUID id = UUID.randomUUID();
    createLibrary(id, "Main Library", "RS", campId);
    Response response = createLibrary(id, "Library on the other Side of the River", "OS", campId);
    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void cannotCreateLibWithoutCode() {

    UUID instId = UUID.randomUUID();
    createInstitution(instId, "Institute of MetaPhysics", "MPI");
    UUID campId = UUID.randomUUID();
    createCampus(campId, "Riverside Campus", "RS", instId);

    UUID id = UUID.randomUUID();
    Response response = createLibrary(id, "Main Library", null, campId);

    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void canGetLibById() {

    UUID instId = UUID.randomUUID();
    createInstitution(instId, "Institute of MetaPhysics", "MPI");
    UUID campId = UUID.randomUUID();
    createCampus(campId, "Riverside Campus", "RS", instId);

    UUID id = UUID.randomUUID();
    createLibrary(id, "Main Library", "ML", campId);
    Response getResponse = getLibById(id);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonObject item = getResponse.getJson();
    assertThat(item.getString("id"), is(id.toString()));
    assertThat(item.getString("name"), is("Main Library"));
    assertThat(item.getString("campusId"), is(campId.toString()));
    assertThat(item.getJsonObject("metadata").getString("createdByUserId"),
      is("test_user"));  // The userId header triggers creation of metadata
  }

  @Test
  public void cannotGetLibWrongId() {

    UUID instId = UUID.randomUUID();
    createInstitution(instId, "Institute of MetaPhysics", "MPI");
    UUID campId = UUID.randomUUID();
    createCampus(campId, "Riverside Campus", "RS", instId);
    createLibrary(null, "Main Library", "ML", campId);
    UUID id = UUID.randomUUID();
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    send(locLibraryStorageUrl("/" + id), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(getCompleted));
    Response getResponse = get(getCompleted);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @Test
  public void canListLibs() {

    UUID instId = UUID.randomUUID();
    createInstitution(instId, "Institute of MetaPhysics", "MPI");
    UUID campId = UUID.randomUUID();
    createCampus(campId, "Riverside Campus", "RS", instId);
    createLibrary(null, "Main Library", "ML", campId);
    createLibrary(null, "Side Library", "SL", campId);
    createLibrary(null, "The Book Store", "BS", campId);

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    send(locLibraryStorageUrl("/?query=name=LiBRaRy"), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(getCompleted));

    Response getResponse = get(getCompleted);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonObject item = getResponse.getJson();
    assertThat(item.getInteger("totalRecords"), is(2));
  }

  @Test
  public void canUpdateLib() {

    UUID instId = UUID.randomUUID();
    createInstitution(instId, "Institute of MetaPhysics", "MPI");
    UUID campId = UUID.randomUUID();
    createCampus(campId, "Riverside Campus", "RS", instId);
    UUID id = UUID.randomUUID();
    createLibrary(id, "Main Library", "MPI", campId);

    JsonObject updateRequest = new JsonObject()
      .put("id", id.toString())
      .put("name", "The Other Library")
      .put("campusId", campId.toString())
      .put("code", "MPA");

    CompletableFuture<Response> updated = new CompletableFuture<>();

    send(locLibraryStorageUrl("/" + id), HttpMethod.PUT,
      updateRequest.toString(), SUPPORTED_CONTENT_TYPE_JSON_DEF,
      ResponseHandler.any(updated));
    Response updateResponse = get(updated);
    assertThat(updateResponse, statusCodeIs(HttpURLConnection.HTTP_NO_CONTENT));
    assertThat(updateResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    Response getResponse = getLibById(id);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonObject item = getResponse.getJson();
    assertThat(item.getString("id"), is(id.toString()));
    assertThat(item.getString("name"), is("The Other Library"));
    assertThat(item.getString("code"), is("MPA"));
  }

  public void cannotUpdateLibId() {
    UUID instId = UUID.randomUUID();
    createInstitution(instId, "Institute of MetaPhysics", "MPI");
    UUID campId = UUID.randomUUID();
    createCampus(campId, "Riverside Campus", "RS", instId);
    UUID id = UUID.randomUUID();
    createLibrary(id, "Main Library", "MPI", campId);

    JsonObject updateRequest = new JsonObject()
      .put("id", UUID.randomUUID().toString());

    CompletableFuture<Response> updated = new CompletableFuture<>();
    send(locInstitutionStorageUrl("/" + id), HttpMethod.PUT,
      updateRequest.toString(), SUPPORTED_CONTENT_TYPE_JSON_DEF,
      ResponseHandler.any(updated));
    Response updateResponse = get(updated);
    assertThat(updateResponse, statusCodeIs(HttpURLConnection.HTTP_BAD_REQUEST));
  }

  @Test
  public void canDeleteLib() {

    UUID instId = UUID.randomUUID();
    createInstitution(instId, "Institute of MetaPhysics", "MPI");
    UUID campId = UUID.randomUUID();
    createCampus(campId, "Riverside Campus", "RS", instId);

    UUID id = UUID.randomUUID();

    createLibrary(id, "Main Library", "RS", campId);

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();

    send(locLibraryStorageUrl("/" + id), HttpMethod.DELETE, null,
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(deleteCompleted));

    Response deleteResponse = get(deleteCompleted);
    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
  }

  @Test
  public void cannotDeleteCampUsedByLib() {

    UUID instId = UUID.randomUUID();
    createInstitution(instId, "Institute of MetaPhysics", "MPI");
    UUID campId = UUID.randomUUID();
    createCampus(campId, "Riverside Campus", "RS", instId);
    createLibrary(null, "Main Library", "RS", campId);
    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();
    send(locCampusStorageUrl("/" + campId), HttpMethod.DELETE, null,
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(deleteCompleted));
    Response deleteResponse = get(deleteCompleted);
    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
  }

  private Response getInstById(UUID id) {

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    send(locInstitutionStorageUrl("/" + id.toString()), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(getCompleted));

    return get(getCompleted);
  }

  private Response getCampById(UUID id) {

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    send(locCampusStorageUrl("/" + id.toString()), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(getCompleted));

    return get(getCompleted);
  }

  private Response getLibById(UUID id) {

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    send(locLibraryStorageUrl("/" + id.toString()), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(getCompleted));

    return get(getCompleted);
  }

}
