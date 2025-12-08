package org.folio.rest.impl;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

@RunWith(JUnitParamsRunner.class)
public class CallNumberTypeApiTest extends TestBase {

  private static final String NAME_FIELD = "name";
  private static final String SOURCE_FIELD = "source";

  @Test
  public void shouldRespondWith400_whenAttemptToDeleteSystemType() {
    var callNumberTypeId = create("foo", "system");

    var response = callNumberTypesClient.attemptToDelete(callNumberTypeId);
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
    assertCallNumberType(callNumberTypeId, "foo", "system");
  }

  @Test
  public void shouldRespondWith400_whenAttemptToUpdateSystemType() {
    var callNumberTypeId = create("bar", "system");

    var requestBody = new JsonObject().put(NAME_FIELD, "updated").put(SOURCE_FIELD, "local");
    var response = callNumberTypesClient.attemptToReplace(callNumberTypeId, requestBody);
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
    assertCallNumberType(callNumberTypeId, "bar", "system");
  }

  @Test
  public void shouldRespondWith204_whenAttemptToDeleteNotSystemType() {
    var callNumberTypeId = create("baz", "non system");
    assertCallNumberType(callNumberTypeId, "baz", "non system");

    var response = callNumberTypesClient.attemptToDelete(callNumberTypeId);
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
  }

  @Test
  public void shouldHandleException_whenPut() {
    var callNumberTypesApi = Mockito.spy(CallNumberTypeApi.class);
    var errorHandler = Mockito.<Handler<AsyncResult<Response>>>mock();
    var pgClient = mock(PostgresClient.class);
    try (MockedStatic<TenantTool> mockedTenantTool = Mockito.mockStatic(TenantTool.class);
         MockedStatic<PgUtil> mockedPgUtil = Mockito.mockStatic(PgUtil.class)) {
      mockedTenantTool.when(() -> TenantTool.tenantId(anyMap())).thenReturn("Test");
      mockedPgUtil.when(() -> PgUtil.postgresClient(any(), any())).thenReturn(pgClient);
      when(pgClient.getById(anyString(), any(), ArgumentMatchers.<Class<Object>>any()))
        .thenReturn(Future.succeededFuture(new CallNumberType().withSource("system")));

      callNumberTypesApi.putCallNumberTypesById(null,
        null,
        null,
        errorHandler,
        mock(Context.class));

      Mockito.verify(errorHandler).handle(any());
    }
  }

  @Test
  public void shouldHandleException_whenDelete() {
    var callNumberTypesApi = Mockito.spy(CallNumberTypeApi.class);
    var errorHandler = Mockito.<Handler<AsyncResult<Response>>>mock();
    var pgClient = mock(PostgresClient.class);
    try (MockedStatic<TenantTool> mockedTenantTool = Mockito.mockStatic(TenantTool.class);
         MockedStatic<PgUtil> mockedPgUtil = Mockito.mockStatic(PgUtil.class)) {
      mockedTenantTool.when(() -> TenantTool.tenantId(anyMap())).thenReturn("Test");
      mockedPgUtil.when(() -> PgUtil.postgresClient(any(), any())).thenReturn(pgClient);
      when(pgClient.getById(anyString(), any(), ArgumentMatchers.<Class<Object>>any()))
        .thenReturn(Future.succeededFuture(new CallNumberType().withSource("system")));

      callNumberTypesApi.deleteCallNumberTypesById(null,
        null,
        errorHandler,
        mock(Context.class));

      Mockito.verify(errorHandler).handle(any());
    }
  }

  @Test
  public void shouldCallPgUtilPutWithProperArgumentsAndHandleError() {
    //Given
    var callNumberTypesApi = Mockito.spy(CallNumberTypeApi.class);
    var entity = new CallNumberType();
    var id = "id";
    Map<String, String> okapiHeaders = Collections.emptyMap();

    Handler<AsyncResult<Response>> errorHandler = Mockito.mock();
    Context vertex = mock(Context.class);
    var mockedPgClient = mock(PostgresClient.class);
    when(mockedPgClient.getById(any(), any(), eq(CallNumberType.class))).thenReturn(Future.succeededFuture(entity));

    try (MockedStatic<TenantTool> mockedTenantTool = Mockito.mockStatic(TenantTool.class);
         MockedStatic<PgUtil> mockedPgUtil = Mockito.mockStatic(PgUtil.class);
         MockedStatic<PostgresClient> mockedPgClientStat = Mockito.mockStatic(PostgresClient.class)) {
      configureMocksForPutTest(mockedTenantTool, mockedPgUtil, mockedPgClientStat, mockedPgClient);

      //When
      callNumberTypesApi.putCallNumberTypesById(id, entity, okapiHeaders, errorHandler, vertex);

      //Then
      verifyPutCallWasMade(mockedPgUtil, entity, id, okapiHeaders, vertex);
      Mockito.verify(errorHandler).handle(any());
    }
  }

  private void configureMocksForPutTest(MockedStatic<TenantTool> mockedTenantTool,
                                        MockedStatic<PgUtil> mockedPgUtil,
                                        MockedStatic<PostgresClient> mockedPgClientStat,
                                        PostgresClient mockedPgClient) {
    mockedTenantTool.when(() -> TenantTool.tenantId(anyMap())).thenReturn("Test");
    mockedPgClientStat.when(() -> PgUtil.postgresClient(any(), any())).thenReturn(mockedPgClient);
    mockedPgUtil.when(() -> PgUtil.put(
      anyString(),
      any(),
      any(),
      any(),
      any(),
      eq(CallNumberTypes.PutCallNumberTypesByIdResponse.class)
    )).thenThrow(new RuntimeException("Test"));
  }

  private void verifyPutCallWasMade(MockedStatic<PgUtil> mockedPgUtil, CallNumberType entity,
                                    String id, Map<String, String> okapiHeaders, Context vertex) {
    mockedPgUtil.verify(() -> PgUtil.put("call_number_type",
      entity,
      id,
      okapiHeaders,
      vertex,
      CallNumberTypes.PutCallNumberTypesByIdResponse.class));
  }

  private UUID create(String name, String source) {
    var requestBody = new JsonObject().put(NAME_FIELD, name).put(SOURCE_FIELD, source);
    var createResponse = callNumberTypesClient.create(requestBody);
    return createResponse.getId();
  }

  private void assertCallNumberType(UUID callNumberTypeId, String name, String source) {
    var callNumberTypeResponse = callNumberTypesClient.getById(callNumberTypeId);
    assertThat(callNumberTypeResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(callNumberTypeResponse.getJson().getString(NAME_FIELD), is(name));
    assertThat(callNumberTypeResponse.getJson().getString(SOURCE_FIELD), is(source));
  }
}
