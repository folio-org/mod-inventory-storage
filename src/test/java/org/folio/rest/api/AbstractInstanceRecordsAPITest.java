package org.folio.rest.api;

import static org.awaitility.Awaitility.await;
import static org.folio.utility.ModuleUtility.getVertx;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import javax.ws.rs.core.Response;

import org.folio.rest.RestVerticle;
import org.folio.rest.impl.AbstractInstanceRecordsAPI;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.AdditionalAnswers;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.Tuple;

@RunWith(VertxUnitRunner.class)
public class AbstractInstanceRecordsAPITest extends TestBase {
  private static final Map<String,String> okapiHeaders = Collections.singletonMap(
      RestVerticle.OKAPI_HEADER_TENANT, TENANT_ID);

  static class MyAbstractInstanceRecordsAPI extends AbstractInstanceRecordsAPI {
    public void fetchRecordsByQuery(String sql, RoutingContext routingContext,
        Supplier<Tuple> paramsSupplier, Handler<AsyncResult<Response>> asyncResultHandler) {

      fetchRecordsByQuery(sql, paramsSupplier, routingContext, okapiHeaders,
          asyncResultHandler, Vertx.vertx().getOrCreateContext(), null);
    }
  }

  @Test
  public void shouldRespondWith500StatusWhenErrorsOccursWhilstFetchingRecords(TestContext testContext) {
    RoutingContext routingContext = mock(RoutingContext.class);
    when(routingContext.response()).thenReturn(mock(HttpServerResponse.class));
    new MyAbstractInstanceRecordsAPI().fetchRecordsByQuery("SELECT 1",
        routingContext, null, testContext.asyncAssertSuccess(response -> {
          assertThat(response.getStatus(), is(500));
        }));
  }

  @Test
  public void shouldCloseTcpWhenFailureAfterHttpHeadHasBeenWritten(TestContext testContext) {
    HttpServerResponse httpServerResponse = mock(HttpServerResponse.class);
    when(httpServerResponse.headWritten()).thenReturn(true);
    RoutingContext routingContext = mock(RoutingContext.class);
    when(routingContext.response()).thenReturn(httpServerResponse);
    new MyAbstractInstanceRecordsAPI().fetchRecordsByQuery("SELECT 1",
        routingContext, null, testContext.asyncAssertSuccess(response -> {
          assertThat(response, is(nullValue()));
          verify(httpServerResponse).close();
        }));
  }

  private HttpServerResponse getHttpServerResponseMock() {
    HttpServerResponse httpServerResponse = mock(HttpServerResponse.class);
    doAnswer(AdditionalAnswers.answerVoid(
        (Handler<AsyncResult<Void>> handler) -> handler.handle(Future.succeededFuture())))
        .when(httpServerResponse).end(any(Handler.class));
    return httpServerResponse;
  }

  @Test
  public void canFetch300Records() {
    RoutingContext routingContext = mock(RoutingContext.class);
    HttpServerResponse httpServerResponse = getHttpServerResponseMock();
    when(routingContext.response()).thenReturn(httpServerResponse);

    new MyAbstractInstanceRecordsAPI().fetchRecordsByQuery("SELECT generate_series(1, 300)",
        routingContext, Tuple::tuple, nu -> {});

    verify(httpServerResponse, timeout(1000).times(300)).write(anyString());
  }

  @Test
  public void canHandleWriteQueueFull() {
    Handler<?>[] drainHandler = new Handler[1];
    AtomicInteger drainCount = new AtomicInteger();

    RoutingContext routingContext = mock(RoutingContext.class);

    HttpServerResponse httpServerResponse = getHttpServerResponseMock();
    when(routingContext.response()).thenReturn(httpServerResponse);

    doAnswer(AdditionalAnswers.answerVoid((Handler handler) -> drainHandler[0] = handler))
      .when(httpServerResponse).drainHandler(any());

    doAnswer(
      invocation -> {
        getVertx().runOnContext(run -> {
          drainCount.getAndIncrement();
          drainHandler[0].handle(null);
        });
        return true;
      })
      .when(httpServerResponse).writeQueueFull();

    new MyAbstractInstanceRecordsAPI().fetchRecordsByQuery("SELECT generate_series(1, 300)",
        routingContext, Tuple::tuple, nu -> {});

    await().until(drainCount::get, is(300));

    verify(httpServerResponse, times(300)).write(anyString());
  }
}
