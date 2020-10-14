package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.Tuple;

import static org.folio.rest.api.StorageTestSuite.getVertx;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import javax.ws.rs.core.Response;
import org.folio.rest.RestVerticle;
import org.folio.rest.api.StorageTestSuite;
import org.folio.rest.api.TestBase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.AdditionalAnswers;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

@RunWith(VertxUnitRunner.class)
public class AbstractInstanceRecordsAPITest extends TestBase {
  private static Map<String,String> okapiHeaders = Collections.singletonMap(
      RestVerticle.OKAPI_HEADER_TENANT, StorageTestSuite.TENANT_ID);

  public static void fetchRecordsByQuery(String sql, RoutingContext routingContext,
      Supplier<Tuple> paramsSupplier, Handler<AsyncResult<Response>> asyncResultHandler) {

    new AbstractInstanceRecordsAPI(){}.fetchRecordsByQuery(
        sql, paramsSupplier, routingContext, okapiHeaders,
        asyncResultHandler, Vertx.vertx().getOrCreateContext(), null);
  }

  @Test
  public void test500(TestContext testContext) {
    RoutingContext routingContext = mock(RoutingContext.class);
    when(routingContext.response()).thenReturn(mock(HttpServerResponse.class));
    AbstractInstanceRecordsAPITest.fetchRecordsByQuery("SELECT 1",
        routingContext, null, testContext.asyncAssertSuccess(response -> {
          assertThat(response.getStatus(), is(500));
        }));
  }

  @Test
  public void testTcpClose(TestContext testContext) {
    HttpServerResponse httpServerResponse = mock(HttpServerResponse.class);
    when(httpServerResponse.headWritten()).thenReturn(true);
    RoutingContext routingContext = mock(RoutingContext.class);
    when(routingContext.response()).thenReturn(httpServerResponse);
    AbstractInstanceRecordsAPITest.fetchRecordsByQuery("SELECT 1",
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
  public void testFetch300(TestContext testContext) {
    RoutingContext routingContext = mock(RoutingContext.class);
    HttpServerResponse httpServerResponse = getHttpServerResponseMock();
    when(routingContext.response()).thenReturn(httpServerResponse);
    AbstractInstanceRecordsAPITest.fetchRecordsByQuery("SELECT generate_series(1, 300)",
        routingContext, () -> Tuple.tuple(), testContext.asyncAssertSuccess(response -> {
          assertThat(response, is(nullValue()));
          verify(httpServerResponse, times(300)).write(anyString());
        }));
  }

  @Test
  public void testWriteQueueFull(TestContext testContext) {
    Handler [] drainHandler = new Handler [1];
    AtomicInteger drainCount = new AtomicInteger();
    RoutingContext routingContext = mock(RoutingContext.class);
    HttpServerResponse httpServerResponse = getHttpServerResponseMock();
    when(routingContext.response()).thenReturn(httpServerResponse);
    doAnswer(AdditionalAnswers.answerVoid((Handler handler) -> drainHandler[0] = handler))
    .when(httpServerResponse).drainHandler(any());
    doAnswer(
      new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) {
          getVertx().runOnContext(run -> {
            drainCount.getAndIncrement();
            drainHandler[0].handle(null);
          });
          return true;
        }
      })
    .when(httpServerResponse).writeQueueFull();
    AbstractInstanceRecordsAPITest.fetchRecordsByQuery("SELECT generate_series(1, 300)",
        routingContext, () -> Tuple.tuple(), testContext.asyncAssertSuccess(response -> {
          assertThat(response, is(nullValue()));
          assertThat(drainCount.get(), is(300));
          verify(httpServerResponse, times(300)).write(anyString());
        }));
  }
}
