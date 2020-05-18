package org.folio.rest.impl;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.rest.support.EffectiveCallNumberComponentsUtil.buildComponents;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.resource.HoldingsStorage;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.SQLConnection;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.support.HridManager;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;

/**
 *
 * @author ne
 */
public class HoldingsStorageAPI implements HoldingsStorage {

  private static final Logger log = LoggerFactory.getLogger(HoldingsStorageAPI.class);

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

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    try {
      PostgresClient postgresClient =
        PostgresClient.getInstance(
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
              if(reply.succeeded()) {
                List<HoldingsRecord> holdingsList = reply.result().getResults();

                if (holdingsList.size() == 1) {
                  final HoldingsRecord existingHoldings = holdingsList.get(0);
                  if (Objects.equals(entity.getHrid(), existingHoldings.getHrid())) {
                    try {
                      postgresClient.startTx(connection -> {
                        updateItemEffectiveCallNumbersByHoldings(connection, postgresClient, entity).onComplete(updateResult -> {
                          if (updateResult.succeeded()) {
                            postgresClient.update(connection, HOLDINGS_RECORD_TABLE, entity,
                              "jsonb", String.format(WHERE_CLAUSE, holdingsRecordId), false,
                              update -> {
                                try {
                                  if (update.succeeded()) {
                                    postgresClient.endTx(connection, done -> {
                                      asyncResultHandler.handle(
                                        Future.succeededFuture(
                                          PutHoldingsStorageHoldingsByHoldingsRecordIdResponse
                                            .respond204()));
                                    });
                                  }
                                  else {
                                    postgresClient.rollbackTx(connection, rollback -> {
                                      asyncResultHandler.handle(
                                        Future.succeededFuture(
                                          PutHoldingsStorageHoldingsByHoldingsRecordIdResponse
                                            .respond500WithTextPlain(
                                              update.cause().getMessage())));
                                    });
                                  }
                                } catch (Exception e) {
                                  postgresClient.rollbackTx(connection, rollback -> {
                                    asyncResultHandler.handle(
                                      Future.succeededFuture(
                                        PutHoldingsStorageHoldingsByHoldingsRecordIdResponse
                                          .respond500WithTextPlain(e.getMessage())));
                                  });
                                }
                              });
                            } else {
                              postgresClient.rollbackTx(connection, rollback ->
                                asyncResultHandler.handle(
                                  Future.succeededFuture(
                                    PutHoldingsStorageHoldingsByHoldingsRecordIdResponse
                                      .respond500WithTextPlain(
                                        updateResult.cause().getMessage()))));
                            }
                          });
                      });
                    } catch (Exception e) {
                      asyncResultHandler.handle(Future.succeededFuture(
                        PutHoldingsStorageHoldingsByHoldingsRecordIdResponse
                          .respond500WithTextPlain(e.getMessage())));
                    }
                  } else {
                    asyncResultHandler.handle(
                      Future.succeededFuture(
                        PutHoldingsStorageHoldingsByHoldingsRecordIdResponse
                          .respond400WithTextPlain(
                              "The hrid field cannot be changed: new="
                                + entity.getHrid()
                                + ", old="
                                + existingHoldings.getHrid())));
                  }
              }
              else {
                try {
                  final Future<String> hridFuture =
                      setHoldingsHrid(entity, vertxContext, postgresClient);

                  hridFuture.map(hrid -> {
                    entity.setHrid(hrid);
                    postgresClient.save(HOLDINGS_RECORD_TABLE, entity.getId(), entity,
                      save -> {
                        try {
                          if(save.succeeded()) {
                            asyncResultHandler.handle(
                              Future.succeededFuture(
                                PutHoldingsStorageHoldingsByHoldingsRecordIdResponse
                                  .respond204()));
                          }
                          else {
                            if (PgExceptionUtil.isUniqueViolation(save.cause())) {
                              asyncResultHandler.handle(
                                Future.succeededFuture(
                                  PutHoldingsStorageHoldingsByHoldingsRecordIdResponse
                                    .respond400WithTextPlain(PgExceptionUtil.badRequestMessage(save.cause()))));
                            } else {
                              asyncResultHandler.handle(
                                Future.succeededFuture(
                                  PutHoldingsStorageHoldingsByHoldingsRecordIdResponse
                                    .respond500WithTextPlain(
                                      save.cause().getMessage())));
                            }
                          }
                        } catch (Exception e) {
                          asyncResultHandler.handle(
                            Future.succeededFuture(
                              PutHoldingsStorageHoldingsByHoldingsRecordIdResponse
                                .respond500WithTextPlain(e.getMessage())));
                        }
                      });
                    return null;
                  })
                  .otherwise(error -> {
                    return null;
                  });
                } catch (Exception e) {
                  asyncResultHandler.handle(Future.succeededFuture(
                    PutHoldingsStorageHoldingsByHoldingsRecordIdResponse
                      .respond500WithTextPlain(e.getMessage())));
                }
              }
            } else {
                asyncResultHandler.handle(Future.succeededFuture(
                  PutHoldingsStorageHoldingsByHoldingsRecordIdResponse
                    .respond500WithTextPlain(reply.cause().getMessage())));
            }
          });
        } catch (Exception e) {
          asyncResultHandler.handle(Future.succeededFuture(
            PutHoldingsStorageHoldingsByHoldingsRecordIdResponse
              .respond500WithTextPlain(e.getMessage())));
        }
      });
    } catch (Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(
        PutHoldingsStorageHoldingsByHoldingsRecordIdResponse
          .respond500WithTextPlain(e.getMessage())));
    }
  }

  private Future<String> setHoldingsHrid(HoldingsRecord entity, Context vertxContext,
      PostgresClient postgresClient) {
    final Future<String> hridFuture;

    if (isBlank(entity.getHrid())) {
      final HridManager hridManager = new HridManager(vertxContext, postgresClient);
      hridFuture = hridManager.getNextHoldingsHrid();
    } else {
      hridFuture = StorageHelper.completeFuture(entity.getHrid());
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

  private Future<RowSet<Row>> updateItemEffectiveCallNumbersByHoldings(AsyncResult<SQLConnection> connection, PostgresClient postgresClient, HoldingsRecord holdingsRecord) {
    Promise<RowSet<Row>> promise = Promise.promise();
    Criterion criterion = new Criterion(
      new Criteria().addField("holdingsRecordId")
        .setJSONB(false).setOperation("=").setVal(holdingsRecord.getId()));
    postgresClient.get(ITEM_TABLE, Item.class, criterion, false, false, response -> {
      updateEffectiveCallNumbers(connection, postgresClient, response.result().getResults(), holdingsRecord).future().onComplete(promise);
    });
    return promise.future();
  }

  private Promise<RowSet<Row>> updateEffectiveCallNumbers(AsyncResult<SQLConnection> connection, PostgresClient postgresClient, List<Item> items, HoldingsRecord holdingsRecord) {
    Promise<RowSet<Row>> allItemsUpdated = Promise.promise();
    List<Function<SQLConnection, Future<RowSet<Row>>>> batchFactories = items
      .stream()
      .map(item -> updateItemEffectiveCallNumber(item, holdingsRecord))
      .map(item -> updateSingleBatchFactory(ITEM_TABLE, item.getId(), item, postgresClient))
      .collect(Collectors.toList());

    SQLConnection connectionResult = connection.result();
    Future<RowSet<Row>> lastUpdate = Future.succeededFuture();
    for (Function<SQLConnection, Future<RowSet<Row>>> factory : batchFactories) {
      lastUpdate = lastUpdate.compose(prev -> factory.apply(connectionResult));
    }

    lastUpdate.onComplete(allItemsUpdated);
    return allItemsUpdated;
  }

  private Item updateItemEffectiveCallNumber(Item item, HoldingsRecord holdingsRecord) {
    item.setEffectiveCallNumberComponents(buildComponents(holdingsRecord, item));
    return item;
  }

  private <T> Function<SQLConnection, Future<RowSet<Row>>> updateSingleBatchFactory(String tableName, String id, T entity, PostgresClient postgresClient) {
    return connection -> {
      Promise<RowSet<Row>> updateResultFuture = Promise.promise();
      Future<SQLConnection> connectionResult = Future.succeededFuture(connection);
      postgresClient.update(connectionResult, tableName, entity, "jsonb", String.format(WHERE_CLAUSE, id), false, updateResultFuture);
      return updateResultFuture.future();
    };
  }
}
