package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.persist.InstanceMarcRepository.INSTANCE_SOURCE_MARC_TABLE;
import static org.folio.persist.InstanceRelationshipRepository.INSTANCE_RELATIONSHIP_TABLE;
import static org.folio.persist.InstanceRepository.INSTANCE_TABLE;
import static org.folio.rest.support.EndpointFailureHandler.handleFailure;
import static org.folio.rest.tools.messages.Messages.DEFAULT_LANGUAGE;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.InstanceRelationship;
import org.folio.rest.jaxrs.model.InstanceRelationships;
import org.folio.rest.jaxrs.model.Instances;
import org.folio.rest.jaxrs.model.MarcJson;
import org.folio.rest.jaxrs.model.RetrieveDto;
import org.folio.rest.jaxrs.resource.InstanceStorage;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.support.EndpointFailureHandler;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.services.instance.InstanceService;

public class InstanceStorageApi implements InstanceStorage {
  private static final Logger log = LogManager.getLogger();
  private static final String TITLE = "title";
  private final Messages messages = Messages.getInstance();

  @Validate
  @Override
  public void getInstanceStorageInstanceRelationships(String totalRecords, int offset, int limit, String query,
                                                      Map<String, String> okapiHeaders,
                                                      Handler<AsyncResult<Response>> asyncResultHandler,
                                                      Context vertxContext) {
    PostgresClient postgresClient =
      PostgresClient.getInstance(vertxContext.owner(), TenantTool.tenantId(okapiHeaders));

    try {
      vertxContext.runOnContext(v -> {
        try {

          String[] fieldList = {"*"};

          CQLWrapper cql = createCqlWrapper(query, limit, offset, INSTANCE_RELATIONSHIP_TABLE);

          log.info("SQL generated from CQL: {}", cql);

          postgresClient.get(INSTANCE_RELATIONSHIP_TABLE, InstanceRelationship.class, fieldList, cql,
            true, false, reply -> {
              try {
                if (reply.succeeded()) {
                  List<InstanceRelationship> instanceRelationships = reply.result().getResults();

                  InstanceRelationships instanceList = new InstanceRelationships();
                  instanceList.setInstanceRelationships(instanceRelationships);
                  instanceList.setTotalRecords(reply.result().getResultInfo().getTotalRecords());

                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    GetInstanceStorageInstanceRelationshipsResponse.respond200WithApplicationJson(instanceList)));
                } else {
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    GetInstanceStorageInstanceRelationshipsResponse.respond500WithTextPlain(
                      reply.cause().getMessage())));
                }
              } catch (Exception e) {
                log.error(e.getMessage(), e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  GetInstanceStorageInstanceRelationshipsResponse.respond500WithTextPlain(e.getMessage())));
              }
            });
        } catch (Exception e) {
          log.error(e.getMessage(), e);
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetInstanceStorageInstanceRelationshipsResponse.respond500WithTextPlain(e.getMessage())));
        }
      });
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        GetInstanceStorageInstanceRelationshipsResponse.respond500WithTextPlain(e.getMessage())));
    }
  }

  @Validate
  @Override
  public void postInstanceStorageInstanceRelationships(InstanceRelationship entity,
                                                       Map<String, String> okapiHeaders,
                                                       Handler<AsyncResult<Response>> asyncResultHandler,
                                                       Context vertxContext) {
    PgUtil.post(INSTANCE_RELATIONSHIP_TABLE, entity, okapiHeaders, vertxContext,
      PostInstanceStorageInstanceRelationshipsResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void getInstanceStorageInstanceRelationshipsByRelationshipId(String relationshipId,
                                                                      Map<String, String> okapiHeaders,
                                                                      Handler<AsyncResult<Response>> asyncResultHandler,
                                                                      Context vertxContext) {
    throw new UnsupportedOperationException(
      "Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Validate
  @Override
  public void deleteInstanceStorageInstanceRelationshipsByRelationshipId(
    String relationshipId,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {
    PostgresClient postgresClient =
      PostgresClient.getInstance(vertxContext.owner(), TenantTool.tenantId(okapiHeaders));

    postgresClient.delete(INSTANCE_RELATIONSHIP_TABLE, relationshipId, reply -> {
      if (!reply.succeeded()) {
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
  public void putInstanceStorageInstanceRelationshipsByRelationshipId(String relationshipId,
                                                                      InstanceRelationship entity,
                                                                      Map<String, String> okapiHeaders,
                                                                      Handler<AsyncResult<Response>> asyncResultHandler,
                                                                      Context vertxContext) {
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
                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(PutInstanceStorageInstanceRelationshipsByRelationshipIdResponse
                      .respond404WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.NoRecordsUpdated))));
                } else {
                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(PutInstanceStorageInstanceRelationshipsByRelationshipIdResponse
                      .respond204()));
                }
              } else {
                String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                if (msg == null) {
                  asyncResultHandler.handle(
                    Future.succeededFuture(PutInstanceStorageInstanceRelationshipsByRelationshipIdResponse
                      .respond500WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE,
                        MessageConsts.InternalServerError))));
                }
                log.info(msg);
                asyncResultHandler.handle(
                  Future.succeededFuture(PutInstanceStorageInstanceRelationshipsByRelationshipIdResponse
                    .respond400WithTextPlain(msg)));
              }
            } catch (Exception e) {
              asyncResultHandler.handle(
                Future.succeededFuture(PutInstanceStorageInstanceRelationshipsByRelationshipIdResponse
                  .respond500WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
            }

          });
      } catch (Exception e) {
        asyncResultHandler.handle(Future.succeededFuture(PutInstanceStorageInstanceRelationshipsByRelationshipIdResponse
          .respond500WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
      }
    });
  }

  @Validate
  @Override
  public void getInstanceStorageInstances(String totalRecords, int offset, int limit, String query,
                                          RoutingContext routingContext, Map<String, String> okapiHeaders,
                                          Handler<AsyncResult<Response>> asyncResultHandler,
                                          Context vertxContext) {

    fetchInstances(query, limit, offset, routingContext, okapiHeaders, asyncResultHandler, vertxContext);
  }

  @Validate
  @Override
  public void postInstanceStorageInstances(
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

    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.deleteById(INSTANCE_SOURCE_MARC_TABLE, instanceId, okapiHeaders, vertxContext,
      DeleteInstanceStorageInstancesSourceRecordByInstanceIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void getInstanceStorageInstancesSourceRecordMarcJsonByInstanceId(
    String instanceId, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    PgUtil.getById(INSTANCE_SOURCE_MARC_TABLE, MarcJson.class, instanceId,
      okapiHeaders, vertxContext,
      GetInstanceStorageInstancesSourceRecordMarcJsonByInstanceIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteInstanceStorageInstancesSourceRecordMarcJsonByInstanceId(
    String instanceId,

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

    MarcJson entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

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
    String instanceId, Map<String, String> okapiHeaders,
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
    String instanceId, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    asyncResultHandler.handle(Future.succeededFuture(
      PutInstanceStorageInstancesSourceRecordModsByInstanceIdResponse
        .respond500WithTextPlain("Not implemented yet.")));
  }

  @Validate
  @Override
  public void postInstanceStorageInstancesRetrieve(RetrieveDto entity,
                                                   RoutingContext routingContext,
                                                   Map<String, String> okapiHeaders,
                                                   Handler<AsyncResult<Response>> asyncResultHandler,
                                                   Context vertxContext) {
    fetchInstances(entity.getQuery(), entity.getLimit(), entity.getOffset(),
      routingContext, okapiHeaders, asyncResultHandler, vertxContext);
  }

  private void fetchInstances(String query, int limit, int offset,
                              RoutingContext routingContext,
                              Map<String, String> okapiHeaders,
                              Handler<AsyncResult<Response>> asyncResultHandler,
                              Context vertxContext) {
    if (PgUtil.checkOptimizedCQL(query, TITLE) != null) {
      try {
        PgUtil.getWithOptimizedSql(INSTANCE_TABLE, Instance.class, Instances.class,
          TITLE, query, offset, limit,
          okapiHeaders, vertxContext, GetInstanceStorageInstancesResponse.class, asyncResultHandler);
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
          GetInstanceStorageInstancesResponse.respond500WithTextPlain(e.getMessage())));
      }
      return;
    }
    PgUtil.streamGet(INSTANCE_TABLE, Instance.class, query, offset, limit, null,
      "instances", routingContext, okapiHeaders, vertxContext);

  }

  private static CQLWrapper createCqlWrapper(String query, int limit, int offset, String tableName)
    throws FieldException {
    return StorageHelper.getCql(query, limit, offset, tableName);
  }

}
