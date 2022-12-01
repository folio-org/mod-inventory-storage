package org.folio.rest.api;

import static org.folio.rest.support.http.InterfaceUrls.holdingsTypesUrl;
import static org.folio.utility.ModuleUtility.getClient;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.net.MalformedURLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.http.ResourceClient;
import org.junit.BeforeClass;
import org.junit.Test;

public class HoldingsTypeTest extends TestBase {

  private static ResourceClient holdingsTypeClient;

  @BeforeClass
  public static void beforeAll() {
    TestBase.beforeAll();

    holdingsTypeClient = ResourceClient.forHoldingsType(getClient());
  }

  @Test
  public void cannotCreateHoldingsTypeWithDuplicateName()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    JsonObject holdingsType = new JsonObject()
      .put("name", "Custom")
      .put("source", "folio");

    holdingsTypeClient.create(holdingsType);

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();
    getClient().post(holdingsTypesUrl(""), holdingsType, TENANT_ID, ResponseHandler.json(postCompleted));

    Response response = postCompleted.get(10, TimeUnit.SECONDS);
    assertThat(response.getStatusCode(), is(422));

    JsonArray errors = response.getJson().getJsonArray("errors");
    assertThat(errors.size(), is(1));

    JsonObject error = errors.getJsonObject(0);
    assertThat(error.getString("code"), is("name.duplicate"));
    assertThat(error.getString("message"), is("Cannot create entity; name is not unique"));

    JsonArray errorParameters = error.getJsonArray("parameters");
    assertThat(errorParameters.size(), is(1));

    JsonObject parameter = errorParameters.getJsonObject(0);
    assertThat(parameter.getString("key"), is("fieldLabel"));
    assertThat(parameter.getString("value"), is("name"));
  }
}
