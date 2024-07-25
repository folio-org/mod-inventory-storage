package org.folio.rest.api;

import static org.folio.rest.support.http.InterfaceUrls.subjectTypesUrl;
import static org.folio.utility.ModuleUtility.getClient;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

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
}
