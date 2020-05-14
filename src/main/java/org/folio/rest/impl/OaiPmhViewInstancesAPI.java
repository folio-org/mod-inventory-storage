package org.folio.rest.impl;

import java.lang.invoke.MethodHandles;
import java.sql.Timestamp;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.commons.lang.StringUtils;
import org.folio.rest.jaxrs.resource.OaiPmhView;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.SQLRowStreamToBufferAdapter;

import com.google.common.collect.Iterables;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.streams.Pump;
import io.vertx.ext.web.RoutingContext;

public class OaiPmhViewInstancesAPI implements OaiPmhView {

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup()
    .lookupClass());

  private static final int MAX_QUEUE_SIZE = 100;
  private static final String SQL = "select * from pmh_view_function(?,?,?,?);";
  private static final String RESPONSE_ENDING = "{}]";

  @Override
  public void getOaiPmhViewInstances(String startDate, String endDate, boolean deletedRecordSupport,
      boolean skipSuppressedFromDiscoveryRecords, String lang, RoutingContext routingContext, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    log.debug("request params:", Iterables.toString(routingContext.request()
      .params()));

    PostgresClient postgresClient = PgUtil.postgresClient(vertxContext, okapiHeaders);

    JsonArray params = createPostgresParams(startDate, endDate, deletedRecordSupport, skipSuppressedFromDiscoveryRecords);

    final HttpServerResponse response = getResponse(routingContext);

    postgresClient.selectStream(SQL, params, handler -> {
      if (handler.failed()) {
        log.error("Error in selecting from oai pmh view", handler.cause());
        OaiPmhView.GetOaiPmhViewInstancesResponse.respond500WithTextPlain(handler.cause()
          .getMessage());
      } else {
        final SQLRowStreamToBufferAdapter rs = new SQLRowStreamToBufferAdapter(handler.result());
        Pump pump = Pump.pump(rs, response, MAX_QUEUE_SIZE);
        pump.start();
        rs.endHandler(event -> {
          log.info("Select from oai pmh view completed successfully");
          response.end(RESPONSE_ENDING);
        })
          .exceptionHandler(t -> log.error("Error connecting to the database", t));
      }
    });
  }

  private HttpServerResponse getResponse(RoutingContext routingContext) {
    final HttpServerResponse response = routingContext.response();
    response.setChunked(true);
    response.putHeader("Content-Type", "application/json");
    return response;
  }

  private JsonArray createPostgresParams(String startDate, String endDate, boolean deletedRecordSupport,
      boolean skipSuppressedFromDiscoveryRecords) {
    JsonArray params = new JsonArray();
    try {
      if (StringUtils.isNotEmpty(startDate)) {
        params.add(Timestamp.valueOf(startDate)
          .toString());
      } else {
        params.add(startDate);
      }
      if (StringUtils.isNotEmpty(endDate)) {
        params.add(Timestamp.valueOf(endDate)
          .toString());
      } else {
        params.add(endDate);
      }
      params.add(deletedRecordSupport);
      params.add(skipSuppressedFromDiscoveryRecords);
    } catch (Exception e) {
      log.error(e);
      OaiPmhView.GetOaiPmhViewInstancesResponse.respond400WithTextPlain(e
        .getMessage());
    }
    return params;
  }
}
