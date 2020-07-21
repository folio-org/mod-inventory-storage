package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.jaxrs.resource.OaiPmhView.GetOaiPmhViewInstancesResponse.respond400WithTextPlain;
import static org.folio.rest.jaxrs.resource.OaiPmhView.GetOaiPmhViewInstancesResponse.respond500WithTextPlain;

import java.lang.invoke.MethodHandles;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import javax.validation.constraints.Pattern;
import javax.ws.rs.core.Response;

import org.apache.commons.lang.StringUtils;
import org.folio.rest.jaxrs.model.OaipmhInstanceIds;
import org.folio.rest.jaxrs.resource.OaiPmhView;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.RowStreamToBufferAdapter;

import com.google.common.collect.Iterables;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.streams.Pipe;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.Tuple;
import io.vertx.sqlclient.impl.ArrayTuple;

public class OaiPmhViewInstancesAPI implements OaiPmhView {

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup()
    .lookupClass());

  private static final String SQL = "select * from pmh_view_function($1,$2,$3,$4);";
  private static final String SQL_UPDATED_INSTANCES_IDS = "select * from pmh_get_updated_instances_ids($1,$2,$3,$4);";
  private static final String SQL_INSTANCES = "select * from pmh_instance_view_function($1,$2);";

  @Override
  public void getOaiPmhViewInstances(String startDate, String endDate, boolean deletedRecordSupport,
      boolean skipSuppressedFromDiscoveryRecords, String lang, RoutingContext routingContext, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    getCommonInstanceProcessing(SQL,
      () -> createPostgresParams(startDate, endDate, deletedRecordSupport, skipSuppressedFromDiscoveryRecords),
      routingContext, okapiHeaders, asyncResultHandler, vertxContext,
      "Select from oai pmh view completed successfully");
  }

  private void getCommonInstanceProcessing(String sql, Supplier<Tuple> paramsSupplier, RoutingContext routingContext, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext, String logMessage) {

    try {
      log.debug("request params:", Iterables.toString(routingContext.request().params()));
      Tuple params = paramsSupplier.get();
      log.debug("postgres params:", params);

      PostgresClient postgresClient = PgUtil.postgresClient(vertxContext, okapiHeaders);
      final HttpServerResponse response = getResponse(routingContext);

      postgresClient.startTx(tx -> postgresClient.selectStream(tx, sql, params, ar -> {
        if (ar.failed()) {
          respondWithError(ar.cause(), asyncResultHandler);
          return;
        }

        Pipe<Buffer> pipe = new RowStreamToBufferAdapter(ar.result()).pipe();
        pipe.to(response, completed -> {
          if (completed.failed()) {
            respondWithError(completed.cause(), asyncResultHandler);
            return;
          }
          log.debug(logMessage);
          postgresClient.endTx(tx, h -> {
            if (h.failed()) {
              respondWithError(h.cause(), asyncResultHandler);
            }
          });
        });
      }));
    } catch (IllegalArgumentException e) {
      log.error(e);
      asyncResultHandler.handle(succeededFuture(respond400WithTextPlain(e.getMessage())));
    } catch (Exception e) {
      respondWithError(e, asyncResultHandler);
    }
  }

  @Override
  public void postOaiPmhViewEnrichedInstances(OaipmhInstanceIds entity, RoutingContext routingContext,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    UUID[] ids = entity.getInstanceids().stream().map(UUID::fromString).toArray(UUID[]::new);

    getCommonInstanceProcessing(SQL_INSTANCES,
      () -> createPostgresParams(ids, entity.getSkipSuppressedFromDiscoveryRecords()),
      routingContext, okapiHeaders, asyncResultHandler, vertxContext,
      "Select from oai pmh instances view completed successfully");
  }

  @Override
  public void getOaiPmhViewUpdatedInstanceIds(String startDate, String endDate, boolean deletedRecordSupport, boolean skipSuppressedFromDiscoveryRecords, @Pattern(regexp = "[a-zA-Z]{2}") String lang, RoutingContext routingContext,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    getCommonInstanceProcessing(SQL_UPDATED_INSTANCES_IDS,
      () -> createPostgresParams(startDate, endDate, deletedRecordSupport, skipSuppressedFromDiscoveryRecords),
      routingContext, okapiHeaders, asyncResultHandler, vertxContext,
      "Select from oai pmh updated instances view completed successfully");
  }

  private HttpServerResponse getResponse(RoutingContext routingContext) {
    final HttpServerResponse response = routingContext.response();
    response.setChunked(true);
    response.putHeader("Content-Type", "application/json");
    return response;
  }

  private void respondWithError(Throwable t, Handler<AsyncResult<Response>> asyncResultHandler) {
    log.error(t);
    asyncResultHandler.handle(succeededFuture(respond500WithTextPlain(t.getMessage())));
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

  private Tuple createPostgresParams(UUID[] instancesIds, boolean skipSuppressedFromDiscoveryRecords) {

    Tuple tuple = new ArrayTuple(2);

    try {
      tuple.addUUIDArray(Optional.ofNullable(instancesIds).orElse(new UUID[0]));
      tuple.addBoolean(skipSuppressedFromDiscoveryRecords);

    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }

    return tuple;
  }

}
