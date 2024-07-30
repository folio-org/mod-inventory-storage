package org.folio.rest.api;

import static org.folio.rest.support.http.InterfaceUrls.subjectTypesUrl;
import static org.folio.utility.ModuleUtility.getClient;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.folio.utility.RestUtility.send;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
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

public class SubjectTypeTest extends TestBase {

  private static ResourceClient subjectTypeClient;
  private static final String SUBJECT_TYPE_ID = "d6488f88-1e74-40ce-81b5-b19a928ff5b1";

  @BeforeClass
  public static void beforeAll() {
    TestBase.beforeAll();
    subjectTypeClient = ResourceClient.forSubjectTypes(getClient());
  }

  @Test
  public void cannotCreateSubjectTypeWithDuplicateName()
    throws InterruptedException, TimeoutException,
    ExecutionException {

    JsonObject subjectType = new JsonObject()
      .put("name", "Topical name")
      .put("source", "local");

    subjectTypeClient.create(subjectType);

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();
    getClient().post(subjectTypesUrl(""), subjectType, TENANT_ID, ResponseHandler.json(postCompleted));

    Response response = postCompleted.get(TIMEOUT, TimeUnit.SECONDS);
    assertThat(response.getStatusCode(), is(422));

    JsonArray errors = response.getJson().getJsonArray("errors");
    assertThat(errors.size(), is(1));
  }

  @Test
  public void cannotCreateSubjectTypeWithSourceFolio() {
    JsonObject subjectType = new JsonObject()
      .put("name", "Topical name2")
      .put("source", "folio");

    Response response = createSubjectType(subjectType);

    JsonArray errors = response.getJson().getJsonArray("errors");
    assertEquals(422, response.getStatusCode());
    assertEquals(1, errors.size());
    assertEquals(
      "Illegal operation: Source field cannot be set to folio",
      errors.getJsonObject(0).getString("message"));
  }

  @Test
  public void cannotUpdateSubjectTypeWithSourceFolio() {
    JsonObject subjectType = new JsonObject()
      .put("name", "Topical name2")
      .put("source", "local");

    Response response = updateSubjectType(SUBJECT_TYPE_ID, subjectType);

    JsonArray errors = response.getJson().getJsonArray("errors");
    assertEquals(422, response.getStatusCode());
    assertEquals(1, errors.size());
    assertEquals(
      "Illegal operation: Source field cannot be updated",
      errors.getJsonObject(0).getString("message"));
  }

  private Response createSubjectType(JsonObject object) {

    CompletableFuture<Response> createSubjectType = new CompletableFuture<>();

    send(subjectTypesUrl("").toString(), HttpMethod.POST, object.toString(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(createSubjectType));

    return get(createSubjectType);
  }

  private Response updateSubjectType(String id, JsonObject object) {
    CompletableFuture<Response> updateSubjectType = new CompletableFuture<>();

    send(subjectTypesUrl("/" + id).toString(), HttpMethod.PUT, object.toString(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(updateSubjectType));

    return get(updateSubjectType);
  }

}
