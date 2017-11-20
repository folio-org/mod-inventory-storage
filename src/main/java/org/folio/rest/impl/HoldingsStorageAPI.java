/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.core.Response;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.HoldingsRecords;
import org.folio.rest.jaxrs.resource.HoldingsStorageResource;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.TenantTool;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;

/**
 *
 * @author ne
 */
public class HoldingsStorageAPI implements HoldingsStorageResource {
  
  // Has to be lowercase because raml-module-builder uses case sensitive
  // lower case headers
  private static final String TENANT_HEADER = "x-okapi-tenant";
  public static final String HOLDINGS_RECORD_TABLE = "holdings_record";


  @Override
  public void deleteHoldingsStorageHoldings(
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    vertxContext.runOnContext(v -> {
      try {
        PostgresClient postgresClient = PostgresClient.getInstance(
          vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

        postgresClient.mutate(String.format("TRUNCATE TABLE %s_%s"+HOLDINGS_RECORD_TABLE,
          tenantId, "mod_inventory_storage"),
          reply -> {
            asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
              HoldingsStorageResource.DeleteHoldingsStorageHoldingsResponse
                .noContent().build()));
          });
      }
      catch(Exception e) {
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
          HoldingsStorageResource.DeleteHoldingsStorageHoldingsResponse
            .withPlainInternalServerError(e.getMessage())));
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
    Context vertxContext) throws Exception {

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
                  List<HoldingsRecord> holdingsRecords = (List<HoldingsRecord>) reply.result().getResults();

                  HoldingsRecords holdingsList = new HoldingsRecords();
                  holdingsList.setHoldingsRecords(holdingsRecords);
                  holdingsList.setTotalRecords(reply.result().getResultInfo().getTotalRecords());

                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    HoldingsStorageResource.GetHoldingsStorageHoldingsResponse.
                      withJsonOK(holdingsList)));
                }
                else {
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    HoldingsStorageResource.GetHoldingsStorageHoldingsResponse.
                      withPlainInternalServerError(reply.cause().getMessage())));
                }
              } catch (Exception e) {
                e.printStackTrace();
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  HoldingsStorageResource.GetHoldingsStorageHoldingsResponse.
                    withPlainInternalServerError(e.getMessage())));
              }
            });
        } catch (Exception e) {
          e.printStackTrace();
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            HoldingsStorageResource.GetHoldingsStorageHoldingsResponse.
              withPlainInternalServerError(e.getMessage())));
        }
      });
    } catch (Exception e) {
      e.printStackTrace();
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        HoldingsStorageResource.GetHoldingsStorageHoldingsResponse.
          withPlainInternalServerError(e.getMessage())));
    }
  }

  @Override
  public void postHoldingsStorageHoldings(
    @DefaultValue("en") 
    @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    HoldingsRecord entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {
    
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
            if(isUUID(entity.getId())) {
              io.vertx.core.Future.succeededFuture(
                HoldingsStorageResource.PostHoldingsStorageHoldingsResponse
                  .withPlainBadRequest("ID must be a UUID"));
            }
          }

          postgresClient.save(HOLDINGS_RECORD_TABLE, entity.getId(), entity,
            reply -> {
              try {
                if(reply.succeeded()) {
                  OutStream stream = new OutStream();
                  stream.setData(entity);

                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(
                      HoldingsStorageResource.PostHoldingsStorageHoldingsResponse
                        .withJsonCreated(reply.result(), stream)));
                }
                else {
                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(
                      HoldingsStorageResource.PostHoldingsStorageHoldingsResponse
                        .withPlainBadRequest("ID must be a UUID")));
                }
              } catch (Exception e) {
                e.printStackTrace();
                asyncResultHandler.handle(
                  io.vertx.core.Future.succeededFuture(
                    HoldingsStorageResource.PostHoldingsStorageHoldingsResponse
                      .withPlainInternalServerError(e.getMessage())));
              }
            });
        } catch (Exception e) {
          e.printStackTrace();
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            HoldingsStorageResource.PostHoldingsStorageHoldingsResponse
              .withPlainInternalServerError(e.getMessage())));
        }
      });
    } catch (Exception e) {
      e.printStackTrace();
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        HoldingsStorageResource.PostHoldingsStorageHoldingsResponse
          .withPlainInternalServerError(e.getMessage())));
    }

  }

  @Override
  public void getHoldingsStorageHoldingsByHoldingsRecordId(
    @NotNull String holdingsRecordId,
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    try {
      PostgresClient postgresClient = PostgresClient.getInstance(
        vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

      Criteria a = new Criteria();

      a.addField("'id'");
      a.setOperation("=");
      a.setValue(holdingsRecordId);

      Criterion criterion = new Criterion(a);

      vertxContext.runOnContext(v -> {
        try {
          postgresClient.get(HOLDINGS_RECORD_TABLE, HoldingsRecord.class, criterion, true, false,
            reply -> {
              try {
                if (reply.succeeded()) {
                  List<HoldingsRecord> holdingsList = (List<HoldingsRecord>) reply.result().getResults();
                  if (holdingsList.size() == 1) {
                    HoldingsRecord holdingsRecord = holdingsList.get(0);

                    asyncResultHandler.handle(
                      io.vertx.core.Future.succeededFuture(
                        HoldingsStorageResource.GetHoldingsStorageHoldingsByHoldingsRecordIdResponse.
                          withJsonOK(holdingsRecord)));
                  }
                  else {
                  asyncResultHandler.handle(
                    Future.succeededFuture(
                      HoldingsStorageResource.GetHoldingsStorageHoldingsByHoldingsRecordIdResponse.
                        withPlainNotFound("Not Found")));
                  }
                } else {
                  asyncResultHandler.handle(
                    Future.succeededFuture(
                      HoldingsStorageResource.GetHoldingsStorageHoldingsByHoldingsRecordIdResponse.
                        withPlainInternalServerError(reply.cause().getMessage())));

                }
              } catch (Exception e) {
                e.printStackTrace();
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  HoldingsStorageResource.GetHoldingsStorageHoldingsByHoldingsRecordIdResponse.
                    withPlainInternalServerError(e.getMessage())));
              }
            });
        } catch (Exception e) {
          e.printStackTrace();
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            HoldingsStorageResource.GetHoldingsStorageHoldingsByHoldingsRecordIdResponse.
              withPlainInternalServerError(e.getMessage())));
        }
      });
    } catch (Exception e) {
      e.printStackTrace();
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        HoldingsStorageResource.GetHoldingsStorageHoldingsByHoldingsRecordIdResponse.
          withPlainInternalServerError(e.getMessage())));
    }

  }

  @Override
  public void deleteHoldingsStorageHoldingsByHoldingsRecordId(
    @NotNull String holdingsRecordId,
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    try {
      PostgresClient postgresClient =
        PostgresClient.getInstance(
          vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

      Criteria a = new Criteria();

      a.addField("'id'");
      a.setOperation("=");
      a.setValue(holdingsRecordId);

      Criterion criterion = new Criterion(a);

      vertxContext.runOnContext(v -> {
        try {
          postgresClient.delete(HOLDINGS_RECORD_TABLE, criterion,
            reply -> {
              if(reply.succeeded()) {
                asyncResultHandler.handle(
                  Future.succeededFuture(
                    DeleteHoldingsStorageHoldingsByHoldingsRecordIdResponse
                      .withNoContent()));
              }
              else {
                asyncResultHandler.handle(Future.succeededFuture(
                  DeleteHoldingsStorageHoldingsByHoldingsRecordIdResponse
                    .withPlainInternalServerError(reply.cause().getMessage())));
              }
            });
        } catch (Exception e) {
          asyncResultHandler.handle(Future.succeededFuture(
            DeleteHoldingsStorageHoldingsByHoldingsRecordIdResponse
              .withPlainInternalServerError(e.getMessage())));
        }
      });
    } catch (Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(
        DeleteHoldingsStorageHoldingsByHoldingsRecordIdResponse
          .withPlainInternalServerError(e.getMessage())));
    }
  }

  @Override
  public void putHoldingsStorageHoldingsByHoldingsRecordId(
    @NotNull String holdingsRecordId,
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    HoldingsRecord entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    try {
      PostgresClient postgresClient =
        PostgresClient.getInstance(
          vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

      Criteria a = new Criteria();

      a.addField("'id'");
      a.setOperation("=");
      a.setValue(holdingsRecordId);

      Criterion criterion = new Criterion(a);

      vertxContext.runOnContext(v -> {
        try {
          postgresClient.get(HOLDINGS_RECORD_TABLE, HoldingsRecord.class, criterion, true, false,
            reply -> {
              if(reply.succeeded()) {
                List<HoldingsRecord> itemList = (List<HoldingsRecord>) reply.result().getResults();

                if (itemList.size() == 1) {
                  try {
                    postgresClient.update(HOLDINGS_RECORD_TABLE, entity, criterion,
                      true,
                      update -> {
                        try {
                          if(update.succeeded()) {
                            OutStream stream = new OutStream();
                            stream.setData(entity);

                            asyncResultHandler.handle(
                              Future.succeededFuture(
                                PutHoldingsStorageHoldingsByHoldingsRecordIdResponse
                                  .withNoContent()));
                          }
                          else {
                            asyncResultHandler.handle(
                              Future.succeededFuture(
                                PutHoldingsStorageHoldingsByHoldingsRecordIdResponse
                                  .withPlainInternalServerError(
                                    update.cause().getMessage())));
                          }
                        } catch (Exception e) {
                          asyncResultHandler.handle(
                            Future.succeededFuture(
                              PutHoldingsStorageHoldingsByHoldingsRecordIdResponse
                                .withPlainInternalServerError(e.getMessage())));
                        }
                      });
                  } catch (Exception e) {
                    asyncResultHandler.handle(Future.succeededFuture(
                      PutHoldingsStorageHoldingsByHoldingsRecordIdResponse
                        .withPlainInternalServerError(e.getMessage())));
                  }
              }
              else {
                try {
                  postgresClient.save(HOLDINGS_RECORD_TABLE, entity.getId(), entity,
                    save -> {
                      try {
                        if(save.succeeded()) {
                          OutStream stream = new OutStream();
                          stream.setData(entity);

                          asyncResultHandler.handle(
                            Future.succeededFuture(
                              PutHoldingsStorageHoldingsByHoldingsRecordIdResponse
                                .withNoContent()));
                        }
                        else {
                          asyncResultHandler.handle(
                            Future.succeededFuture(
                              PutHoldingsStorageHoldingsByHoldingsRecordIdResponse
                                .withPlainInternalServerError(
                                  save.cause().getMessage())));
                        }
                      } catch (Exception e) {
                        asyncResultHandler.handle(
                          Future.succeededFuture(
                            PutHoldingsStorageHoldingsByHoldingsRecordIdResponse
                              .withPlainInternalServerError(e.getMessage())));
                      }
                    });
                } catch (Exception e) {
                  asyncResultHandler.handle(Future.succeededFuture(
                    PutHoldingsStorageHoldingsByHoldingsRecordIdResponse
                      .withPlainInternalServerError(e.getMessage())));
                }
              }
            } else {
                asyncResultHandler.handle(Future.succeededFuture(
                  PutHoldingsStorageHoldingsByHoldingsRecordIdResponse
                    .withPlainInternalServerError(reply.cause().getMessage())));
            }
          });
        } catch (Exception e) {
          asyncResultHandler.handle(Future.succeededFuture(
            PutHoldingsStorageHoldingsByHoldingsRecordIdResponse
              .withPlainInternalServerError(e.getMessage())));
        }
      });
    } catch (Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(
        PutHoldingsStorageHoldingsByHoldingsRecordIdResponse
          .withPlainInternalServerError(e.getMessage())));
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
