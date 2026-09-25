package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.persist.InstanceMarcRepository.INSTANCE_SOURCE_MARC_TABLE;
import static org.folio.persist.InstanceRelationshipRepository.INSTANCE_RELATIONSHIP_TABLE;
import static org.folio.persist.InstanceRepository.INSTANCE_TABLE;
import static org.folio.rest.support.EndpointFailureHandler.handleFailure;
import static org.folio.rest.tools.messages.Messages.DEFAULT_LANGUAGE;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.InstancePatchRequest;
import org.folio.rest.jaxrs.model.InstanceRelationship;
import org.folio.rest.jaxrs.model.InstanceRelationships;
import org.folio.rest.jaxrs.model.MarcJson;
import org.folio.rest.jaxrs.model.RetrieveEntitiesRequest;
import org.folio.rest.jaxrs.resource.InstanceStorage;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.persist.interfaces.Results;
import org.folio.rest.support.EndpointFailureHandler;
import org.folio.rest.support.PostgresClientFactory;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.services.instance.InstanceService;

public class InstanceStorageApi implements InstanceStorage {
  private static final Logger log = LogManager.getLogger();
  private final Messages messages = Messages.getInstance();

  @Validate
  @Override
  public void getInstanceStorageInstanceRelationships(String totalRecords, int offset, int limit, String query,
                                                      Map<String, String> okapiHeaders,
                                                      Handler<AsyncResult<Response>> handler,
                                                      Context vertxContext) {

    try {
      vertxContext.runOnContext(v -> {
        try {
          executeInstanceRelationshipsQuery(offset, limit, query, okapiHeaders, handler, vertxContext);
        } catch (Exception e) {
          log.error(e.getMessage(), e);
          handler.handle(succeededFuture(
            GetInstanceStorageInstanceRelationshipsResponse.respond500WithTextPlain(e.getMessage())));
        }
      });
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      handler.handle(succeededFuture(
        GetInstanceStorageInstanceRelationshipsResponse.respond500WithTextPlain(e.getMessage())));
    }
  }

  @Validate
  @Override
  public void postInstanceStorageInstanceRelationships(InstanceRelationship entity,
                                                       Map<String, String> okapiHeaders,
                                                       Handler<AsyncResult<Response>> handler,
                                                       Context vertxContext) {
    PgUtil.post(INSTANCE_RELATIONSHIP_TABLE, entity, okapiHeaders, vertxContext,
      PostInstanceStorageInstanceRelationshipsResponse.class, handler);
  }

  @Validate
  @Override
  public void getInstanceStorageInstanceRelationshipsByRelationshipId(String relationshipId,
                                                                      Map<String, String> okapiHeaders,
                                                                      Handler<AsyncResult<Response>> handler,
                                                                      Context vertxContext) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Validate
  @Override
  public void deleteInstanceStorageInstanceRelationshipsByRelationshipId(String relationshipId,
                                                                         Map<String, String> okapiHeaders,
                                                                         Handler<AsyncResult<Response>> handler,
                                                                         Context vertxContext) {
    PostgresClientFactory.getInstance(vertxContext, okapiHeaders)
      .delete(INSTANCE_RELATIONSHIP_TABLE, relationshipId, reply -> {
        if (!reply.succeeded()) {
          handler.handle(succeededFuture(
            DeleteInstanceStorageInstanceRelationshipsByRelationshipIdResponse
              .respond500WithTextPlain(reply.cause().getMessage())));
          return;
        }
        handler.handle(succeededFuture(
          DeleteInstanceStorageInstanceRelationshipsByRelationshipIdResponse.respond204()));
      });
  }

  @Validate
  @Override
  public void putInstanceStorageInstanceRelationshipsByRelationshipId(String relationshipId,
                                                                      InstanceRelationship entity,
                                                                      Map<String, String> okapiHeaders,
                                                                      Handler<AsyncResult<Response>> handler,
                                                                      Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        if (entity.getId() == null) {
          entity.setId(relationshipId);
        }
        PostgresClientFactory.getInstance(vertxContext, okapiHeaders).update(
          INSTANCE_RELATIONSHIP_TABLE, entity, relationshipId,
          reply -> handleUpdateRelationshipResult(reply, handler));
      } catch (Exception e) {
        handler.handle(succeededFuture(PutInstanceStorageInstanceRelationshipsByRelationshipIdResponse
          .respond500WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
      }
    });
  }

  @Validate
  @Override
  public void getInstanceStorageInstances(boolean includeShadowCopies, String totalRecords, int offset, int limit,
                                          String query, RoutingContext routingContext,
                                          Map<String, String> okapiHeaders,
                                          Handler<AsyncResult<Response>> handler,
                                          Context vertxContext) {
    var modifiedQuery = excludeShadowCopiesIfNeeded(query, includeShadowCopies);
    fetchInstances(modifiedQuery, limit, offset, routingContext, okapiHeaders, vertxContext);
  }

  @Validate
  @Override
  public void postInstanceStorageInstances(Instance entity, RoutingContext routingContext,
                                           Map<String, String> okapiHeaders,
                                           Handler<AsyncResult<Response>> handler, Context vertxContext) {
    new InstanceService(vertxContext, okapiHeaders)
      .createInstance(entity)
      .onSuccess(response -> handler.handle(succeededFuture(response)))
      .onFailure(handleFailure(handler));
  }

  @Validate
  @Override
  public void deleteInstanceStorageInstances(String query, RoutingContext routingContext,
                                             Map<String, String> okapiHeaders,
                                             Handler<AsyncResult<Response>> handler, Context vertxContext) {
    new InstanceService(vertxContext, okapiHeaders).deleteInstances(query)
      .otherwise(EndpointFailureHandler::failureResponse)
      .onComplete(handler);
  }

  @Validate
  @Override
  public void getInstanceStorageInstancesByInstanceId(String instanceId, Map<String, String> okapiHeaders,
                                                      Handler<AsyncResult<Response>> handler,
                                                      Context vertxContext) {
    new InstanceService(vertxContext, okapiHeaders).getInstance(instanceId)
      .otherwise(EndpointFailureHandler::failureResponse)
      .onComplete(handler);
  }

  @Override
  public void patchInstanceStorageInstancesByInstanceId(String instanceId, InstancePatchRequest request,
                                                        Map<String, String> okapiHeaders,
                                                        Handler<AsyncResult<Response>> handler,
                                                        Context vertxContext) {
    new InstanceService(vertxContext, okapiHeaders)
      .patchInstance(instanceId, request)
      .onSuccess(response -> handler.handle(succeededFuture(response)))
      .onFailure(handleFailure(handler));
  }

  @Validate
  @Override
  public void deleteInstanceStorageInstancesByInstanceId(String instanceId, Map<String, String> okapiHeaders,
                                                         Handler<AsyncResult<Response>> handler,
                                                         Context vertxContext) {
    new InstanceService(vertxContext, okapiHeaders)
      .deleteInstance(instanceId)
      .onSuccess(response -> handler.handle(succeededFuture(response)))
      .onFailure(handleFailure(handler));
  }

  @Validate
  @Override
  public void putInstanceStorageInstancesByInstanceId(String instanceId, Instance entity,
                                                      Map<String, String> okapiHeaders,
                                                      Handler<AsyncResult<Response>> handler,
                                                      Context vertxContext) {
    new InstanceService(vertxContext, okapiHeaders)
      .updateInstance(instanceId, entity)
      .onSuccess(response -> handler.handle(succeededFuture(response)))
      .onFailure(handleFailure(handler));
  }

  @Validate
  @Override
  public void getInstanceStorageInstancesSummaryByInstanceId(String instanceId,
                                                             Map<String, String> okapiHeaders,
                                                             Handler<AsyncResult<Response>> handler,
                                                             Context vertxContext) {
    new InstanceService(vertxContext, okapiHeaders)
      .getInstanceSummary(instanceId)
      .otherwise(EndpointFailureHandler::failureResponse)
      .onComplete(handler);
  }

  @Validate
  @Override
  public void deleteInstanceStorageInstancesSourceRecordByInstanceId(String instanceId,
                                                                     Map<String, String> okapiHeaders,
                                                                     Handler<AsyncResult<Response>> handler,
                                                                     Context vertxContext) {
    PgUtil.deleteById(INSTANCE_SOURCE_MARC_TABLE, instanceId, okapiHeaders, vertxContext,
      DeleteInstanceStorageInstancesSourceRecordByInstanceIdResponse.class, handler);
  }

  @Validate
  @Override
  public void getInstanceStorageInstancesSourceRecordMarcJsonByInstanceId(String instanceId,
                                                                          Map<String, String> okapiHeaders,
                                                                          Handler<AsyncResult<Response>> handler,
                                                                          Context vertxContext) {
    PgUtil.getById(INSTANCE_SOURCE_MARC_TABLE, MarcJson.class, instanceId,
      okapiHeaders, vertxContext,
      GetInstanceStorageInstancesSourceRecordMarcJsonByInstanceIdResponse.class, handler);
  }

  @Validate
  @Override
  public void deleteInstanceStorageInstancesSourceRecordMarcJsonByInstanceId(String instanceId,
                                                                             Map<String, String> okapiHeaders,
                                                                             Handler<AsyncResult<Response>> handler,
                                                                             Context vertxContext) {
    PgUtil.deleteById(INSTANCE_SOURCE_MARC_TABLE, instanceId, okapiHeaders, vertxContext,
      DeleteInstanceStorageInstancesSourceRecordMarcJsonByInstanceIdResponse.class, handler);
  }

  @Validate
  @Override
  public void putInstanceStorageInstancesSourceRecordMarcJsonByInstanceId(String instanceId,
                                                                          MarcJson entity,
                                                                          Map<String, String> okapiHeaders,
                                                                          Handler<AsyncResult<Response>> handler,
                                                                          Context vertxContext) {
    PostgresClientFactory.getInstance(vertxContext, okapiHeaders)
      .upsert(INSTANCE_SOURCE_MARC_TABLE, instanceId, entity, reply -> {
        if (reply.succeeded()) {
          handler.handle(succeededFuture(
            PutInstanceStorageInstancesSourceRecordMarcJsonByInstanceIdResponse.respond204()));
          return;
        }
        if (PgExceptionUtil.isForeignKeyViolation(reply.cause())
            && reply.cause().getMessage().contains(INSTANCE_SOURCE_MARC_TABLE)) {
          handler.handle(succeededFuture(
            PutInstanceStorageInstancesSourceRecordMarcJsonByInstanceIdResponse
              .respond404WithTextPlain(reply.cause().getMessage())));
          return;
        }
        handler.handle(succeededFuture(
          PutInstanceStorageInstancesSourceRecordMarcJsonByInstanceIdResponse
            .respond500WithTextPlain(reply.cause().getMessage())));
      });
  }

  /**
   * Example stub showing how other formats might get implemented.
   */
  @Validate
  @Override
  public void getInstanceStorageInstancesSourceRecordModsByInstanceId(String instanceId,
                                                                      Map<String, String> okapiHeaders,
                                                                      Handler<AsyncResult<Response>> handler,
                                                                      Context vertxContext) {
    handler.handle(succeededFuture(GetInstanceStorageInstancesSourceRecordModsByInstanceIdResponse
      .respond500WithTextPlain("Not implemented yet.")));
  }

  /**
   * Example stub showing how other formats might get implemented.
   */
  @Validate
  @Override
  public void putInstanceStorageInstancesSourceRecordModsByInstanceId(String instanceId,
                                                                      Map<String, String> okapiHeaders,
                                                                      Handler<AsyncResult<Response>> handler,
                                                                      Context vertxContext) {

    handler.handle(succeededFuture(
      PutInstanceStorageInstancesSourceRecordModsByInstanceIdResponse
        .respond500WithTextPlain("Not implemented yet.")));
  }

  @Validate
  @Override
  public void postInstanceStorageInstancesRetrieve(RetrieveEntitiesRequest entity,
                                                   RoutingContext routingContext,
                                                   Map<String, String> okapiHeaders,
                                                   Handler<AsyncResult<Response>> handler,
                                                   Context vertxContext) {
    fetchInstances(entity.getQuery(), entity.getLimit(), entity.getOffset(),
      routingContext, okapiHeaders, vertxContext);
  }

  private void executeInstanceRelationshipsQuery(int offset, int limit, String query,
                                                 Map<String, String> okapiHeaders,
                                                 Handler<AsyncResult<Response>> handler,
                                                 Context vertxContext) throws FieldException {
    String[] fieldList = {"*"};
    CQLWrapper cql = createCqlWrapper(query, limit, offset, INSTANCE_RELATIONSHIP_TABLE);
    log.info("SQL generated from CQL: {}", cql);

    PostgresClientFactory.getInstance(vertxContext, okapiHeaders)
      .get(INSTANCE_RELATIONSHIP_TABLE, InstanceRelationship.class, fieldList, cql,
        true, false, reply -> handleInstanceRelationshipsQueryResult(reply, handler));
  }

  private void handleInstanceRelationshipsQueryResult(AsyncResult<Results<InstanceRelationship>> reply,
                                                      Handler<AsyncResult<Response>> handler) {
    try {
      if (reply.succeeded()) {
        List<InstanceRelationship> instanceRelationships = reply.result().getResults();
        InstanceRelationships instanceList = new InstanceRelationships();
        instanceList.setInstanceRelationships(instanceRelationships);
        instanceList.setTotalRecords(reply.result().getResultInfo().getTotalRecords());

        handler.handle(succeededFuture(
          GetInstanceStorageInstanceRelationshipsResponse.respond200WithApplicationJson(instanceList)));
      } else {
        handler.handle(succeededFuture(
          GetInstanceStorageInstanceRelationshipsResponse.respond500WithTextPlain(reply.cause().getMessage())));
      }
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      handler.handle(succeededFuture(
        GetInstanceStorageInstanceRelationshipsResponse.respond500WithTextPlain(e.getMessage())));
    }
  }

  private void handleUpdateRelationshipResult(AsyncResult<RowSet<Row>> reply,
                                              Handler<AsyncResult<Response>> handler) {
    try {
      if (reply.succeeded()) {
        handleSuccessfulUpdate(reply.result(), handler);
      } else {
        handleFailedUpdate(reply.cause(), handler);
      }
    } catch (Exception e) {
      handler.handle(
        succeededFuture(PutInstanceStorageInstanceRelationshipsByRelationshipIdResponse
          .respond500WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
    }
  }

  private void handleSuccessfulUpdate(RowSet<Row> result, Handler<AsyncResult<Response>> handler) {
    if (result.rowCount() == 0) {
      handler.handle(
        succeededFuture(PutInstanceStorageInstanceRelationshipsByRelationshipIdResponse
          .respond404WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.NoRecordsUpdated))));
    } else {
      handler.handle(
        succeededFuture(PutInstanceStorageInstanceRelationshipsByRelationshipIdResponse
          .respond204()));
    }
  }

  private void handleFailedUpdate(Throwable cause, Handler<AsyncResult<Response>> handler) {
    String msg = PgExceptionUtil.badRequestMessage(cause);
    if (msg == null) {
      handler.handle(
        succeededFuture(PutInstanceStorageInstanceRelationshipsByRelationshipIdResponse
          .respond500WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE,
            MessageConsts.InternalServerError))));
      return;
    }
    log.info(msg);
    handler.handle(
      succeededFuture(PutInstanceStorageInstanceRelationshipsByRelationshipIdResponse
        .respond400WithTextPlain(msg)));
  }

  private void fetchInstances(String query, int limit, int offset,
                              RoutingContext routingContext,
                              Map<String, String> okapiHeaders,
                              Context vertxContext) {
    PgUtil.streamGet(INSTANCE_TABLE, Instance.class, query, offset, limit, null,
      "instances", routingContext, okapiHeaders, vertxContext);
  }

  /**
   * Excludes consortium shadow copies (source CONSORTIUM-MARC or CONSORTIUM-FOLIO) from the query
   * when includeShadowCopies is false, so a tenant's own search does not need to know the shadow
   * copy source-value convention.
   */
  private static String excludeShadowCopiesIfNeeded(String query, boolean includeShadowCopies) {
    if (includeShadowCopies) {
      return query;
    }
    String baseQuery = StringUtils.isBlank(query) ? "cql.allRecords=1" : query;
    return "(" + baseQuery + ") and source <> \"CONSORTIUM-MARC\" and source <> \"CONSORTIUM-FOLIO\"";
  }

  private static CQLWrapper createCqlWrapper(String query, int limit, int offset, String tableName)
    throws FieldException {
    return StorageHelper.getCql(query, limit, offset, tableName);
  }
}
