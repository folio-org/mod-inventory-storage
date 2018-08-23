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
import org.folio.rest.jaxrs.resource.InstanceStorageResource;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PostgresClient;
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
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class InstanceStorageAPI implements InstanceStorageResource {

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  // Has to be lowercase because raml-module-builder uses case sensitive
  // lower case headers
  private static final String TENANT_HEADER = "x-okapi-tenant";
  public static final String MODULE = "mod_inventory_storage";
  public static final String INSTANCE_HOLDINGS_VIEW = "instance_holding_view";
  public static final String INSTANCE_HOLDINGS_ITEMS_VIEW = "instance_holding_item_view";
  public static final String INSTANCE_TABLE =  "instance";
  private String tableName =  "instance";
  private static final String INSTANCE_SOURCE_MARC_TABLE = "instance_source_marc";
  private static final String INSTANCE_RELATIONSHIP_TABLE = "instance_relationship";
  private final Messages messages = Messages.getInstance();

  private CQLWrapper handleCQL(String query, int limit, int offset) throws FieldException {
    boolean containsHoldingsRecordProperties = query != null && query.contains("holdingsRecords.");
    boolean containsItemsRecordProperties = query != null && query.contains("item.");

    if(containsItemsRecordProperties && containsHoldingsRecordProperties) {
      tableName = INSTANCE_HOLDINGS_ITEMS_VIEW;

      //it_jsonb is the alias given items in the view in the DB
      query = query.replaceAll("(?i)item\\.", INSTANCE_HOLDINGS_ITEMS_VIEW+".it_jsonb.");

      //ho_jsonb is the alias given holdings in the view in the DB
      query = query.replaceAll("(?i)holdingsRecords\\.", INSTANCE_HOLDINGS_ITEMS_VIEW+".ho_jsonb.");

      return createCQLWrapper(query, limit, offset, Arrays.asList(
        INSTANCE_HOLDINGS_ITEMS_VIEW + ".jsonb",
        INSTANCE_HOLDINGS_ITEMS_VIEW + ".it_jsonb",
        INSTANCE_HOLDINGS_ITEMS_VIEW + ".ho_jsonb"));
    }

    if(containsItemsRecordProperties) {
      tableName = INSTANCE_HOLDINGS_ITEMS_VIEW;

      //it_jsonb is the alias given items in the view in the DB
      query = query.replaceAll("(?i)item\\.", INSTANCE_HOLDINGS_ITEMS_VIEW+".it_jsonb.");

      return createCQLWrapper(query, limit, offset, Arrays.asList(
        INSTANCE_HOLDINGS_ITEMS_VIEW + ".jsonb",
        INSTANCE_HOLDINGS_ITEMS_VIEW + ".it_jsonb"));
    }

    if(containsHoldingsRecordProperties) {
      tableName = INSTANCE_HOLDINGS_VIEW;

      //ho_jsonb is the alias given holdings in the view in the DB
      query = query.replaceAll("(?i)holdingsRecords\\.", INSTANCE_HOLDINGS_VIEW+".ho_jsonb.");

      return createCQLWrapper(query, limit, offset, Arrays.asList(
        INSTANCE_HOLDINGS_VIEW+".jsonb",
        INSTANCE_HOLDINGS_VIEW+".ho_jsonb"));
    }

    return createCQLWrapper(query, limit, offset, Arrays.asList(INSTANCE_TABLE+".jsonb"));
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
    Context vertxContext) throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    try {
      vertxContext.runOnContext(v -> {
        try {
          PostgresClient postgresClient = PostgresClient.getInstance(
            vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

          String[] fieldList = {"*"};

          CQLWrapper cql = handleCQL(query, limit, offset);

          log.info(String.format("SQL generated from CQL: %s", cql.toString()));

          postgresClient.get(tableName, Instance.class, fieldList, cql,
            true, false, reply -> {
              try {
                if(reply.succeeded()) {
                  List<Instance> instances = (List<Instance>) reply.result().getResults();

                  Instances instanceList = new Instances();
                  instanceList.setInstances(instances);
                  instanceList.setTotalRecords(reply.result().getResultInfo().getTotalRecords());

                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    InstanceStorageResource.GetInstanceStorageInstancesResponse.
                      withJsonOK(instanceList)));
                }
                else {
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    InstanceStorageResource.GetInstanceStorageInstancesResponse.
                      withPlainInternalServerError(reply.cause().getMessage())));
                }
              } catch (Exception e) {
                e.printStackTrace();
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  InstanceStorageResource.GetInstanceStorageInstancesResponse.
                    withPlainInternalServerError(e.getMessage())));
              }
            });
        } catch (Exception e) {
          e.printStackTrace();
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            InstanceStorageResource.GetInstanceStorageInstancesResponse.
              withPlainInternalServerError(e.getMessage())));
        }
      });
    } catch (Exception e) {
      e.printStackTrace();
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        InstanceStorageResource.GetInstanceStorageInstancesResponse.
          withPlainInternalServerError(e.getMessage())));
    }
  }

  @Override
  public void postInstanceStorageInstances(
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Instance entity,
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
                InstanceStorageResource.PostInstanceStorageInstancesResponse
                  .withPlainBadRequest("ID must be a UUID"));
            }
          }

          postgresClient.save("instance", entity.getId(), entity,
            reply -> {
              try {
                if(reply.succeeded()) {
                  OutStream stream = new OutStream();
                  stream.setData(entity);

                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(
                      InstanceStorageResource.PostInstanceStorageInstancesResponse
                        .withJsonCreated(reply.result(), stream)));
                }
                else {
                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(
                      InstanceStorageResource.PostInstanceStorageInstancesResponse
                        .withPlainBadRequest(reply.cause().getMessage())));
                }
              } catch (Exception e) {
                e.printStackTrace();
                asyncResultHandler.handle(
                  io.vertx.core.Future.succeededFuture(
                    InstanceStorageResource.PostInstanceStorageInstancesResponse
                      .withPlainInternalServerError(e.getMessage())));
              }
            });
        } catch (Exception e) {
          e.printStackTrace();
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            InstanceStorageResource.PostInstanceStorageInstancesResponse
              .withPlainInternalServerError(e.getMessage())));
        }
      });
    } catch (Exception e) {
      e.printStackTrace();
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        InstanceStorageResource.PostInstanceStorageInstancesResponse
          .withPlainInternalServerError(e.getMessage())));
    }
  }

  @Override
  public void deleteInstanceStorageInstances(
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {

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
                    InstanceStorageResource.DeleteInstanceStorageInstancesResponse
                    .withPlainInternalServerError(reply1.cause().getMessage())));
                return;
              }
              postgresClient.mutate(String.format("DELETE FROM "
               + tenantId + "_" + MODULE + "." + INSTANCE_RELATIONSHIP_TABLE),
              reply2 -> {
                if (! reply2.succeeded()) {
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                      InstanceStorageResource.DeleteInstanceStorageInstancesResponse
                      .withPlainInternalServerError(reply1.cause().getMessage())));
                  return;
                }
                postgresClient.mutate("DELETE FROM "
                  + tenantId + "_" + MODULE + "." + INSTANCE_TABLE, reply3 -> {
                  if (! reply3.succeeded()) {
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                      InstanceStorageResource.DeleteInstanceStorageInstancesResponse
                      .withPlainInternalServerError(reply3.cause().getMessage())));
                    return;
                  }

                  asyncResultHandler.handle(Future.succeededFuture(
                    DeleteInstanceStorageInstancesResponse.withNoContent()
                  ));
                });
              });
            });
      }
      catch(Exception e) {
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
          InstanceStorageResource.DeleteInstanceStorageInstancesResponse
            .withPlainInternalServerError(e.getMessage())));
      }
    });
  }

  @Override
  public void getInstanceStorageInstancesByInstanceId(
    @NotNull String instanceId,
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    try {
      PostgresClient postgresClient = PostgresClient.getInstance(
        vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

      String[] fieldList = {"*"};

      CQLWrapper cql = handleCQL(String.format("id==%s", instanceId), 1, 0);

      log.info(String.format("SQL generated from CQL: %s", cql.toString()));

      vertxContext.runOnContext(v -> {
        try {
          postgresClient.get(tableName, Instance.class, fieldList, cql, true, false,
            reply -> {
              try {
                if (reply.succeeded()) {
                  List<Instance> instanceList = (List<Instance>) reply.result().getResults();
                  if (instanceList.size() == 1) {
                    Instance instance = instanceList.get(0);

                    asyncResultHandler.handle(
                      io.vertx.core.Future.succeededFuture(
                        InstanceStorageResource.GetInstanceStorageInstancesByInstanceIdResponse.
                          withJsonOK(instance)));
                  }
                  else {
                  asyncResultHandler.handle(
                    Future.succeededFuture(
                      InstanceStorageResource.GetInstanceStorageInstancesByInstanceIdResponse.
                        withPlainNotFound("Not Found")));
                  }
                } else {
                  asyncResultHandler.handle(
                    Future.succeededFuture(
                      InstanceStorageResource.GetInstanceStorageInstancesByInstanceIdResponse.
                        withPlainInternalServerError(reply.cause().getMessage())));

                }
              } catch (Exception e) {
                e.printStackTrace();
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  InstanceStorageResource.GetInstanceStorageInstancesByInstanceIdResponse.
                    withPlainInternalServerError(e.getMessage())));
              }
            });
        } catch (Exception e) {
          e.printStackTrace();
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            InstanceStorageResource.GetInstanceStorageInstancesByInstanceIdResponse.
              withPlainInternalServerError(e.getMessage())));
        }
      });
    } catch (Exception e) {
      e.printStackTrace();
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        InstanceStorageResource.GetInstanceStorageInstancesByInstanceIdResponse.
          withPlainInternalServerError(e.getMessage())));
    }
  }

  @Override
  public void deleteInstanceStorageInstancesByInstanceId(
    @NotNull String instanceId,
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {

    // before deleting the instance record delete its source marc record (foreign key!)

    PostgresClient postgresClient =
        PostgresClient.getInstance(vertxContext.owner(), TenantTool.tenantId(okapiHeaders));

    postgresClient.delete(INSTANCE_SOURCE_MARC_TABLE, instanceId, reply1 -> {
      if (! reply1.succeeded()) {
        asyncResultHandler.handle(Future.succeededFuture(
            DeleteInstanceStorageInstancesByInstanceIdResponse
            .withPlainInternalServerError(reply1.cause().getMessage())));
        return;
      }

      postgresClient.delete(INSTANCE_TABLE, instanceId, reply2 -> {
        if (! reply2.succeeded()) {
          asyncResultHandler.handle(Future.succeededFuture(
              DeleteInstanceStorageInstancesByInstanceIdResponse
              .withPlainInternalServerError(reply2.cause().getMessage())));
          return;
        }

        asyncResultHandler.handle(Future.succeededFuture(
            DeleteInstanceStorageInstancesByInstanceIdResponse.withNoContent()));
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
    Context vertxContext) throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    try {
      PostgresClient postgresClient =
        PostgresClient.getInstance(
          vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

      vertxContext.runOnContext(v -> {
        try {
          String[] fieldList = {"*"};

          CQLWrapper cql = handleCQL(String.format("id==%s", instanceId), 1, 0);

          postgresClient.get(tableName, Instance.class, fieldList, cql, true, false,
            reply -> {
              if(reply.succeeded()) {
                List<Instance> instancesList = (List<Instance>) reply.result().getResults();

                if (instancesList.size() == 1) {
                  try {
                    postgresClient.update(tableName, entity, entity.getId(),
                      update -> {
                        try {
                          if(update.succeeded()) {
                            OutStream stream = new OutStream();
                            stream.setData(entity);

                            asyncResultHandler.handle(
                              Future.succeededFuture(
                                PutInstanceStorageInstancesByInstanceIdResponse
                                  .withNoContent()));
                          }
                          else {
                            asyncResultHandler.handle(
                              Future.succeededFuture(
                                PutInstanceStorageInstancesByInstanceIdResponse
                                  .withPlainInternalServerError(
                                    update.cause().getMessage())));
                          }
                        } catch (Exception e) {
                          asyncResultHandler.handle(
                            Future.succeededFuture(
                              PutInstanceStorageInstancesByInstanceIdResponse
                                .withPlainInternalServerError(e.getMessage())));
                        }
                      });
                  } catch (Exception e) {
                    asyncResultHandler.handle(Future.succeededFuture(
                      PutInstanceStorageInstancesByInstanceIdResponse
                        .withPlainInternalServerError(e.getMessage())));
                  }
              }
              else {
                try {
                  postgresClient.save(tableName, entity.getId(), entity,
                    save -> {
                      try {
                        if(save.succeeded()) {
                          OutStream stream = new OutStream();
                          stream.setData(entity);

                          asyncResultHandler.handle(
                            Future.succeededFuture(
                              PutInstanceStorageInstancesByInstanceIdResponse
                                .withNoContent()));
                        }
                        else {
                          asyncResultHandler.handle(
                            Future.succeededFuture(
                              PutInstanceStorageInstancesByInstanceIdResponse
                                .withPlainInternalServerError(
                                  save.cause().getMessage())));
                        }
                      } catch (Exception e) {
                        asyncResultHandler.handle(
                          Future.succeededFuture(
                            PutInstanceStorageInstancesByInstanceIdResponse
                              .withPlainInternalServerError(e.getMessage())));
                      }
                    });
                } catch (Exception e) {
                  asyncResultHandler.handle(Future.succeededFuture(
                    PutInstanceStorageInstancesByInstanceIdResponse
                      .withPlainInternalServerError(e.getMessage())));
                }
              }
            } else {
                asyncResultHandler.handle(Future.succeededFuture(
                  PutInstanceStorageInstancesByInstanceIdResponse
                    .withPlainInternalServerError(reply.cause().getMessage())));
            }
          });
        } catch (Exception e) {
          asyncResultHandler.handle(Future.succeededFuture(
            PutInstanceStorageInstancesByInstanceIdResponse
              .withPlainInternalServerError(e.getMessage())));
        }
      });
    } catch (Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(
        PutInstanceStorageInstancesByInstanceIdResponse
          .withPlainInternalServerError(e.getMessage())));
    }
  }

  private void badRequestResult(
    Handler<AsyncResult<Response>> asyncResultHandler, String message) {
    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
      InstanceStorageResource.GetInstanceStorageInstancesResponse
        .withPlainBadRequest(message)));
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
  public void deleteInstanceStorageInstancesByInstanceIdSourceRecord(
      @NotNull String instanceId,
      @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) throws Exception {

    PostgresClient postgresClient =
        PostgresClient.getInstance(vertxContext.owner(), TenantTool.tenantId(okapiHeaders));
    postgresClient.delete(INSTANCE_SOURCE_MARC_TABLE, instanceId, reply -> {
      if (! reply.succeeded()) {
        asyncResultHandler.handle(Future.succeededFuture(
            DeleteInstanceStorageInstancesByInstanceIdSourceRecordResponse
            .withPlainInternalServerError(reply.cause().getMessage())));
        return;
      }

      asyncResultHandler.handle(Future.succeededFuture(
          DeleteInstanceStorageInstancesByInstanceIdSourceRecordResponse.withNoContent()));
    });
  }

  @Override
  public void getInstanceStorageInstancesByInstanceIdSourceRecordMarcJson(
      String instanceId, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

    PostgresClient postgresClient =
        PostgresClient.getInstance(vertxContext.owner(), TenantTool.tenantId(okapiHeaders));

    String where = "WHERE _id='" + instanceId + "'";
    postgresClient.get(INSTANCE_SOURCE_MARC_TABLE, MarcJson.class, where, false, false, reply -> {
      if (! reply.succeeded()) {
        asyncResultHandler.handle(Future.succeededFuture(
            GetInstanceStorageInstancesByInstanceIdSourceRecordMarcJsonResponse
            .withPlainInternalServerError(reply.cause().getMessage())));
        return;
      }
      List<MarcJson> results = (List<MarcJson>) reply.result().getResults();
      if (results.isEmpty()) {
        asyncResultHandler.handle(Future.succeededFuture(
            GetInstanceStorageInstancesByInstanceIdSourceRecordMarcJsonResponse
            .withPlainNotFound("No source record for instance " + instanceId)));
        return;
      }
      MarcJson marcJson = results.get(0);
      if (marcJson == null) {
        asyncResultHandler.handle(Future.succeededFuture(
            GetInstanceStorageInstancesByInstanceIdSourceRecordMarcJsonResponse
            .withPlainNotFound("No MARC source record for instance " + instanceId)));
        return;
      }
      asyncResultHandler.handle(Future.succeededFuture(
          GetInstanceStorageInstancesByInstanceIdSourceRecordMarcJsonResponse.withJsonOK(
              marcJson)));
    });
  }

  @Override
  public void deleteInstanceStorageInstancesByInstanceIdSourceRecordMarcJson(
      @NotNull String instanceId,
      @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) throws Exception {

    PostgresClient postgresClient =
        PostgresClient.getInstance(vertxContext.owner(), TenantTool.tenantId(okapiHeaders));

    postgresClient.delete(INSTANCE_SOURCE_MARC_TABLE, instanceId, reply -> {
      if (! reply.succeeded()) {
        asyncResultHandler.handle(Future.succeededFuture(
            DeleteInstanceStorageInstancesByInstanceIdSourceRecordMarcJsonResponse
            .withPlainInternalServerError(reply.cause().getMessage())));
        return;
      }
      asyncResultHandler.handle(Future.succeededFuture(
          DeleteInstanceStorageInstancesByInstanceIdSourceRecordMarcJsonResponse.withNoContent()));
    });
  }

  @Override
  public void putInstanceStorageInstancesByInstanceIdSourceRecordMarcJson(
      @NotNull String instanceId,
      @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
      MarcJson entity,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) throws Exception {

    PostgresClient postgresClient =
        PostgresClient.getInstance(vertxContext.owner(), TenantTool.tenantId(okapiHeaders));
    String sql = "INSERT INTO " + TenantTool.tenantId(okapiHeaders) + "_" + MODULE + "."
        + INSTANCE_SOURCE_MARC_TABLE
        + " (_id,jsonb)"
        + " VALUES ('" + instanceId + "', '" + PostgresClient.pojo2json(entity) + "'::JSONB)";
    postgresClient.mutate(sql, reply -> {
      if (reply.succeeded()) {
        asyncResultHandler.handle(Future.succeededFuture(
            PutInstanceStorageInstancesByInstanceIdSourceRecordMarcJsonResponse.withNoContent()));
        return;
      }
      Map<Object, String> fields = PgExceptionUtil.getBadRequestFields(reply.cause());
      if (fields != null && "23503".equals(fields.get('C'))) {  // foreign key constraint violation
        asyncResultHandler.handle(Future.succeededFuture(
            PutInstanceStorageInstancesByInstanceIdSourceRecordMarcJsonResponse
            .withPlainNotFound(reply.cause().getMessage())));
        return;
      }
      asyncResultHandler.handle(Future.succeededFuture(
          PutInstanceStorageInstancesByInstanceIdSourceRecordMarcJsonResponse
          .withPlainInternalServerError(reply.cause().getMessage())));
    });
  }

  /**
   * Example stub showing how other formats might get implemented.
   */
  @Override
  public void getInstanceStorageInstancesByInstanceIdSourceRecordMods(
      String instanceId, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    asyncResultHandler.handle(Future.succeededFuture(
        GetInstanceStorageInstancesByInstanceIdSourceRecordModsResponse
        .withPlainInternalServerError("Not implemented yet.")));
  }

  /**
   * Example stub showing how other formats might get implemented.
   */
  @Override
  public void putInstanceStorageInstancesByInstanceIdSourceRecordMods(
      String instanceId, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    asyncResultHandler.handle(Future.succeededFuture(
        PutInstanceStorageInstancesByInstanceIdSourceRecordModsResponse
        .withPlainInternalServerError("Not implemented yet.")));
  }

  @Override
  public void getInstanceStorageInstanceRelationships(int offset, int limit, String query, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    PostgresClient postgresClient =
        PostgresClient.getInstance(vertxContext.owner(), TenantTool.tenantId(okapiHeaders));

    String tenantId = okapiHeaders.get(TENANT_HEADER);

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
                  List<InstanceRelationship> instanceRelationships = (List<InstanceRelationship>) reply.result().getResults();

                  InstanceRelationships instanceList = new InstanceRelationships();
                  instanceList.setInstanceRelationships(instanceRelationships);
                  instanceList.setTotalRecords(reply.result().getResultInfo().getTotalRecords());

                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                          GetInstanceStorageInstanceRelationshipsResponse.withJsonOK(instanceList)));
                }
                else {
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    InstanceStorageResource.GetInstanceStorageInstanceRelationshipsResponse.
                      withPlainInternalServerError(reply.cause().getMessage())));
                }
              } catch (Exception e) {
                e.printStackTrace();
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  InstanceStorageResource.GetInstanceStorageInstanceRelationshipsResponse.
                    withPlainInternalServerError(e.getMessage())));
              }
            });
        } catch (Exception e) {
          e.printStackTrace();
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            InstanceStorageResource.GetInstanceStorageInstanceRelationshipsResponse.
              withPlainInternalServerError(e.getMessage())));
        }
      });
    } catch (Exception e) {
      e.printStackTrace();
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        InstanceStorageResource.GetInstanceStorageInstanceRelationshipsResponse.
          withPlainInternalServerError(e.getMessage())));
    }
  }

  @Override
  public void postInstanceStorageInstanceRelationships(String lang, InstanceRelationship entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
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
                      InstanceStorageResource.PostInstanceStorageInstanceRelationshipsResponse
                        .withJsonCreated(reply.result(), stream)));
                }
                else {
                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(
                      InstanceStorageResource.PostInstanceStorageInstanceRelationshipsResponse
                        .withPlainBadRequest(reply.cause().getMessage())));
                }
              } catch (Exception e) {
                e.printStackTrace();
                asyncResultHandler.handle(
                  io.vertx.core.Future.succeededFuture(
                    InstanceStorageResource.PostInstanceStorageInstanceRelationshipsResponse
                      .withPlainInternalServerError(e.getMessage())));
              }
            });
        } catch (Exception e) {
          e.printStackTrace();
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            InstanceStorageResource.PostInstanceStorageInstanceRelationshipsResponse
              .withPlainInternalServerError(e.getMessage())));
        }
      });
    } catch (Exception e) {
      e.printStackTrace();
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        InstanceStorageResource.PostInstanceStorageInstanceRelationshipsResponse
          .withPlainInternalServerError(e.getMessage())));
    }
  }

  @Override
  public void getInstanceStorageInstanceRelationshipsByRelationshipId(String relationshipId, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public void deleteInstanceStorageInstanceRelationshipsByRelationshipId(String relationshipId, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    PostgresClient postgresClient =
        PostgresClient.getInstance(vertxContext.owner(), TenantTool.tenantId(okapiHeaders));

    postgresClient.delete(INSTANCE_RELATIONSHIP_TABLE, relationshipId, reply -> {
      if (! reply.succeeded()) {
        asyncResultHandler.handle(Future.succeededFuture(
            DeleteInstanceStorageInstanceRelationshipsByRelationshipIdResponse
            .withPlainInternalServerError(reply.cause().getMessage())));
        return;
      }
      asyncResultHandler.handle(Future.succeededFuture(
          InstanceStorageResource.DeleteInstanceStorageInstanceRelationshipsByRelationshipIdResponse
                  .withNoContent()));
    });
  }

  @Override
  public void putInstanceStorageInstanceRelationshipsByRelationshipId(String relationshipId, String lang, InstanceRelationship entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    vertxContext.runOnContext(v -> {
      String tenantId = TenantTool.tenantId(okapiHeaders);
      try {
        if (entity.getId() == null) {
          entity.setId(relationshipId);
        }
        try {
          getInstanceRelationship(vertxContext.owner(), tenantId, relationshipId, replyHandler -> {
            log.info("in getInstanceRelationship handler");
            if (replyHandler.succeeded()) {
              InstanceRelationship relationshipToUpdate = ((InstanceRelationship)replyHandler.result());
              if (relationshipToUpdate.getInstanceRelationshipTypeId().equals(entity.getInstanceRelationshipTypeId())
                  && relationshipToUpdate.getSuperInstanceId().equals(entity.getSuperInstanceId())
                  && relationshipToUpdate.getSubInstanceId().equals(entity.getSubInstanceId())) {
                log.info("Skipping update of relationship with no modified properties.");
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutInstanceStorageInstanceRelationshipsByRelationshipIdResponse
                   .withNoContent()));
              } else {
                PostgresClient.getInstance(vertxContext.owner(), tenantId).update(
                INSTANCE_RELATIONSHIP_TABLE, entity, relationshipId,
                reply -> {
                  try {
                    if (reply.succeeded()) {
                      if (reply.result().getUpdated() == 0) {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutInstanceStorageInstanceRelationshipsByRelationshipIdResponse
                            .withPlainNotFound(messages.getMessage(lang, MessageConsts.NoRecordsUpdated))));
                      } else{
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutInstanceStorageInstanceRelationshipsByRelationshipIdResponse
                            .withNoContent()));
                      }
                    } else {
                      String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                      if (msg == null) {
                        asyncResultHandler.handle(Future.succeededFuture(PutInstanceStorageInstanceRelationshipsByRelationshipIdResponse
                           .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
                      }
                      log.info(msg);
                      asyncResultHandler.handle(Future.succeededFuture(PutInstanceStorageInstanceRelationshipsByRelationshipIdResponse
                          .withPlainBadRequest(msg)));
                    }
                  } catch (Exception e) {
                    asyncResultHandler.handle(Future.succeededFuture(PutInstanceStorageInstanceRelationshipsByRelationshipIdResponse
                       .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
                  }

                });
              }
            } else {
              log.info("The instance relationship to update was not found");
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutInstanceStorageInstanceRelationshipsByRelationshipIdResponse
                  .withPlainNotFound(messages.getMessage(lang, MessageConsts.NoRecordsUpdated))));
            }
          });
        } catch (Exception e) {
          log.info(e);
        }
      } catch (Exception e) {
        asyncResultHandler.handle(Future.succeededFuture(PutInstanceStorageInstanceRelationshipsByRelationshipIdResponse
           .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  private void getInstanceRelationship  (
    Vertx vertx,
    String tenantId,
    String instanceRelationshipId,
    Handler<AsyncResult> handler) throws Exception {
      try {
        String[] fieldList = {"*"};
        String query = String.format("id==%s",instanceRelationshipId);
        CQLWrapper cql = createCQLWrapper(query, 1, 0, Arrays.asList(INSTANCE_RELATIONSHIP_TABLE+".jsonb"));
        PostgresClient.getInstance(vertx, tenantId).get(INSTANCE_RELATIONSHIP_TABLE,
          InstanceRelationship.class, fieldList, cql, true, false, getReply -> {
            if(getReply.failed()) {
              handler.handle(Future.failedFuture(getReply.cause()));
            } else {
              List<InstanceRelationship> instanceRelationshipList = (List<InstanceRelationship>) getReply.result().getResults();
              if(instanceRelationshipList.size() < 1) {
                log.info("No relationship found: "+ instanceRelationshipList.size());
                handler.handle(Future.failedFuture("No relationship found"));
              } else {
                handler.handle(Future.succeededFuture(instanceRelationshipList.get(0)));
              }
            }
          });
      } catch (FieldException fe) {
        handler.handle(Future.failedFuture(fe.getCause()));
      }
  }
}
