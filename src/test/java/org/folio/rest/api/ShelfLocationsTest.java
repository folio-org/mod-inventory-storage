package org.folio.rest.api;

import static org.folio.rest.support.HttpResponseMatchers.statusCodeIs;
import static org.folio.rest.support.http.InterfaceUrls.ShelfLocationsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.holdingsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.instancesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.loanTypesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.locCampusStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.locInstitutionStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.locLibraryStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.locationsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.materialTypesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.servicePointsUrl;
import static org.folio.rest.support.http.InterfaceUrls.servicePointsUsersUrl;
import static org.folio.utility.LocationUtility.clearServicePointIDs;
import static org.folio.utility.LocationUtility.createLocation;
import static org.folio.utility.LocationUtility.createLocationUnits;
import static org.folio.utility.LocationUtility.getCampusID;
import static org.folio.utility.LocationUtility.getInstitutionID;
import static org.folio.utility.LocationUtility.getLibraryID;
import static org.folio.utility.LocationUtility.getServicePointIDs;
import static org.folio.utility.ModuleUtility.getVertx;
import static org.folio.utility.RestUtility.send;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.SneakyThrows;
import org.folio.rest.support.HttpClient;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.client.LoanTypesClient;
import org.folio.rest.support.client.MaterialTypesClient;
import org.junit.Before;
import org.junit.Test;

public class ShelfLocationsTest extends TestBase {
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
    StorageTestSuite.deleteAll(servicePointsUrl(""));
    StorageTestSuite.deleteAll(servicePointsUsersUrl(""));

    new LoanTypesClient(
      new HttpClient(getVertx()),
      loanTypesStorageUrl("")).create("Can Circulate");

    new MaterialTypesClient(
      new HttpClient(getVertx()),
      materialTypesStorageUrl("")).create("Journal");

    clearServicePointIDs();
    createLocationUnits(true);
    removeAllEvents();
  }

  @Test
  public void canCreateShelfLocation()
      throws InterruptedException,
      ExecutionException,
      TimeoutException,
      MalformedURLException {

    Response response = createLocation(null, "Main Library",
        getInstitutionID(), getCampusID(), getLibraryID(), "PI/CC/ML/X",
        getServicePointIDs());

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    assertThat(response.getJson().getString("id"), notNullValue());
    assertThat(response.getJson().getString("name"), is("Main Library"));
  }

  @Test
  public void canGetAShelfLocationById()
      throws InterruptedException,
      ExecutionException,
      TimeoutException,
      MalformedURLException {

    UUID id = UUID.randomUUID();
    Response createResponse = createLocation(id, "Main Library",
        getInstitutionID(), getCampusID(), getLibraryID(), "PI/CC/ML/X",
        getServicePointIDs());
    assertThat(createResponse, statusCodeIs(201));

    Response getResponse = getById(id);
    assertThat(getResponse, statusCodeIs(HttpURLConnection.HTTP_OK));
    JsonObject item = getResponse.getJson();
    assertThat(item.getString("id"), is(id.toString()));
    assertThat(item.getString("name"), is("Main Library"));
  }

  private Response getById(UUID id)
      throws InterruptedException,
      ExecutionException,
      TimeoutException,
      MalformedURLException {

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    send(ShelfLocationsStorageUrl("/" + id.toString()), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(getCompleted));

    return getCompleted.get(10, TimeUnit.SECONDS);
  }

}

