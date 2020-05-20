package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.jaxrs.resource.OaiPmhView.GetOaiPmhViewInstancesResponse.respond400WithTextPlain;
import static org.folio.rest.jaxrs.resource.OaiPmhView.GetOaiPmhViewInstancesResponse.respond500WithTextPlain;

import java.lang.invoke.MethodHandles;
import java.time.OffsetDateTime;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.commons.lang.StringUtils;
import org.folio.rest.jaxrs.resource.OaiPmhView;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;

import com.google.common.collect.Iterables;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import io.vertx.sqlclient.impl.ArrayTuple;

public class OaiPmhViewInstancesAPI implements OaiPmhView {

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup()
    .lookupClass());

  private static final String SQL = "select * from pmh_view_function($1,$2,$3,$4);";
  private static final String RESPONSE_START = "[";
  private static final String RESPONSE_END = "{}]";

  @Override
  public void getOaiPmhViewInstances(String startDate, String endDate, boolean deletedRecordSupport,
      boolean skipSuppressedFromDiscoveryRecords, String lang, RoutingContext routingContext, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    log.debug("request params:", Iterables.toString(routingContext.request()
      .params()));

    try {
      Tuple params = createPostgresParams(startDate, endDate, deletedRecordSupport, skipSuppressedFromDiscoveryRecords);
      log.debug("postgres params:", params);

      PostgresClient postgresClient = PgUtil.postgresClient(vertxContext, okapiHeaders);

      final HttpServerResponse response = getResponse(routingContext);

      postgresClient.startTx(tx -> postgresClient.selectStream(tx, SQL, params, ar -> {
        if (ar.failed()) {
          respondWithError(ar.cause(), asyncResultHandler);
          return;
        }
        response.write(RESPONSE_START);
        ar.result()
          .handler(row -> {
            response.write(createJsonFromRow(row));
            response.write(",");
          })
          .endHandler(event -> {
            log.info("Select from oai pmh view completed successfully");
            response.end(RESPONSE_END);
            postgresClient.endTx(tx, h -> {
              if (h.failed()) {
                respondWithError(h.cause(), asyncResultHandler);
              }
            });
          });
      }));
    } catch (IllegalArgumentException e) {
      log.error(e);
      asyncResultHandler
        .handle(succeededFuture(respond400WithTextPlain(e.getMessage())));
    } catch (Exception e) {
      respondWithError(e, asyncResultHandler);
    }
  }

  private String createJsonFromRow(Row row) {
    JsonObject json = new JsonObject();
    for (int i = 0; i < row.size(); i++) {
      json.put(row.getColumnName(i), convertRowValue(row.getValue(i)));
    }
    return json.toString();
  }

  private Object convertRowValue(Object value) {
    if (value == null) {
      return "";
    }
    return value instanceof JsonObject ? value : value.toString();
  }

  private HttpServerResponse getResponse(RoutingContext routingContext) {
    final HttpServerResponse response = routingContext.response();
    response.setChunked(true);
    response.putHeader("Content-Type", "application/json");
    return response;
  }

  private void respondWithError(Throwable t, Handler<AsyncResult<Response>> asyncResultHandler) {
    log.error(t);
    asyncResultHandler
      .handle(succeededFuture(respond500WithTextPlain(t.getMessage())));
  }

  private Tuple createPostgresParams(String startDate, String endDate, boolean deletedRecordSupport,
      boolean skipSuppressedFromDiscoveryRecords) {

    Tuple tuple = new ArrayTuple(4);

    try {
      if (StringUtils.isNotEmpty(startDate)) {
        tuple.addTemporal(OffsetDateTime.parse(startDate));
      } else {
        tuple.addValue(null);
      }
      if (StringUtils.isNotEmpty(endDate)) {
        tuple.addTemporal(OffsetDateTime.parse(endDate));
      } else {
        tuple.addValue(null);
      }

      tuple.addBoolean(deletedRecordSupport);
      tuple.addBoolean(skipSuppressedFromDiscoveryRecords);

    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }

    return tuple;
  }

}
