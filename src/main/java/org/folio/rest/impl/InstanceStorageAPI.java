package org.folio.rest.impl;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.core.Response;

import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.InstanceRelationship;
import org.folio.rest.jaxrs.model.InstanceRelationships;
import org.folio.rest.jaxrs.model.Instances;
import org.folio.rest.jaxrs.model.MarcJson;
import org.folio.rest.jaxrs.resource.InstanceStorage;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.support.HridManager;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.cql2pgjson.exception.FieldException;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;

public class InstanceStorageAPI implements InstanceStorage {

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  // Has to be lowercase because raml-module-builder uses case sensitive
  // lower case headers
  private static final String TENANT_HEADER = "x-okapi-tenant";
  public static final String MODULE = "mod_inventory_storage";
  public static final String INSTANCE_TABLE =  "instance";
  private static final String INSTANCE_SOURCE_MARC_TABLE = "instance_source_marc";
  private static final String INSTANCE_RELATIONSHIP_TABLE = "instance_relationship";
  private final Messages messages = Messages.getInstance();

  PreparedCQL handleCQL(String query, int limit, int offset) throws FieldException {
    return new PreparedCQL(INSTANCE_TABLE, query, limit, offset);
  }

  private static CQLWrapper createCQLWrapper(
    String query,
    int limit,
    int offset,
    String tableName) throws FieldException {

    CQL2PgJSON cql2pgJson = new CQL2PgJSON(tableName + ".jsonb");

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
    RoutingContext routingContext, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    if (PgUtil.checkOptimizedCQL(query, "title") != null) { // Until RMB-573 is fixed
      try {
        PreparedCQL preparedCql = handleCQL(query, limit, offset);
        PgUtil.getWithOptimizedSql(preparedCql.getTableName(), Instance.class, Instances.class,
          "title", query, offset, limit,
          okapiHeaders, vertxContext, GetInstanceStorageInstancesResponse.class, asyncResultHandler);
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
          GetInstanceStorageInstancesResponse.
            respond500WithTextPlain(e.getMessage())));
      }
      return;
    }
    PgUtil.streamGet(INSTANCE_TABLE, Instance.class, query, offset, limit, null,
      "instances", routingContext, okapiHeaders, vertxContext);
  }

  @Override
  public void postInstanceStorageInstances(
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Instance entity,
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
              asyncResultHandler.handle(Future.succeededFuture(
                PostInstanceStorageInstancesResponse
                  .respond400WithTextPlain("ID must be a UUID")));
              return;
            }
          }

          final Future<String> hridFuture;
          if (isBlank(entity.getHrid())) {
            final HridManager hridManager = new HridManager(vertxContext, postgresClient);
            hridFuture = hridManager.getNextInstanceHrid();
          } else {
            hridFuture = StorageHelper.completeFuture(entity.getHrid());
          }

          hridFuture.map(hrid -> {
            entity.setHrid(hrid);
            postgresClient.save(INSTANCE_TABLE, entity.getId(), entity,
              reply -> {
                try {
                  if(reply.succeeded()) {
                    asyncResultHandler.handle(
                      io.vertx.core.Future.succeededFuture(
                        PostInstanceStorageInstancesResponse
                          .respond201WithApplicationJson(entity,
                              PostInstanceStorageInstancesResponse.headersFor201().withLocation(reply.result()))));
                  }
                  else {
                    if (PgExceptionUtil.isUniqueViolation(reply.cause())) {
                      asyncResultHandler.handle(
                          io.vertx.core.Future.succeededFuture(
                            PostInstanceStorageInstancesResponse
                              .respond400WithTextPlain(PgExceptionUtil.badRequestMessage(reply.cause()))));
                    } else {
                      asyncResultHandler.handle(
                        io.vertx.core.Future.succeededFuture(
                          PostInstanceStorageInstancesResponse
                            .respond400WithTextPlain(reply.cause().getMessage())));
                    }
                  }
                } catch (Exception e) {
                  log.error(e.getMessage(), e);
                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(
                      PostInstanceStorageInstancesResponse
                        .respond500WithTextPlain(e.getMessage())));
                }
              });
            return null;
          }).otherwise(error -> {
            log.error(error.getMessage(), error);
            asyncResultHandler.handle(
              io.vertx.core.Future.succeededFuture(
                PostInstanceStorageInstancesResponse
                  .respond500WithTextPlain(error.getMessage())));
            return null;
          });
        } catch (Exception e) {
          log.error(e.getMessage(), e);
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            PostInstanceStorageInstancesResponse
              .respond500WithTextPlain(e.getMessage())));
        }
      });
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        PostInstanceStorageInstancesResponse
          .respond500WithTextPlain(e.getMessage())));
    }
  }

  @Override
  public void deleteInstanceStorageInstances(
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    RoutingContext routingContext, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    vertxContext.runOnContext(v -> {
      try {
        PostgresClient postgresClient = PostgresClient.getInstance(
          vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

        postgresClient.execute(String.format("DELETE FROM "
              + tenantId + "_" + MODULE + "." + INSTANCE_SOURCE_MARC_TABLE ),
            reply1 -> {
              if (! reply1.succeeded()) {
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    DeleteInstanceStorageInstancesResponse
                    .respond500WithTextPlain(reply1.cause().getMessage())));
                return;
              }
              postgresClient.execute(String.format("DELETE FROM "
               + tenantId + "_" + MODULE + "." + INSTANCE_RELATIONSHIP_TABLE),
              reply2 -> {
                if (! reply2.succeeded()) {
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                      DeleteInstanceStorageInstancesResponse
                      .respond500WithTextPlain(reply1.cause().getMessage())));
                  return;
                }
                postgresClient.execute("DELETE FROM "
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
                log.error(e.getMessage(), e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  GetInstanceStorageInstancesByInstanceIdResponse.
                    respond500WithTextPlain(e.getMessage())));
              }
            });
        } catch (Exception e) {
          log.error(e.getMessage(), e);
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetInstanceStorageInstancesByInstanceIdResponse.
              respond500WithTextPlain(e.getMessage())));
        }
      });
    } catch (Exception e) {
      log.error(e.getMessage(), e);
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

    PgUtil.getById(INSTANCE_TABLE, Instance.class, instanceId, okapiHeaders, vertxContext,
        GetInstanceStorageInstancesByInstanceIdResponse.class, response -> {
          if (response.succeeded()) {
            if (response.result().getStatus() == 404) {
              asyncResultHandler.handle(Future.succeededFuture(
                  PutInstanceStorageInstancesByInstanceIdResponse
                  .respond404WithTextPlain(response.result().getEntity())));
            } else if (response.result().getStatus() == 500) {
              asyncResultHandler.handle(Future.succeededFuture(
                  PutInstanceStorageInstancesByInstanceIdResponse
                  .respond500WithTextPlain(response.result().getEntity())));
            } else {
              final Instance existingInstance = (Instance) response.result().getEntity();
              if (Objects.equals(entity.getHrid(), existingInstance.getHrid())) {
                PgUtil.put(INSTANCE_TABLE, entity, instanceId, okapiHeaders, vertxContext,
                    PutInstanceStorageInstancesByInstanceIdResponse.class, asyncResultHandler);
              } else {
                asyncResultHandler.handle(Future.succeededFuture(
                    PutInstanceStorageInstancesByInstanceIdResponse
                    .respond400WithTextPlain(
                        "The hrid field cannot be changed: new="
                            + entity.getHrid()
                            + ", old="
                            + existingInstance.getHrid())));
              }
            }
          } else {
            asyncResultHandler.handle(Future.succeededFuture(
                PutInstanceStorageInstancesByInstanceIdResponse
                .respond500WithTextPlain(response.cause().getMessage())));
          }
        });
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

    PgUtil.deleteById(INSTANCE_SOURCE_MARC_TABLE, instanceId, okapiHeaders, vertxContext,
        DeleteInstanceStorageInstancesSourceRecordByInstanceIdResponse.class, asyncResultHandler);
  }

  @Override
  public void getInstanceStorageInstancesSourceRecordMarcJsonByInstanceId(
      String instanceId, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    PgUtil.getById(INSTANCE_SOURCE_MARC_TABLE, MarcJson.class, instanceId,
      okapiHeaders, vertxContext,
      GetInstanceStorageInstancesSourceRecordMarcJsonByInstanceIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteInstanceStorageInstancesSourceRecordMarcJsonByInstanceId(
      @NotNull String instanceId,
      @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    PgUtil.deleteById(INSTANCE_SOURCE_MARC_TABLE, instanceId, okapiHeaders, vertxContext,
        DeleteInstanceStorageInstancesSourceRecordMarcJsonByInstanceIdResponse.class, asyncResultHandler);
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
      if (PgExceptionUtil.isForeignKeyViolation(reply.cause())
        && reply.cause().getMessage().contains(INSTANCE_SOURCE_MARC_TABLE)) {
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

          CQLWrapper cql = createCQLWrapper(query, limit, offset, INSTANCE_RELATIONSHIP_TABLE);

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
                log.error(e.getMessage(), e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  GetInstanceStorageInstanceRelationshipsResponse.
                    respond500WithTextPlain(e.getMessage())));
              }
            });
        } catch (Exception e) {
          log.error(e.getMessage(), e);
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetInstanceStorageInstanceRelationshipsResponse.
              respond500WithTextPlain(e.getMessage())));
        }
      });
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        GetInstanceStorageInstanceRelationshipsResponse.
          respond500WithTextPlain(e.getMessage())));
    }
  }

  @Override
  public void postInstanceStorageInstanceRelationships(String lang, InstanceRelationship entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(INSTANCE_RELATIONSHIP_TABLE, entity, okapiHeaders, vertxContext,
        PostInstanceStorageInstanceRelationshipsResponse.class, asyncResultHandler);
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
              if (reply.result().rowCount() == 0) {
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

  static class PreparedCQL {
    private final String tableName;
    private final CQLWrapper cqlWrapper;

    public PreparedCQL(String tableName, String query, int limit, int offset)
        throws FieldException {
      this.tableName = tableName;
      this.cqlWrapper = createCQLWrapper(query, limit, offset, tableName);
    }

    public String getTableName() {
      return tableName;
    }

    public CQLWrapper getCqlWrapper() {
      return cqlWrapper;
    }
  }

}
