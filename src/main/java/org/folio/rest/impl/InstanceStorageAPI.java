package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;

import static org.folio.persist.InstanceMarcRepository.INSTANCE_SOURCE_MARC_TABLE;
import static org.folio.persist.InstanceRelationshipRepository.INSTANCE_RELATIONSHIP_TABLE;
import static org.folio.persist.InstanceRepository.INSTANCE_TABLE;
import static org.folio.rest.support.EndpointFailureHandler.handleFailure;

import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.persist.entity.GetInstanceStorageInstancesResponseInternal;
import org.folio.persist.entity.InstanceInternal;
import org.folio.persist.entity.InstancesInternal;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.InstanceRelationship;
import org.folio.rest.jaxrs.model.InstanceRelationships;
import org.folio.rest.jaxrs.model.MarcJson;
import org.folio.rest.jaxrs.resource.InstanceStorage;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.support.EndpointFailureHandler;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.services.instance.InstanceService;

public class InstanceStorageAPI implements InstanceStorage {
  private static final Logger log = LogManager.getLogger();
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

  @Validate
  @Override
  public void getInstanceStorageInstances(
    int offset,
    int limit,
    String query,
    String lang,
    RoutingContext routingContext, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    if (PgUtil.checkOptimizedCQL(query, "title") != null) { // Until RMB-573 is fixed
      try {
        PreparedCQL preparedCql = handleCQL(query, limit, offset);
        PgUtil.getWithOptimizedSql(preparedCql.getTableName(), InstanceInternal.class, InstancesInternal.class,
          "title", query, offset, limit,
          okapiHeaders, vertxContext, GetInstanceStorageInstancesResponseInternal.class, asyncResultHandler);
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
          GetInstanceStorageInstancesResponse.
            respond500WithTextPlain(e.getMessage())));
      }
      return;
    }

    PgUtil.streamGet(INSTANCE_TABLE, InstanceInternal.class, query, offset, limit, null,
      "instances", routingContext, okapiHeaders, vertxContext);
  }

  @Validate
  @Override
  public void postInstanceStorageInstances(
    String lang,
    Instance entity,
    RoutingContext routingContext, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new InstanceService(vertxContext, okapiHeaders)
      .createInstance(entity)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void deleteInstanceStorageInstances(
    String query,
    RoutingContext routingContext, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new InstanceService(vertxContext, okapiHeaders).deleteInstances(query)
    .otherwise(EndpointFailureHandler::failureResponse)
    .onComplete(asyncResultHandler);
  }

  @Validate
  @Override
  public void getInstanceStorageInstancesByInstanceId(
    String instanceId,
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new InstanceService(vertxContext, okapiHeaders).getInstance(instanceId)
    .otherwise(EndpointFailureHandler::failureResponse)
    .onComplete(asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteInstanceStorageInstancesByInstanceId(
    String instanceId,
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new InstanceService(vertxContext, okapiHeaders)
      .deleteInstance(instanceId)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void putInstanceStorageInstancesByInstanceId(
    String instanceId,
    String lang,
    Instance entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new InstanceService(vertxContext, okapiHeaders)
      .updateInstance(instanceId, entity)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void deleteInstanceStorageInstancesSourceRecordByInstanceId(
      String instanceId,
      String lang,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    PgUtil.deleteById(INSTANCE_SOURCE_MARC_TABLE, instanceId, okapiHeaders, vertxContext,
        DeleteInstanceStorageInstancesSourceRecordByInstanceIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void getInstanceStorageInstancesSourceRecordMarcJsonByInstanceId(
      String instanceId, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    PgUtil.getById(INSTANCE_SOURCE_MARC_TABLE, MarcJson.class, instanceId,
      okapiHeaders, vertxContext,
      GetInstanceStorageInstancesSourceRecordMarcJsonByInstanceIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteInstanceStorageInstancesSourceRecordMarcJsonByInstanceId(
      String instanceId,
      String lang,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    PgUtil.deleteById(INSTANCE_SOURCE_MARC_TABLE, instanceId, okapiHeaders, vertxContext,
        DeleteInstanceStorageInstancesSourceRecordMarcJsonByInstanceIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void putInstanceStorageInstancesSourceRecordMarcJsonByInstanceId(
      String instanceId,
      String lang,
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
  @Validate
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
  @Validate
  @Override
  public void putInstanceStorageInstancesSourceRecordModsByInstanceId(
      String instanceId, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    asyncResultHandler.handle(Future.succeededFuture(
      PutInstanceStorageInstancesSourceRecordModsByInstanceIdResponse
        .respond500WithTextPlain("Not implemented yet.")));
  }

  @Validate
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

  @Validate
  @Override
  public void postInstanceStorageInstanceRelationships(String lang, InstanceRelationship entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(INSTANCE_RELATIONSHIP_TABLE, entity, okapiHeaders, vertxContext,
        PostInstanceStorageInstanceRelationshipsResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void getInstanceStorageInstanceRelationshipsByRelationshipId(String relationshipId, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Validate
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

  @Validate
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
