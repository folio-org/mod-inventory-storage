package org.folio.rest.api;

import static org.folio.rest.support.http.InterfaceUrls.subjectSourcesUrl;
import static org.folio.utility.ModuleUtility.getClient;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.folio.utility.RestUtility.send;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.http.ResourceClient;
import org.junit.BeforeClass;
import org.junit.Test;

public class SubjectSourceTest extends TestBase {

  private static ResourceClient subjectSourceClient;
  private static final String SUBJECT_SOURCE_ID = "e894d0dc-621d-4b1d-98f6-6f7120eb0d40";

  @BeforeClass
  public static void beforeAll() {
    TestBase.beforeAll();
    subjectSourceClient = ResourceClient.forSubjectSources(getClient());
  }

  @Test
  public void cannotCreateSubjectSourceWithDuplicateName()
    throws InterruptedException, TimeoutException,
    ExecutionException {

    JsonObject subjectSource = new JsonObject()
      .put("name", "Library of Congress Subject Headings2")
      .put("source", "local");

    subjectSourceClient.create(subjectSource);

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();
    getClient().post(subjectSourcesUrl(""), subjectSource, TENANT_ID, ResponseHandler.json(postCompleted));

    Response response = postCompleted.get(TIMEOUT, TimeUnit.SECONDS);
    assertThat(response.getStatusCode(), is(422));

    JsonArray errors = response.getJson().getJsonArray("errors");
    assertThat(errors.size(), is(1));
    assertTrue(errors.getJsonObject(0).getString("message").contains("(jsonb ->> 'name'::text)) value already exists"));
  }

  @Test
  public void cannotCreateSubjectSourceWithDuplicateCode()
    throws InterruptedException, TimeoutException,
    ExecutionException {

    JsonObject subjectSource = new JsonObject()
      .put("name", "Test")
      .put("code", "test")
      .put("source", "local");

    subjectSourceClient.create(subjectSource);

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();
    getClient().post(subjectSourcesUrl(""), subjectSource.put("name", "Test2"),
      TENANT_ID, ResponseHandler.json(postCompleted));

    Response response = postCompleted.get(TIMEOUT, TimeUnit.SECONDS);
    assertThat(response.getStatusCode(), is(422));

    JsonArray errors = response.getJson().getJsonArray("errors");
    assertThat(errors.size(), is(1));
    assertTrue(errors.getJsonObject(0).getString("message").contains("(jsonb ->> 'code'::text)) value already exists"));
  }

  @Test
  public void cannotCreateSubjectSourceWithSourceFolio() {
    JsonObject subjectSource = new JsonObject()
      .put("name", "Library of Congress Subject Headings2")
      .put("source", "folio");

    Response response = createSubjectSource(subjectSource);

    JsonArray errors = response.getJson().getJsonArray("errors");
    assertEquals(422, response.getStatusCode());
    assertEquals(1, errors.size());
    assertEquals(
      "Illegal operation: Source field cannot be set to folio",
      errors.getJsonObject(0).getString("message"));
  }

  @Test
  public void cannotUpdateSubjectSourceWithSourceFolio() {
    JsonObject subjectSource = new JsonObject()
      .put("name", "Library of Congress Subject Headings")
      .put("source", "local");

    Response response = updateSubjectSource(SUBJECT_SOURCE_ID, subjectSource);

    JsonArray errors = response.getJson().getJsonArray("errors");
    assertEquals(422, response.getStatusCode());
    assertEquals(1, errors.size());
    assertEquals(
      "Illegal operation: Source folio cannot be updated",
      errors.getJsonObject(0).getString("message"));
  }

  private Response createSubjectSource(JsonObject object) {

    CompletableFuture<Response> createSubjectSource = new CompletableFuture<>();

    send(subjectSourcesUrl("").toString(), HttpMethod.POST, object.toString(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(createSubjectSource));

    return get(createSubjectSource);
  }

  private Response updateSubjectSource(String id, JsonObject object) {
    CompletableFuture<Response> updateSubjectSource = new CompletableFuture<>();

    send(subjectSourcesUrl("/" + id).toString(), HttpMethod.PUT, object.toString(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(updateSubjectSource));

    return get(updateSubjectSource);
  }
}
