package org.folio.rest.impl;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.rest.support.EndpointFailureHandler.handleFailure;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.resource.HoldingsStorage;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.support.HridManager;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;
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
  private static final String WHERE_CLAUSE = "WHERE id = '%s'";
  public static final String HOLDINGS_RECORD_TABLE = "holdings_record";
  public static final String ITEM_TABLE = "item";

  @Validate
  @Override
  public void deleteHoldingsStorageHoldings(String lang,
    RoutingContext routingContext, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    vertxContext.runOnContext(v -> {
      try {
        PostgresClient postgresClient = PostgresClient.getInstance(
          vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

        postgresClient.execute(String.format("DELETE FROM %s_%s."+HOLDINGS_RECORD_TABLE,
          tenantId, "mod_inventory_storage"),
          reply -> {
            asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
              DeleteHoldingsStorageHoldingsResponse
                .noContent().build()));
          });
      }
      catch(Exception e) {
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
          DeleteHoldingsStorageHoldingsResponse
            .respond500WithTextPlain(e.getMessage())));
      }
    });

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

        String tenantId = okapiHeaders.get(TENANT_HEADER);

    try {
      PostgresClient postgresClient =
        PostgresClient.getInstance(
          vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

      vertxContext.runOnContext(v -> {
        try {

          if(entity.getId() == null) {
            entity.setId(UUID.randomUUID().toString());
          }
          else {
            if (! isUUID(entity.getId())) {
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                PostHoldingsStorageHoldingsResponse
                  .respond400WithTextPlain("ID must be a UUID")));
              return;
            }
          }
          final Future<String> hridFuture =
              setHoldingsHrid(entity, vertxContext, postgresClient);

          hridFuture.map(hrid -> {
            entity.setHrid(hrid);
            postgresClient.save(HOLDINGS_RECORD_TABLE, entity.getId(), entity,
              reply -> {
                try {
                  if(reply.succeeded()) {
                    String ret = reply.result();
                    asyncResultHandler.handle(
                      io.vertx.core.Future.succeededFuture(
                        PostHoldingsStorageHoldingsResponse
                          .respond201WithApplicationJson(entity, PostHoldingsStorageHoldingsResponse.headersFor201().withLocation(ret))));
                  }
                  else {
                    if (PgExceptionUtil.isUniqueViolation(reply.cause())) {
                      ValidationHelper.handleError(reply.cause(), asyncResultHandler);
                    } else {
                      asyncResultHandler.handle(
                        io.vertx.core.Future.succeededFuture(
                          PostHoldingsStorageHoldingsResponse
                            .respond400WithTextPlain(reply.cause().getMessage())));
                    }
                  }
                } catch (Exception e) {
                  log.error(e.getMessage());
                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(
                      PostHoldingsStorageHoldingsResponse
                        .respond500WithTextPlain(e.getMessage())));
                }
              });
            return null;
          })
          .otherwise(error -> {
            log.error(error.getMessage(), error);
            asyncResultHandler.handle(
              io.vertx.core.Future.succeededFuture(
                PostHoldingsStorageHoldingsResponse
                  .respond500WithTextPlain(error.getMessage())));
            return null;
          });
        } catch (Exception e) {
          log.error(e.getMessage());
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            PostHoldingsStorageHoldingsResponse.respond500WithTextPlain(e.getMessage())));
        }
      });
    } catch (Exception e) {
      log.error(e.getMessage());
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        PostHoldingsStorageHoldingsResponse.respond500WithTextPlain(e.getMessage())));
    }
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
                      io.vertx.core.Future.succeededFuture(
                        GetHoldingsStorageHoldingsByHoldingsRecordIdResponse.
                          respond200WithApplicationJson(holdingsRecord)));
                  }
                  else {
                  asyncResultHandler.handle(
                    Future.succeededFuture(
                      GetHoldingsStorageHoldingsByHoldingsRecordIdResponse.
                        respond404WithTextPlain("Not Found")));
                  }
                } else {
                  asyncResultHandler.handle(
                    Future.succeededFuture(
                      GetHoldingsStorageHoldingsByHoldingsRecordIdResponse.
                        respond500WithTextPlain(reply.cause().getMessage())));

                }
              } catch (Exception e) {
                  log.error(e.getMessage());
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  GetHoldingsStorageHoldingsByHoldingsRecordIdResponse.
                    respond500WithTextPlain(e.getMessage())));
              }
            });
        } catch (Exception e) {
          log.error(e.getMessage());
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetHoldingsStorageHoldingsByHoldingsRecordIdResponse.
              respond500WithTextPlain(e.getMessage())));
        }
      });
    } catch (Exception e) {
      log.error(e.getMessage());
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
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

    PgUtil.deleteById(HOLDINGS_RECORD_TABLE, holdingsRecordId,
        okapiHeaders, vertxContext, DeleteHoldingsStorageHoldingsByHoldingsRecordIdResponse.class, asyncResultHandler);
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

  private Future<String> setHoldingsHrid(HoldingsRecord entity, Context vertxContext,
      PostgresClient postgresClient) {
    final Future<String> hridFuture;

    if (isBlank(entity.getHrid())) {
      final HridManager hridManager = new HridManager(vertxContext, postgresClient);
      hridFuture = hridManager.getNextHoldingsHrid();
    } else {
      hridFuture = Future.succeededFuture(entity.getHrid());
    }

    return hridFuture;
  }

  private boolean isUUID(String id) {
    try {
      UUID.fromString(id);
      return true;
    }
    catch(IllegalArgumentException e) {
      return false;
    }
  }
}
