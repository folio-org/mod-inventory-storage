package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.Tuple;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;
import javax.ws.rs.core.Response;
import org.folio.rest.RestVerticle;
import org.folio.rest.api.StorageTestSuite;

public class AbstractInstanceRecordsAPIImpl extends AbstractInstanceRecordsAPI {
  private static Map<String,String> okapiHeaders = Collections.singletonMap(
      RestVerticle.OKAPI_HEADER_TENANT, StorageTestSuite.TENANT_ID);

  public static void fetchRecordsByQuery(String sql, RoutingContext routingContext,
      Supplier<Tuple> paramsSupplier, Handler<AsyncResult<Response>> asyncResultHandler) {

    new AbstractInstanceRecordsAPI(){}.fetchRecordsByQuery(
        sql, paramsSupplier, routingContext, okapiHeaders,
        asyncResultHandler, Vertx.vertx().getOrCreateContext(), null);
  }
}
