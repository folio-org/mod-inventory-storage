package org.folio.rest.impl;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import javax.ws.rs.core.Response;
import junitparams.JUnitParamsRunner;
import org.folio.rest.api.TestBase;
import org.folio.rest.jaxrs.model.CallNumberType;
import org.folio.rest.jaxrs.resource.CallNumberTypes;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
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
  public void shouldHandleException_whenPut() {
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
  public void shouldHandleException_whenDelete() {
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

  @Test
  public void shouldCallPgUtilPutWithProperArgumentsAndHandleError() {
    //Given
    var callNumberTypesApi = Mockito.spy(CallNumberTypeApi.class);
    var entity = new CallNumberType();
    Map<String, String> okapiHeaders = Collections.emptyMap();

    Handler<AsyncResult<Response>> errorHandler = Mockito.mock(Handler.class);
    Context vertex = Mockito.mock(Context.class);
    var mockedPgClient = Mockito.mock(PostgresClient.class);
    when(mockedPgClient.getById(any(), any(), eq(CallNumberType.class))).thenReturn(Future.succeededFuture(entity));

    try (MockedStatic<TenantTool> mockedTenantTool = Mockito.mockStatic(TenantTool.class);
         MockedStatic<PgUtil> mockedPgUtil = Mockito.mockStatic(PgUtil.class);
         MockedStatic<PostgresClient> mockedPgClientStat = Mockito.mockStatic(PostgresClient.class)) {
      mockedTenantTool.when(() -> TenantTool.tenantId(anyMap())).thenReturn("Test");
      mockedPgClientStat.when(() -> PostgresClient.getInstance(any(), any())).thenReturn(mockedPgClient);
      mockedPgUtil.when(() -> PgUtil.put(
        anyString(),
        any(),
        any(),
        any(),
        any(),
        eq(CallNumberTypes.PutCallNumberTypesByIdResponse.class)
      )).thenThrow(new RuntimeException("Test"));

      //When
      callNumberTypesApi.putCallNumberTypesById("id",
        "us",
        entity,
        okapiHeaders,
        errorHandler,
        vertex);

      //Then
      mockedPgUtil.verify(() -> PgUtil.put("call_number_type",
        entity,
        "id",
        okapiHeaders,
        vertex,
        CallNumberTypes.PutCallNumberTypesByIdResponse.class));
      Mockito.verify(errorHandler).handle(any());
    }
  }
}
