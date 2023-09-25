package org.folio.rest.impl;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import java.net.HttpURLConnection;
import java.util.UUID;
import javax.ws.rs.core.Response;
import junitparams.JUnitParamsRunner;
import org.folio.rest.api.TestBase;
import org.folio.rest.tools.utils.TenantTool;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

@RunWith(JUnitParamsRunner.class)
public class CallNumberTypeApiTest extends TestBase {

  private static final String NAME_FIELD = "name";
  private static final String NAME_VALUE = "test type";
  private static final String SOURCE_FIELD = "source";
  private static final String SOURCE_VALUE = "system";

  private static UUID callNumberTypeId;

  @BeforeClass
  public static void setUp() {
  }

  @Test
  public void shouldRespondWith400_whenAttemptToDeleteSystemType() {
    var requestBody = new JsonObject().put(NAME_FIELD, NAME_VALUE).put(SOURCE_FIELD, SOURCE_VALUE);
    var createResponse = callNumberTypesClient.create(requestBody);
    callNumberTypeId = createResponse.getId();

    var response = callNumberTypesClient.attemptToDelete(callNumberTypeId);
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));

    var callNumberTypeResponse = callNumberTypesClient.getById(callNumberTypeId);
    assertThat(callNumberTypeResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(callNumberTypeResponse.getJson().getString(NAME_FIELD), is(NAME_VALUE));
    assertThat(callNumberTypeResponse.getJson().getString(SOURCE_FIELD), is(SOURCE_VALUE));
  }

  @Test
  public void shouldRespondWith400_whenAttemptToUpdateSystemType() {
    var requestBody = new JsonObject().put(NAME_FIELD, "updated").put(SOURCE_FIELD, "local");
    var response = callNumberTypesClient.attemptToReplace(callNumberTypeId, requestBody);
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));

    var callNumberTypeResponse = callNumberTypesClient.getById(callNumberTypeId);
    assertThat(callNumberTypeResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(callNumberTypeResponse.getJson().getString(NAME_FIELD), is(NAME_VALUE));
    assertThat(callNumberTypeResponse.getJson().getString(SOURCE_FIELD), is(SOURCE_VALUE));
  }

  @Test
  public void shouldRespondWith204_whenAttemptToDeleteNotSystemType() {
    var requestBody = new JsonObject().put(NAME_FIELD, "NOT SYSTEM TYPE").put(SOURCE_FIELD, "NOT SYSTEM");
    var createResponse = callNumberTypesClient.create(requestBody);
    var callNumberTypeId = createResponse.getId();

    var callNumberTypeResponse = callNumberTypesClient.getById(callNumberTypeId);
    assertThat(callNumberTypeResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(callNumberTypeResponse.getJson().getString(NAME_FIELD), is("NOT SYSTEM TYPE"));
    assertThat(callNumberTypeResponse.getJson().getString(SOURCE_FIELD), is("NOT SYSTEM"));

    var response = callNumberTypesClient.attemptToDelete(callNumberTypeId);
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
  }

  @Test
  public void shouldRespondWith500_whenAttemptToSaveNull() {
    var callNumberTypesApi = Mockito.spy(CallNumberTypeApi.class);
    Handler<AsyncResult<Response>> errorHandler = Mockito.mock(Handler.class);
    try (MockedStatic<TenantTool> mockedTenantTool = Mockito.mockStatic(TenantTool.class)) {
      mockedTenantTool.when(() -> TenantTool.tenantId(any())).thenThrow(new RuntimeException("Test"));

      callNumberTypesApi.putCallNumberTypesById(null,
        null,
        null,
        null,
        errorHandler,
        null);

      Mockito.verify(errorHandler).handle(any());
    }
  }

  @Test
  public void shouldRespondWith500_whenAttemptToDeleteNull() {
    var callNumberTypesApi = Mockito.spy(CallNumberTypeApi.class);
    Handler<AsyncResult<Response>> errorHandler = Mockito.mock(Handler.class);
    try (MockedStatic<TenantTool> mockedTenantTool = Mockito.mockStatic(TenantTool.class)) {
      mockedTenantTool.when(() -> TenantTool.tenantId(any())).thenThrow(new RuntimeException("Test"));

      callNumberTypesApi.deleteCallNumberTypesById(null,
        null,
        null,
        errorHandler,
        null);

      Mockito.verify(errorHandler).handle(any());
    }
  }
}
