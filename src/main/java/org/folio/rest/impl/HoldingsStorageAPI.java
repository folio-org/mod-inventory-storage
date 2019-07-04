package org.folio.rest.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.core.Response;

import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.HoldingsRecords;
import org.folio.rest.jaxrs.resource.HoldingsStorage;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.utils.TenantTool;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 *
 * @author ne
 */
public class HoldingsStorageAPI implements HoldingsStorage {

  private static final Logger log = LoggerFactory.getLogger(HoldingsStorageAPI.class);

  // Has to be lowercase because raml-module-builder uses case sensitive
  // lower case headers
  private static final String TENANT_HEADER = "x-okapi-tenant";
  public static final String HOLDINGS_RECORD_TABLE = "holdings_record";

  @Override
  public void deleteHoldingsStorageHoldings(
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Map<String, String> okapiHeaders,
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

  @Override
  public void getHoldingsStorageHoldings(
    @DefaultValue("0") @Min(0L) @Max(1000L) int offset,
    @DefaultValue("10") @Min(1L) @Max(100L) int limit,
    String query,
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    try {
      vertxContext.runOnContext(v -> {
        try {
          PostgresClient postgresClient = PostgresClient.getInstance(
            vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

          String[] fieldList = {"*"};

          CQL2PgJSON cql2pgJson = new CQL2PgJSON(HOLDINGS_RECORD_TABLE+".jsonb");
          CQLWrapper cql = new CQLWrapper(cql2pgJson, query)
            .setLimit(new Limit(limit))
            .setOffset(new Offset(offset));

          postgresClient.get(HOLDINGS_RECORD_TABLE, HoldingsRecord.class, fieldList, cql,
            true, false, reply -> {
              try {
                if(reply.succeeded()) {
                  List<HoldingsRecord> holdingsRecords = reply.result().getResults();

                  HoldingsRecords holdingsList = new HoldingsRecords();
                  holdingsList.setHoldingsRecords(holdingsRecords);
                  holdingsList.setTotalRecords(reply.result().getResultInfo().getTotalRecords());

                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    GetHoldingsStorageHoldingsResponse.
                      respond200WithApplicationJson(holdingsList)));
                }
                else {
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    GetHoldingsStorageHoldingsResponse.
                      respond500WithTextPlain(reply.cause().getMessage())));
                }
              } catch (Exception e) {
                  log.error(e.getMessage());
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  GetHoldingsStorageHoldingsResponse.
                    respond500WithTextPlain(e.getMessage())));
              }
            });
        } catch (Exception e) {
          log.error(e.getMessage());
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetHoldingsStorageHoldingsResponse.
              respond500WithTextPlain(e.getMessage())));
        }
      });
    } catch (Exception e) {
      log.error(e.getMessage());
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        GetHoldingsStorageHoldingsResponse.
          respond500WithTextPlain(e.getMessage())));
    }
  }

  @Override
  public void postHoldingsStorageHoldings(
    @DefaultValue("en")
    @Pattern(regexp = "[a-zA-Z]{2}") String lang,
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
                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(
                      PostHoldingsStorageHoldingsResponse
                        .respond400WithTextPlain(reply.cause().getMessage())));
                }
              } catch (Exception e) {
                log.error(e.getMessage());
                asyncResultHandler.handle(
                  io.vertx.core.Future.succeededFuture(
                    PostHoldingsStorageHoldingsResponse
                      .respond500WithTextPlain(e.getMessage())));
              }
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

  @Override
  public void getHoldingsStorageHoldingsByHoldingsRecordId(
    @NotNull String holdingsRecordId,
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
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

  @Override
  public void deleteHoldingsStorageHoldingsByHoldingsRecordId(
    @NotNull String holdingsRecordId,
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.deleteById(HOLDINGS_RECORD_TABLE, holdingsRecordId,
        okapiHeaders, vertxContext, DeleteHoldingsStorageHoldingsByHoldingsRecordIdResponse.class, asyncResultHandler);
  }

  @Override
  public void putHoldingsStorageHoldingsByHoldingsRecordId(
    @NotNull String holdingsRecordId,
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
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
                List<HoldingsRecord> itemList = reply.result().getResults();

                if (itemList.size() == 1) {
                  try {
                    postgresClient.update(HOLDINGS_RECORD_TABLE, entity, entity.getId(),
                      update -> {
                        try {
                          if (update.succeeded()) {
                            asyncResultHandler.handle(
                              Future.succeededFuture(
                                PutHoldingsStorageHoldingsByHoldingsRecordIdResponse
                                  .respond204()));
                          }
                          else {
                            asyncResultHandler.handle(
                              Future.succeededFuture(
                                PutHoldingsStorageHoldingsByHoldingsRecordIdResponse
                                  .respond500WithTextPlain(
                                    update.cause().getMessage())));
                          }
                        } catch (Exception e) {
                          asyncResultHandler.handle(
                            Future.succeededFuture(
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
              else {
                try {
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
                          asyncResultHandler.handle(
                            Future.succeededFuture(
                              PutHoldingsStorageHoldingsByHoldingsRecordIdResponse
                                .respond500WithTextPlain(
                                  save.cause().getMessage())));
                        }
                      } catch (Exception e) {
                        asyncResultHandler.handle(
                          Future.succeededFuture(
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
