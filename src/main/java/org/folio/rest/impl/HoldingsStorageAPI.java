package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.EndpointFailureHandler.handleFailure;

import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.resource.HoldingsStorage;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.services.holding.HoldingsService;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 *
 * @author ne
 */
public class HoldingsStorageAPI implements HoldingsStorage {

  private static final Logger log = LogManager.getLogger();

  // Has to be lowercase because raml-module-builder uses case sensitive
  // lower case headers
  private static final String TENANT_HEADER = "x-okapi-tenant";
  public static final String HOLDINGS_RECORD_TABLE = "holdings_record";

  @Validate
  @Override
  public void deleteHoldingsStorageHoldings(String lang,
    RoutingContext routingContext, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new HoldingsService(vertxContext, okapiHeaders)
      .deleteAllHoldings()
      .onSuccess(notUsed -> asyncResultHandler.handle(succeededFuture(
        DeleteHoldingsStorageHoldingsResponse.respond204())))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void getHoldingsStorageHoldings(
    int offset, int limit, String query, String lang,
    RoutingContext routingContext, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.streamGet(HOLDINGS_RECORD_TABLE, HoldingsRecord.class, query, offset,
      limit, null, "holdingsRecords", routingContext, okapiHeaders, vertxContext);
  }

  @Validate
  @Override
  public void postHoldingsStorageHoldings(String lang,
    HoldingsRecord entity,
    RoutingContext routingContext, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new HoldingsService(vertxContext, okapiHeaders)
      .createHolding(entity)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void getHoldingsStorageHoldingsByHoldingsRecordId(
    String holdingsRecordId, String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    String tenantId = okapiHeaders.get(TENANT_HEADER);
    try {
      PostgresClient postgresClient = PostgresClient.getInstance(
        vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

      vertxContext.runOnContext(v -> {
        try {
          String[] fieldList = {"*"};

          CQL2PgJSON cql2pgJson = new CQL2PgJSON(HOLDINGS_RECORD_TABLE+".jsonb");
          CQLWrapper cql = new CQLWrapper(cql2pgJson, String.format("id==%s", holdingsRecordId))
            .setLimit(new Limit(1))
            .setOffset(new Offset(0));

          log.info(String.format("SQL generated from CQL: %s", cql.toString()));

          postgresClient.get(HOLDINGS_RECORD_TABLE, HoldingsRecord.class, fieldList, cql, true, false,
            reply -> {
              try {
                if (reply.succeeded()) {
                  List<HoldingsRecord> holdingsList = reply.result().getResults();
                  if (holdingsList.size() == 1) {
                    HoldingsRecord holdingsRecord = holdingsList.get(0);

                    asyncResultHandler.handle(
                      succeededFuture(
                        GetHoldingsStorageHoldingsByHoldingsRecordIdResponse.
                          respond200WithApplicationJson(holdingsRecord)));
                  }
                  else {
                  asyncResultHandler.handle(
                    succeededFuture(
                      GetHoldingsStorageHoldingsByHoldingsRecordIdResponse.
                        respond404WithTextPlain("Not Found")));
                  }
                } else {
                  asyncResultHandler.handle(
                    succeededFuture(
                      GetHoldingsStorageHoldingsByHoldingsRecordIdResponse.
                        respond500WithTextPlain(reply.cause().getMessage())));

                }
              } catch (Exception e) {
                  log.error(e.getMessage());
                asyncResultHandler.handle(succeededFuture(
                  GetHoldingsStorageHoldingsByHoldingsRecordIdResponse.
                    respond500WithTextPlain(e.getMessage())));
              }
            });
        } catch (Exception e) {
          log.error(e.getMessage());
          asyncResultHandler.handle(succeededFuture(
            GetHoldingsStorageHoldingsByHoldingsRecordIdResponse.
              respond500WithTextPlain(e.getMessage())));
        }
      });
    } catch (Exception e) {
      log.error(e.getMessage());
      asyncResultHandler.handle(succeededFuture(
        GetHoldingsStorageHoldingsByHoldingsRecordIdResponse.
          respond500WithTextPlain(e.getMessage())));
    }

  }

  @Validate
  @Override
  public void deleteHoldingsStorageHoldingsByHoldingsRecordId(
    String holdingsRecordId, String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new HoldingsService(vertxContext, okapiHeaders)
      .deleteHolding(holdingsRecordId)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void putHoldingsStorageHoldingsByHoldingsRecordId(
    String holdingsRecordId, String lang,
    HoldingsRecord entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new HoldingsService(vertxContext, okapiHeaders)
      .updateHoldingRecord(holdingsRecordId, entity)
      .onSuccess(notUsed -> asyncResultHandler.handle(Future.succeededFuture(
        PutHoldingsStorageHoldingsByHoldingsRecordIdResponse.respond204())))
      .onFailure(handleFailure(asyncResultHandler));
  }
}
