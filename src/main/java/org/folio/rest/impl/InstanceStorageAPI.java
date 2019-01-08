package org.folio.rest.impl;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.InstanceRelationship;
import org.folio.rest.jaxrs.model.InstanceRelationships;
import org.folio.rest.jaxrs.model.Instances;
import org.folio.rest.jaxrs.model.MarcJson;
import org.folio.rest.jaxrs.resource.InstanceStorage;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.TenantTool;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class InstanceStorageAPI implements InstanceStorage {

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  // Has to be lowercase because raml-module-builder uses case sensitive
  // lower case headers
  private static final String TENANT_HEADER = "x-okapi-tenant";
  public static final String MODULE = "mod_inventory_storage";
  public static final String INSTANCE_HOLDINGS_VIEW = "instance_holding_view";
  public static final String INSTANCE_HOLDINGS_ITEMS_VIEW = "instance_holding_item_view";
  public static final String INSTANCE_TABLE =  "instance";
  private static final String INSTANCE_SOURCE_MARC_TABLE = "instance_source_marc";
  private static final String INSTANCE_RELATIONSHIP_TABLE = "instance_relationship";
  private final Messages messages = Messages.getInstance();

  PreparedCQL handleCQL(String query, int limit, int offset) throws FieldException {
    boolean containsHoldingsRecordProperties = query != null && query.contains("holdingsRecords.");
    boolean containsItemsRecordProperties = query != null && query.contains("item.");

    if(containsItemsRecordProperties && containsHoldingsRecordProperties) {
      //it_jsonb is the alias given items in the view in the DB
      query = query.replaceAll("item\\.", INSTANCE_HOLDINGS_ITEMS_VIEW+".it_jsonb.");

      //ho_jsonb is the alias given holdings in the view in the DB
      query = query.replaceAll("holdingsRecords\\.", INSTANCE_HOLDINGS_ITEMS_VIEW+".ho_jsonb.");

      return new PreparedCQL(INSTANCE_HOLDINGS_ITEMS_VIEW, createCQLWrapper(query, limit, offset, Arrays.asList(
        INSTANCE_HOLDINGS_ITEMS_VIEW + ".jsonb",
        INSTANCE_HOLDINGS_ITEMS_VIEW + ".it_jsonb",
          INSTANCE_HOLDINGS_ITEMS_VIEW + ".ho_jsonb")));
    }

    if(containsItemsRecordProperties) {
      //it_jsonb is the alias given items in the view in the DB
      query = query.replaceAll("item\\.", INSTANCE_HOLDINGS_ITEMS_VIEW+".it_jsonb.");

      return new PreparedCQL(INSTANCE_HOLDINGS_ITEMS_VIEW, createCQLWrapper(query, limit, offset, Arrays.asList(
        INSTANCE_HOLDINGS_ITEMS_VIEW + ".jsonb",
          INSTANCE_HOLDINGS_ITEMS_VIEW + ".it_jsonb")));
    }

    if(containsHoldingsRecordProperties) {
      //ho_jsonb is the alias given holdings in the view in the DB
      query = query.replaceAll("holdingsRecords\\.", INSTANCE_HOLDINGS_VIEW+".ho_jsonb.");

      return new PreparedCQL(INSTANCE_HOLDINGS_VIEW, createCQLWrapper(query, limit, offset, Arrays.asList(
        INSTANCE_HOLDINGS_VIEW+".jsonb",
          INSTANCE_HOLDINGS_VIEW + ".ho_jsonb")));
    }

    return new PreparedCQL(INSTANCE_TABLE,
        createCQLWrapper(query, limit, offset, Arrays.asList(INSTANCE_TABLE + ".jsonb")));
  }

  private CQLWrapper createCQLWrapper(
    String query,
    int limit,
    int offset,
    List<String> fields) throws FieldException {

    CQL2PgJSON cql2pgJson = new CQL2PgJSON(fields);

    return new CQLWrapper(cql2pgJson, query)
      .setLimit(new Limit(limit))
      .setOffset(new Offset(offset));
  }

  @Override
  public void getInstanceStorageInstances(
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

                PreparedCQL preparedCql = handleCQL(query, limit, offset);
          CQLWrapper cql = preparedCql.getCqlWrapper();

          log.info(String.format("SQL generated from CQL: %s", cql.toString()));

          postgresClient.get(preparedCql.getTableName(), Instance.class, fieldList, cql,
            true, false, reply -> {
              try {
                if(reply.succeeded()) {
                  List<Instance> instances = reply.result().getResults();

                  Instances instanceList = new Instances();
                  instanceList.setInstances(instances);
                  instanceList.setTotalRecords(reply.result().getResultInfo().getTotalRecords());

                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    GetInstanceStorageInstancesResponse.
                      respond200WithApplicationJson(instanceList)));
                }
                else {
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    GetInstanceStorageInstancesResponse.
                      respond500WithTextPlain(reply.cause().getMessage())));
                }
              } catch (Exception e) {
                log.error(e.getStackTrace());
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  GetInstanceStorageInstancesResponse.
                    respond500WithTextPlain(e.getMessage())));
              }
            });
        } catch (Exception e) {
          log.error(e.getStackTrace());
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetInstanceStorageInstancesResponse.
              respond500WithTextPlain(e.getMessage())));
        }
      });
    } catch (Exception e) {
      log.error(e.getStackTrace());
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        GetInstanceStorageInstancesResponse.
          respond500WithTextPlain(e.getMessage())));
    }
  }

  @Override
  public void postInstanceStorageInstances(
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Instance entity,
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
              asyncResultHandler.handle(Future.succeededFuture(
                PostInstanceStorageInstancesResponse
                  .respond400WithTextPlain("ID must be a UUID")));
              return;
            }
          }

          postgresClient.save(INSTANCE_TABLE, entity.getId(), entity,
            reply -> {
              try {
                if(reply.succeeded()) {
                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(
                      PostInstanceStorageInstancesResponse
                        .respond201WithApplicationJson(entity,PostInstanceStorageInstancesResponse.headersFor201().withLocation(reply.result()))));
                }
                else {
                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(
                      PostInstanceStorageInstancesResponse
                        .respond400WithTextPlain(reply.cause().getMessage())));
                }
              } catch (Exception e) {
                log.error(e.getStackTrace());
                asyncResultHandler.handle(
                  io.vertx.core.Future.succeededFuture(
                    PostInstanceStorageInstancesResponse
                      .respond500WithTextPlain(e.getMessage())));
              }
            });
        } catch (Exception e) {
          log.error(e.getStackTrace());
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            PostInstanceStorageInstancesResponse
              .respond500WithTextPlain(e.getMessage())));
        }
      });
    } catch (Exception e) {
      log.error(e.getStackTrace());
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        PostInstanceStorageInstancesResponse
          .respond500WithTextPlain(e.getMessage())));
    }
  }

  @Override
  public void deleteInstanceStorageInstances(
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    vertxContext.runOnContext(v -> {
      try {
        PostgresClient postgresClient = PostgresClient.getInstance(
          vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

        postgresClient.mutate(String.format("DELETE FROM "
              + tenantId + "_" + MODULE + "." + INSTANCE_SOURCE_MARC_TABLE ),
            reply1 -> {
              if (! reply1.succeeded()) {
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    DeleteInstanceStorageInstancesResponse
                    .respond500WithTextPlain(reply1.cause().getMessage())));
                return;
              }
              postgresClient.mutate(String.format("DELETE FROM "
               + tenantId + "_" + MODULE + "." + INSTANCE_RELATIONSHIP_TABLE),
              reply2 -> {
                if (! reply2.succeeded()) {
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                      DeleteInstanceStorageInstancesResponse
                      .respond500WithTextPlain(reply1.cause().getMessage())));
                  return;
                }
                postgresClient.mutate("DELETE FROM "
                  + tenantId + "_" + MODULE + "." + INSTANCE_TABLE, reply3 -> {
                  if (! reply3.succeeded()) {
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                      DeleteInstanceStorageInstancesResponse
                      .respond500WithTextPlain(reply3.cause().getMessage())));
                    return;
                  }

                  asyncResultHandler.handle(Future.succeededFuture(
                    DeleteInstanceStorageInstancesResponse.respond204()
                  ));
                });
              });
            });
      }
      catch(Exception e) {
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
          DeleteInstanceStorageInstancesResponse
            .respond500WithTextPlain(e.getMessage())));
      }
    });
  }

  @Override
  public void getInstanceStorageInstancesByInstanceId(
    @NotNull String instanceId,
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    try {
      PostgresClient postgresClient = PostgresClient.getInstance(
        vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

      String[] fieldList = {"*"};

      PreparedCQL preparedCql = handleCQL(String.format("id==%s", instanceId), 1, 0);
      CQLWrapper cql = preparedCql.getCqlWrapper();

      log.info(String.format("SQL generated from CQL: %s", cql.toString()));

      vertxContext.runOnContext(v -> {
        try {
          postgresClient.get(preparedCql.getTableName(), Instance.class, fieldList, cql, true, false,
            reply -> {
              try {
                if (reply.succeeded()) {
                  List<Instance> instanceList = reply.result().getResults();
                  if (instanceList.size() == 1) {
                    Instance instance = instanceList.get(0);

                    asyncResultHandler.handle(
                      io.vertx.core.Future.succeededFuture(
                        GetInstanceStorageInstancesByInstanceIdResponse.
                          respond200WithApplicationJson(instance)));
                  }
                  else {
                  asyncResultHandler.handle(
                    Future.succeededFuture(
                      GetInstanceStorageInstancesByInstanceIdResponse.
                        respond404WithTextPlain("Not Found")));
                  }
                } else {
                  asyncResultHandler.handle(
                    Future.succeededFuture(
                      GetInstanceStorageInstancesByInstanceIdResponse.
                        respond500WithTextPlain(reply.cause().getMessage())));

                }
              } catch (Exception e) {
                log.error(e.getStackTrace());
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  GetInstanceStorageInstancesByInstanceIdResponse.
                    respond500WithTextPlain(e.getMessage())));
              }
            });
        } catch (Exception e) {
          log.error(e.getStackTrace());
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetInstanceStorageInstancesByInstanceIdResponse.
              respond500WithTextPlain(e.getMessage())));
        }
      });
    } catch (Exception e) {
      log.error(e.getStackTrace());
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        GetInstanceStorageInstancesByInstanceIdResponse.
          respond500WithTextPlain(e.getMessage())));
    }
  }

  @Override
  public void deleteInstanceStorageInstancesByInstanceId(
    @NotNull String instanceId,
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    // before deleting the instance record delete its source marc record (foreign key!)

    PostgresClient postgresClient =
        PostgresClient.getInstance(vertxContext.owner(), TenantTool.tenantId(okapiHeaders));

    postgresClient.delete(INSTANCE_SOURCE_MARC_TABLE, instanceId, reply1 -> {
      if (! reply1.succeeded()) {
        asyncResultHandler.handle(Future.succeededFuture(
            DeleteInstanceStorageInstancesByInstanceIdResponse
            .respond500WithTextPlain(reply1.cause().getMessage())));
        return;
      }

      postgresClient.delete(INSTANCE_TABLE, instanceId, reply2 -> {
        if (! reply2.succeeded()) {
          asyncResultHandler.handle(Future.succeededFuture(
              DeleteInstanceStorageInstancesByInstanceIdResponse
              .respond500WithTextPlain(reply2.cause().getMessage())));
          return;
        }

        asyncResultHandler.handle(Future.succeededFuture(
            DeleteInstanceStorageInstancesByInstanceIdResponse.respond204()));
      });
    });
  }

  @Override
  public void putInstanceStorageInstancesByInstanceId(
    @NotNull String instanceId,
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Instance entity,
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

          PreparedCQL preparedCql = handleCQL(String.format("id==%s", instanceId), 1, 0);
          CQLWrapper cql = preparedCql.getCqlWrapper();

          postgresClient.get(preparedCql.getTableName(), Instance.class, fieldList, cql, true, false,
            reply -> {
              if(reply.succeeded()) {
                List<Instance> instancesList = reply.result().getResults();

                if (instancesList.size() == 1) {
                  try {
                      postgresClient.update(preparedCql.getTableName(), entity, entity.getId(),
                      update -> {
                        try {
                          if(update.succeeded()) {
                            OutStream stream = new OutStream();
                            stream.setData(entity);

                            asyncResultHandler.handle(
                              Future.succeededFuture(
                                PutInstanceStorageInstancesByInstanceIdResponse
                                  .respond204()));
                          }
                          else {
                            asyncResultHandler.handle(
                              Future.succeededFuture(
                                PutInstanceStorageInstancesByInstanceIdResponse
                                  .respond500WithTextPlain(
                                    update.cause().getMessage())));
                          }
                        } catch (Exception e) {
                          asyncResultHandler.handle(
                            Future.succeededFuture(
                              PutInstanceStorageInstancesByInstanceIdResponse
                                .respond500WithTextPlain(e.getMessage())));
                        }
                      });
                  } catch (Exception e) {
                    asyncResultHandler.handle(Future.succeededFuture(
                      PutInstanceStorageInstancesByInstanceIdResponse
                        .respond500WithTextPlain(e.getMessage())));
                  }
              }
              else {
                try {
                      postgresClient.save(preparedCql.getTableName(), entity.getId(), entity,
                    save -> {
                      try {
                        if(save.succeeded()) {
                          OutStream stream = new OutStream();
                          stream.setData(entity);

                          asyncResultHandler.handle(
                            Future.succeededFuture(
                              PutInstanceStorageInstancesByInstanceIdResponse
                                .respond204()));
                        }
                        else {
                          asyncResultHandler.handle(
                            Future.succeededFuture(
                              PutInstanceStorageInstancesByInstanceIdResponse
                                .respond500WithTextPlain(
                                  save.cause().getMessage())));
                        }
                      } catch (Exception e) {
                        asyncResultHandler.handle(
                          Future.succeededFuture(
                            PutInstanceStorageInstancesByInstanceIdResponse
                              .respond500WithTextPlain(e.getMessage())));
                      }
                    });
                } catch (Exception e) {
                  asyncResultHandler.handle(Future.succeededFuture(
                    PutInstanceStorageInstancesByInstanceIdResponse
                      .respond500WithTextPlain(e.getMessage())));
                }
              }
            } else {
                asyncResultHandler.handle(Future.succeededFuture(
                  PutInstanceStorageInstancesByInstanceIdResponse
                    .respond500WithTextPlain(reply.cause().getMessage())));
            }
          });
        } catch (Exception e) {
          asyncResultHandler.handle(Future.succeededFuture(
            PutInstanceStorageInstancesByInstanceIdResponse
              .respond500WithTextPlain(e.getMessage())));
        }
      });
    } catch (Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(
        PutInstanceStorageInstancesByInstanceIdResponse
          .respond500WithTextPlain(e.getMessage())));
    }
  }

  private void badRequestResult(
    Handler<AsyncResult<Response>> asyncResultHandler, String message) {
    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
      GetInstanceStorageInstancesResponse.respond400WithTextPlain(message)));
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


  @Override
  public void deleteInstanceStorageInstancesSourceRecordByInstanceId(
      @NotNull String instanceId,
      @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    PostgresClient postgresClient =
        PostgresClient.getInstance(vertxContext.owner(), TenantTool.tenantId(okapiHeaders));
    postgresClient.delete(INSTANCE_SOURCE_MARC_TABLE, instanceId, reply -> {
      if (! reply.succeeded()) {
        asyncResultHandler.handle(Future.succeededFuture(
          DeleteInstanceStorageInstancesSourceRecordByInstanceIdResponse
            .respond500WithTextPlain(reply.cause().getMessage())));
        return;
      }

      asyncResultHandler.handle(Future.succeededFuture(
          DeleteInstanceStorageInstancesSourceRecordByInstanceIdResponse.respond204()));
    });
  }

  @Override
  public void getInstanceStorageInstancesSourceRecordMarcJsonByInstanceId(
      String instanceId, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    PostgresClient postgresClient =
        PostgresClient.getInstance(vertxContext.owner(), TenantTool.tenantId(okapiHeaders));

    String where = "WHERE _id='" + instanceId + "'";
    postgresClient.get(INSTANCE_SOURCE_MARC_TABLE, MarcJson.class, where, false, false, reply -> {
      if (! reply.succeeded()) {
        asyncResultHandler.handle(Future.succeededFuture(
          GetInstanceStorageInstancesSourceRecordMarcJsonByInstanceIdResponse
            .respond500WithTextPlain(reply.cause().getMessage())));
        return;
      }
      List<MarcJson> results = reply.result().getResults();
      if (results.isEmpty()) {
        asyncResultHandler.handle(Future.succeededFuture(
          GetInstanceStorageInstancesSourceRecordMarcJsonByInstanceIdResponse
            .respond404WithTextPlain("No source record for instance " + instanceId)));
        return;
      }
      MarcJson marcJson = results.get(0);
      if (marcJson == null) {
        asyncResultHandler.handle(Future.succeededFuture(
          GetInstanceStorageInstancesSourceRecordMarcJsonByInstanceIdResponse
            .respond404WithTextPlain("No MARC source record for instance " + instanceId)));
        return;
      }
      asyncResultHandler.handle(Future.succeededFuture(
          GetInstanceStorageInstancesSourceRecordMarcJsonByInstanceIdResponse.respond200WithApplicationJson(
              marcJson)));
    });
  }

  @Override
  public void deleteInstanceStorageInstancesSourceRecordMarcJsonByInstanceId(
      @NotNull String instanceId,
      @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    PostgresClient postgresClient =
        PostgresClient.getInstance(vertxContext.owner(), TenantTool.tenantId(okapiHeaders));

    postgresClient.delete(INSTANCE_SOURCE_MARC_TABLE, instanceId, reply -> {
      if (! reply.succeeded()) {
        asyncResultHandler.handle(Future.succeededFuture(
          DeleteInstanceStorageInstancesSourceRecordMarcJsonByInstanceIdResponse
            .respond500WithTextPlain(reply.cause().getMessage())));
        return;
      }
      asyncResultHandler.handle(Future.succeededFuture(
          DeleteInstanceStorageInstancesSourceRecordMarcJsonByInstanceIdResponse.respond204()));
    });
  }

  @Override
  public void putInstanceStorageInstancesSourceRecordMarcJsonByInstanceId(
      @NotNull String instanceId,
      @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
      MarcJson entity,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext)  {

    PostgresClient postgresClient =
        PostgresClient.getInstance(vertxContext.owner(), TenantTool.tenantId(okapiHeaders));
    postgresClient.upsert(INSTANCE_SOURCE_MARC_TABLE, instanceId, entity, reply -> {
      if (reply.succeeded()) {
        asyncResultHandler.handle(Future.succeededFuture(
          PutInstanceStorageInstancesSourceRecordMarcJsonByInstanceIdResponse.respond204()));
        return;
      }
      Map<Object, String> fields = PgExceptionUtil.getBadRequestFields(reply.cause());
      if (fields != null && "23503".equals(fields.get('C'))  // foreign key constraint violation
          && INSTANCE_SOURCE_MARC_TABLE.equals(fields.get('t'))) {
        asyncResultHandler.handle(Future.succeededFuture(
            PutInstanceStorageInstancesSourceRecordMarcJsonByInstanceIdResponse
            .respond404WithTextPlain(reply.cause().getMessage())));
        return;
      }
      asyncResultHandler.handle(Future.succeededFuture(
        PutInstanceStorageInstancesSourceRecordMarcJsonByInstanceIdResponse
          .respond500WithTextPlain(reply.cause().getMessage())));
    });
  }

  /**
   * Example stub showing how other formats might get implemented.
   */
  @Override
  public void getInstanceStorageInstancesSourceRecordModsByInstanceId(
      String instanceId, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    asyncResultHandler.handle(Future.succeededFuture(
      GetInstanceStorageInstancesSourceRecordModsByInstanceIdResponse
        .respond500WithTextPlain("Not implemented yet.")));
  }

  /**
   * Example stub showing how other formats might get implemented.
   */
  @Override
  public void putInstanceStorageInstancesSourceRecordModsByInstanceId(
      String instanceId, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    asyncResultHandler.handle(Future.succeededFuture(
      PutInstanceStorageInstancesSourceRecordModsByInstanceIdResponse
        .respond500WithTextPlain("Not implemented yet.")));
  }

  @Override
  public void getInstanceStorageInstanceRelationships(int offset, int limit, String query, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PostgresClient postgresClient =
        PostgresClient.getInstance(vertxContext.owner(), TenantTool.tenantId(okapiHeaders));

    try {
      vertxContext.runOnContext(v -> {
        try {

          String[] fieldList = {"*"};

          CQLWrapper cql = createCQLWrapper(query, limit, offset, Arrays.asList(INSTANCE_RELATIONSHIP_TABLE+".jsonb"));

          log.info(String.format("SQL generated from CQL: %s", cql.toString()));

          postgresClient.get(INSTANCE_RELATIONSHIP_TABLE, InstanceRelationship.class, fieldList, cql,
            true, false, reply -> {
              try {
                if(reply.succeeded()) {
                  List<InstanceRelationship> instanceRelationships = reply.result().getResults();

                  InstanceRelationships instanceList = new InstanceRelationships();
                  instanceList.setInstanceRelationships(instanceRelationships);
                  instanceList.setTotalRecords(reply.result().getResultInfo().getTotalRecords());

                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                          GetInstanceStorageInstanceRelationshipsResponse.respond200WithApplicationJson(instanceList)));
                }
                else {
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    GetInstanceStorageInstanceRelationshipsResponse.
                      respond500WithTextPlain(reply.cause().getMessage())));
                }
              } catch (Exception e) {
                log.error(e.getStackTrace());
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  GetInstanceStorageInstanceRelationshipsResponse.
                    respond500WithTextPlain(e.getMessage())));
              }
            });
        } catch (Exception e) {
          log.error(e.getStackTrace());
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetInstanceStorageInstanceRelationshipsResponse.
              respond500WithTextPlain(e.getMessage())));
        }
      });
    } catch (Exception e) {
      log.error(e.getStackTrace());
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        GetInstanceStorageInstanceRelationshipsResponse.
          respond500WithTextPlain(e.getMessage())));
    }
  }

  @Override
  public void postInstanceStorageInstanceRelationships(String lang, InstanceRelationship entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      PostgresClient postgresClient =
        PostgresClient.getInstance(vertxContext.owner(), TenantTool.tenantId(okapiHeaders));

      vertxContext.runOnContext(v -> {
        try {

          postgresClient.save(INSTANCE_RELATIONSHIP_TABLE, entity.getId(), entity,
            reply -> {
              try {
                if(reply.succeeded()) {
                  OutStream stream = new OutStream();
                  stream.setData(entity);

                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(
                      PostInstanceStorageInstanceRelationshipsResponse
                        .respond201WithApplicationJson(entity,
                          PostInstanceStorageInstanceRelationshipsResponse.headersFor201().withLocation(reply.result()))));
                }
                else {
                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(
                      PostInstanceStorageInstanceRelationshipsResponse
                        .respond400WithTextPlain(reply.cause().getMessage())));
                }
              } catch (Exception e) {
                log.error(e.getStackTrace());
                asyncResultHandler.handle(
                  io.vertx.core.Future.succeededFuture(
                    PostInstanceStorageInstanceRelationshipsResponse
                      .respond500WithTextPlain(e.getMessage())));
              }
            });
        } catch (Exception e) {
          log.error(e.getStackTrace());
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            PostInstanceStorageInstanceRelationshipsResponse
              .respond500WithTextPlain(e.getMessage())));
        }
      });
    } catch (Exception e) {
      log.error(e.getStackTrace());
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        PostInstanceStorageInstanceRelationshipsResponse
          .respond500WithTextPlain(e.getMessage())));
    }
  }

  @Override
  public void getInstanceStorageInstanceRelationshipsByRelationshipId(String relationshipId, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public void deleteInstanceStorageInstanceRelationshipsByRelationshipId(String relationshipId, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PostgresClient postgresClient =
        PostgresClient.getInstance(vertxContext.owner(), TenantTool.tenantId(okapiHeaders));

    postgresClient.delete(INSTANCE_RELATIONSHIP_TABLE, relationshipId, reply -> {
      if (! reply.succeeded()) {
        asyncResultHandler.handle(Future.succeededFuture(
            DeleteInstanceStorageInstanceRelationshipsByRelationshipIdResponse
            .respond500WithTextPlain(reply.cause().getMessage())));
        return;
      }
      asyncResultHandler.handle(Future.succeededFuture(
          DeleteInstanceStorageInstanceRelationshipsByRelationshipIdResponse.respond204()));
    });
  }

  @Override
  public void putInstanceStorageInstanceRelationshipsByRelationshipId(String relationshipId, String lang, InstanceRelationship entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      String tenantId = TenantTool.tenantId(okapiHeaders);
      try {
        if (entity.getId() == null) {
          entity.setId(relationshipId);
        }
        PostgresClient.getInstance(vertxContext.owner(), tenantId).update(
        INSTANCE_RELATIONSHIP_TABLE, entity, relationshipId,
        reply -> {
          try {
            if (reply.succeeded()) {
              if (reply.result().getUpdated() == 0) {
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutInstanceStorageInstanceRelationshipsByRelationshipIdResponse
                    .respond404WithTextPlain(messages.getMessage(lang, MessageConsts.NoRecordsUpdated))));
              } else{
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutInstanceStorageInstanceRelationshipsByRelationshipIdResponse
                    .respond204()));
              }
            } else {
              String msg = PgExceptionUtil.badRequestMessage(reply.cause());
              if (msg == null) {
                asyncResultHandler.handle(Future.succeededFuture(PutInstanceStorageInstanceRelationshipsByRelationshipIdResponse
                   .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
              }
              log.info(msg);
              asyncResultHandler.handle(Future.succeededFuture(PutInstanceStorageInstanceRelationshipsByRelationshipIdResponse
                  .respond400WithTextPlain(msg)));
            }
          } catch (Exception e) {
            asyncResultHandler.handle(Future.succeededFuture(PutInstanceStorageInstanceRelationshipsByRelationshipIdResponse
               .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
          }

        });
      } catch (Exception e) {
        asyncResultHandler.handle(Future.succeededFuture(PutInstanceStorageInstanceRelationshipsByRelationshipIdResponse
           .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  class PreparedCQL {
    private final String tableName;
    private final CQLWrapper cqlWrapper;

    public PreparedCQL(String tableName, CQLWrapper cqlWrapper) {
      this.tableName = tableName;
      this.cqlWrapper = cqlWrapper;
    }

    public String getTableName() {
      return tableName;
    }

    public CQLWrapper getCqlWrapper() {
      return cqlWrapper;
    }

  }

}
