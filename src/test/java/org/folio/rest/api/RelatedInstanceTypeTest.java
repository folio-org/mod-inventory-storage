package org.folio.rest.api;

import static org.folio.rest.support.HttpResponseMatchers.statusCodeIs;
import static org.folio.rest.support.http.InterfaceUrls.relatedInstanceTypesStorageUrl;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.rest.support.AdditionalHttpStatusCodes;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.junit.Before;
import org.junit.Test;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;

public class RelatedInstanceTypeTest extends TestBaseWithInventoryUtil {

  private static final String SUPPORTED_CONTENT_TYPE_JSON_DEF = "application/json";

  @Before
  public void beforeEach()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    StorageTestSuite.deleteAll(relatedInstanceTypesStorageUrl(""));
  }

  @Test
  public void canCreateRelatedInstanceType()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    Response response = createRelatedInstanceType("Reenactment of");

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    assertThat(response.getJson().getString("id"), notNullValue());
    assertThat(response.getJson().getString("name"), is("Reenactment of"));
  }

  @Test
  public void cannotCreateRelatedInstanceTypeWithSameName()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    createRelatedInstanceType("Reenactment of");

    Response response = createRelatedInstanceType("Reenactment of");

    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void cannotCreateRelatedInstanceTypeWithSameId()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    createRelatedInstanceType(id, "Reenactment of");

    Response response = createRelatedInstanceType(id, "Revision of");

    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void canGetARelatedInstanceTypeById()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    createRelatedInstanceType(id, "Reenactment of");

    Response getResponse = getById(id);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject item = getResponse.getJson();

    assertThat(item.getString("id"), is(id.toString()));
    assertThat(item.getString("name"), is("Reenactment of"));
  }

  @Test
  public void canUpdateARelatedInstanceType()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    createRelatedInstanceType(id, "Reenactment of");

    JsonObject updateRequest = new JsonObject()
      .put("id", id.toString())
      .put("name", "Re-enactment of");

    CompletableFuture<Response> updated = new CompletableFuture<>();

    send(relatedInstanceTypesStorageUrl("/" + id.toString()).toString(), HttpMethod.PUT,
      updateRequest.toString(), SUPPORTED_CONTENT_TYPE_JSON_DEF,
      ResponseHandler.any(updated));

    Response updateResponse = updated.get(5, TimeUnit.SECONDS);

    assertThat(updateResponse, statusCodeIs(HttpURLConnection.HTTP_NO_CONTENT));

    assertThat(updateResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    Response getResponse = getById(id);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject item = getResponse.getJson();

    assertThat(item.getString("id"), is(id.toString()));
    assertThat(item.getString("name"), is("Re-enactment of"));
  }

  @Test
  public void cannotUpdateARelatedInstanceTypeThatDoesNotExist()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    JsonObject updateRequest = new JsonObject()
      .put("id", id.toString())
      .put("name", "Revision of");

    CompletableFuture<Response> updated = new CompletableFuture<>();

    send(relatedInstanceTypesStorageUrl("/" + id.toString()).toString(), HttpMethod.PUT,
      updateRequest.toString(), SUPPORTED_CONTENT_TYPE_JSON_DEF,
      ResponseHandler.any(updated));

    Response updateResponse = updated.get(5, TimeUnit.SECONDS);

    assertThat(updateResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @Test
  public void cannotGetARelatedInstanceTypeThatDoesNotExist()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    send(relatedInstanceTypesStorageUrl("/" + UUID.randomUUID().toString()).toString(), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(getCompleted));

    Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @Test
  public void canGetAllRelatedInstanceTypes()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    createRelatedInstanceType(UUID.randomUUID(), "Reenactment of");
    createRelatedInstanceType(UUID.randomUUID(), "Revision of");

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    send(relatedInstanceTypesStorageUrl("").toString(), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(getCompleted));

    Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(getResponse.getJson().getInteger("totalRecords"), is(2));
  }

  @Test
  public void canDeleteAnUnusedRelatedInstanceType()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    createRelatedInstanceType(id, "Reenactment of");

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();

    send(relatedInstanceTypesStorageUrl("/" + id.toString()).toString(), HttpMethod.DELETE, null,
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(deleteCompleted));

    Response deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);

    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
  }

  @Test
  public void cannotDeleteARelatedInstanceTypeThatCannotBeFound()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();

    send(relatedInstanceTypesStorageUrl("/" + UUID.randomUUID().toString()).toString(),
      HttpMethod.DELETE, null, SUPPORTED_CONTENT_TYPE_JSON_DEF,
      ResponseHandler.text(deleteCompleted));

    Response deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);

    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  private Response createRelatedInstanceType(String name)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<Response> createRelatedInstanceType = new CompletableFuture<>();
    String createURL = relatedInstanceTypesStorageUrl("").toString();

    send(createURL, HttpMethod.POST, new JsonObject().put("name", name).toString(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(createRelatedInstanceType));

    return createRelatedInstanceType.get(5, TimeUnit.SECONDS);
  }

  private Response createRelatedInstanceType(UUID id, String name)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<Response> createRelatedInstanceType = new CompletableFuture<>();

    JsonObject request = new JsonObject()
      .put("id", id.toString())
      .put("name", name);

    send(relatedInstanceTypesStorageUrl("").toString(), HttpMethod.POST, request.toString(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(createRelatedInstanceType));

    return createRelatedInstanceType.get(5, TimeUnit.SECONDS);
  }

  private Response getById(UUID id)
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    send(relatedInstanceTypesStorageUrl("/" + id.toString()).toString(), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(getCompleted));

    return getCompleted.get(5, TimeUnit.SECONDS);
  }
}
