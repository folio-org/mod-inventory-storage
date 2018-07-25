package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
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
import org.folio.rest.jaxrs.model.Instances;
import org.folio.rest.jaxrs.model.MarcJson;
import org.folio.rest.jaxrs.resource.InstanceStorageResource;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.TenantTool;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;

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

        postgresClient.mutate(String.format("TRUNCATE TABLE "
              + tenantId + "_" + MODULE + "." + INSTANCE_TABLE + ", "
              + tenantId + "_" + MODULE + "." + INSTANCE_SOURCE_MARC_TABLE),
            reply -> {
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  InstanceStorageResource.DeleteInstanceStorageInstancesResponse
                  .noContent().build()));
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
}
