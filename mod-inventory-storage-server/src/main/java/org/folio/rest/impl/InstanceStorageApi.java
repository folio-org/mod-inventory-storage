package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.persist.InstanceMarcRepository.INSTANCE_SOURCE_MARC_TABLE;
import static org.folio.persist.InstanceRelationshipRepository.INSTANCE_RELATIONSHIP_TABLE;
import static org.folio.rest.support.ResponseHandlerUtil.firstErrorMessage;
import static org.folio.rest.support.ResponseHandlerUtil.handleFailure;
import static org.folio.rest.support.ResponseHandlerUtil.handleFailureWith;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import java.util.Map;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.InstancePatchRequest;
import org.folio.rest.jaxrs.model.InstanceRelationship;
import org.folio.rest.jaxrs.model.InstanceRelationships;
import org.folio.rest.jaxrs.model.MarcJson;
import org.folio.rest.jaxrs.model.RetrieveEntitiesRequest;
import org.folio.rest.jaxrs.resource.InstanceStorage;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.support.ResponseHandlerUtil;
import org.folio.rest.support.StreamingJsonResponse;
import org.folio.services.instance.InstanceService;
import org.folio.services.referencedata.ReferenceDataService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("java:S6813")
public class InstanceStorageApi implements InstanceStorage {

  @Autowired
  private InstanceService instanceService;
  @Autowired
  private ReferenceDataService referenceDataService;

  public InstanceStorageApi() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  InstanceStorageApi(InstanceService instanceService, ReferenceDataService referenceDataService) {
    this.instanceService = instanceService;
    this.referenceDataService = referenceDataService;
  }

  @Validate
  @Override
  public void getInstanceStorageInstanceRelationships(String totalRecords, int offset, int limit, String query,
                                                      Map<String, String> headers,
                                                      Handler<AsyncResult<Response>> handler,
                                                      Context vertxContext) {

    referenceDataService.query(INSTANCE_RELATIONSHIP_TABLE, InstanceRelationship.class, query, offset, limit,
        totalRecords, headers)
      .map(results -> new InstanceRelationships()
        .withInstanceRelationships(results.getResults())
        .withTotalRecords(results.getResultInfo().getTotalRecords()))
      .map(GetInstanceStorageInstanceRelationshipsResponse::respond200WithApplicationJson)
      .onSuccess(response -> handler.handle(succeededFuture(response)))
      .onFailure(handleFailure(handler));
  }

  @Validate
  @Override
  public void postInstanceStorageInstanceRelationships(InstanceRelationship entity,
                                                       Map<String, String> headers,
                                                       Handler<AsyncResult<Response>> handler,
                                                       Context vertxContext) {
    referenceDataService.create(INSTANCE_RELATIONSHIP_TABLE, InstanceRelationship.class, entity, headers)
      .map(created -> PostInstanceStorageInstanceRelationshipsResponse.respond201WithApplicationJson(created,
        PostInstanceStorageInstanceRelationshipsResponse.headersFor201().withLocation(created.getId())))
      .onSuccess(response -> handler.handle(succeededFuture(response)))
      .onFailure(handleFailure(handler, errors ->
        PostInstanceStorageInstanceRelationshipsResponse.respond400WithTextPlain(firstErrorMessage(errors))));
  }

  @Validate
  @Override
  public void getInstanceStorageInstanceRelationshipsByRelationshipId(String relationshipId,
                                                                      Map<String, String> headers,
                                                                      Handler<AsyncResult<Response>> handler,
                                                                      Context vertxContext) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Validate
  @Override
  public void deleteInstanceStorageInstanceRelationshipsByRelationshipId(String relationshipId,
                                                                         Map<String, String> headers,
                                                                         Handler<AsyncResult<Response>> handler,
                                                                         Context vertxContext) {
    referenceDataService.deleteIfExists(INSTANCE_RELATIONSHIP_TABLE, InstanceRelationship.class, relationshipId,
        headers)
      .map(notUsed -> DeleteInstanceStorageInstanceRelationshipsByRelationshipIdResponse.respond204())
      .onSuccess(response -> handler.handle(succeededFuture(response)))
      .onFailure(cause -> handler.handle(succeededFuture(
        DeleteInstanceStorageInstanceRelationshipsByRelationshipIdResponse
          .respond500WithTextPlain(cause.getMessage()))));
  }

  @Validate
  @Override
  public void putInstanceStorageInstanceRelationshipsByRelationshipId(String relationshipId,
                                                                      InstanceRelationship entity,
                                                                      Map<String, String> headers,
                                                                      Handler<AsyncResult<Response>> handler,
                                                                      Context vertxContext) {
    if (entity.getId() == null) {
      entity.setId(relationshipId);
    }
    referenceDataService.update(INSTANCE_RELATIONSHIP_TABLE, InstanceRelationship.class, relationshipId, entity,
        headers)
      .map(notUsed -> PutInstanceStorageInstanceRelationshipsByRelationshipIdResponse.respond204())
      .onSuccess(response -> handler.handle(succeededFuture(response)))
      .onFailure(handleFailure(handler, errors ->
        PutInstanceStorageInstanceRelationshipsByRelationshipIdResponse.respond400WithTextPlain(
          firstErrorMessage(errors))));
  }

  @Validate
  @Override
  public void getInstanceStorageInstances(boolean includeShadowCopies, String totalRecords, int offset, int limit,
                                          String query, RoutingContext routingContext,
                                          Map<String, String> headers,
                                          Handler<AsyncResult<Response>> handler,
                                          Context vertxContext) {
    var modifiedQuery = excludeShadowCopiesIfNeeded(query, includeShadowCopies);
    fetchInstances(modifiedQuery, limit, offset, routingContext, headers);
  }

  @Validate
  @Override
  public void postInstanceStorageInstances(Instance entity, RoutingContext routingContext,
                                           Map<String, String> headers,
                                           Handler<AsyncResult<Response>> handler, Context vertxContext) {
    instanceService.createInstance(entity, headers)
      .map(created -> PostInstanceStorageInstancesResponse.respond201WithApplicationJson(created,
        PostInstanceStorageInstancesResponse.headersFor201()))
      .onSuccess(response -> handler.handle(succeededFuture(response)))
      .onFailure(handleFailureWith(handler, ResponseHandlerUtil::handleHridErrorInInstance));
  }

  @Validate
  @Override
  public void deleteInstanceStorageInstances(String query, RoutingContext routingContext,
                                             Map<String, String> headers,
                                             Handler<AsyncResult<Response>> handler, Context vertxContext) {
    instanceService.deleteInstances(query, headers)
      .map(notUsed -> DeleteInstanceStorageInstancesResponse.respond204())
      .onSuccess(response -> handler.handle(succeededFuture(response)))
      .onFailure(handleFailure(handler));
  }

  @Validate
  @Override
  public void getInstanceStorageInstancesByInstanceId(String instanceId, Map<String, String> headers,
                                                      Handler<AsyncResult<Response>> handler,
                                                      Context vertxContext) {
    instanceService.getInstance(instanceId, headers)
      .map(GetInstanceStorageInstancesByInstanceIdResponse::respond200WithApplicationJson)
      .onSuccess(response -> handler.handle(succeededFuture(response)))
      .onFailure(handleFailure(handler));
  }

  @Override
  public void patchInstanceStorageInstancesByInstanceId(String instanceId, InstancePatchRequest request,
                                                        Map<String, String> headers,
                                                        Handler<AsyncResult<Response>> handler,
                                                        Context vertxContext) {
    instanceService.patchInstance(instanceId, request, headers)
      .map(notUsed -> PutInstanceStorageInstancesByInstanceIdResponse.respond204())
      .onSuccess(response -> handler.handle(succeededFuture(response)))
      .onFailure(handleFailure(handler));
  }

  @Validate
  @Override
  public void deleteInstanceStorageInstancesByInstanceId(String instanceId, Map<String, String> headers,
                                                         Handler<AsyncResult<Response>> handler,
                                                         Context vertxContext) {
    instanceService.deleteInstance(instanceId, headers)
      .map(notUsed -> DeleteInstanceStorageInstancesByInstanceIdResponse.respond204())
      .onSuccess(response -> handler.handle(succeededFuture(response)))
      .onFailure(handleFailure(handler, errors ->
        DeleteInstanceStorageInstancesByInstanceIdResponse.respond400WithTextPlain(firstErrorMessage(errors))));
  }

  @Validate
  @Override
  public void putInstanceStorageInstancesByInstanceId(String instanceId, Instance entity,
                                                      Map<String, String> headers,
                                                      Handler<AsyncResult<Response>> handler,
                                                      Context vertxContext) {
    instanceService.updateInstance(instanceId, entity, headers)
      .map(notUsed -> PutInstanceStorageInstancesByInstanceIdResponse.respond204())
      .onSuccess(response -> handler.handle(succeededFuture(response)))
      .onFailure(handleFailure(handler));
  }

  @Validate
  @Override
  public void getInstanceStorageInstancesSummaryByInstanceId(String instanceId,
                                                             Map<String, String> headers,
                                                             Handler<AsyncResult<Response>> handler,
                                                             Context vertxContext) {
    instanceService.getInstanceSummary(instanceId, headers)
      .map(json -> Response.ok(json, MediaType.APPLICATION_JSON).build())
      .onSuccess(response -> handler.handle(succeededFuture(response)))
      .onFailure(handleFailure(handler));
  }

  @Validate
  @Override
  public void deleteInstanceStorageInstancesSourceRecordByInstanceId(String instanceId,
                                                                     Map<String, String> headers,
                                                                     Handler<AsyncResult<Response>> handler,
                                                                     Context vertxContext) {
    referenceDataService.delete(INSTANCE_SOURCE_MARC_TABLE, MarcJson.class, instanceId, headers)
      .map(notUsed -> DeleteInstanceStorageInstancesSourceRecordByInstanceIdResponse.respond204())
      .onSuccess(response -> handler.handle(succeededFuture(response)))
      .onFailure(handleFailure(handler, errors ->
        DeleteInstanceStorageInstancesSourceRecordByInstanceIdResponse.respond400WithTextPlain(
          firstErrorMessage(errors))));
  }

  @Validate
  @Override
  public void getInstanceStorageInstancesSourceRecordMarcJsonByInstanceId(String instanceId,
                                                                          Map<String, String> headers,
                                                                          Handler<AsyncResult<Response>> handler,
                                                                          Context vertxContext) {
    referenceDataService.getById(INSTANCE_SOURCE_MARC_TABLE, MarcJson.class, instanceId, headers)
      .map(GetInstanceStorageInstancesSourceRecordMarcJsonByInstanceIdResponse::respond200WithApplicationJson)
      .onSuccess(response -> handler.handle(succeededFuture(response)))
      .onFailure(handleFailure(handler, errors ->
        GetInstanceStorageInstancesSourceRecordMarcJsonByInstanceIdResponse.respond404WithTextPlain(
          firstErrorMessage(errors))));
  }

  @Validate
  @Override
  public void deleteInstanceStorageInstancesSourceRecordMarcJsonByInstanceId(String instanceId,
                                                                             Map<String, String> headers,
                                                                             Handler<AsyncResult<Response>> handler,
                                                                             Context vertxContext) {
    referenceDataService.delete(INSTANCE_SOURCE_MARC_TABLE, MarcJson.class, instanceId, headers)
      .map(notUsed -> DeleteInstanceStorageInstancesSourceRecordMarcJsonByInstanceIdResponse.respond204())
      .onSuccess(response -> handler.handle(succeededFuture(response)))
      .onFailure(handleFailure(handler, errors ->
        DeleteInstanceStorageInstancesSourceRecordMarcJsonByInstanceIdResponse.respond400WithTextPlain(
          firstErrorMessage(errors))));
  }

  @Validate
  @Override
  public void putInstanceStorageInstancesSourceRecordMarcJsonByInstanceId(String instanceId,
                                                                          MarcJson entity,
                                                                          Map<String, String> headers,
                                                                          Handler<AsyncResult<Response>> handler,
                                                                          Context vertxContext) {
    referenceDataService.upsert(INSTANCE_SOURCE_MARC_TABLE, MarcJson.class, instanceId, entity, headers)
      .map(notUsed -> PutInstanceStorageInstancesSourceRecordMarcJsonByInstanceIdResponse.respond204())
      .onSuccess(response -> handler.handle(succeededFuture(response)))
      .onFailure(cause -> handler.handle(succeededFuture(marcUpsertFailure(cause))));
  }

  @Validate
  @Override
  public void postInstanceStorageInstancesRetrieve(RetrieveEntitiesRequest entity,
                                                   RoutingContext routingContext,
                                                   Map<String, String> headers,
                                                   Handler<AsyncResult<Response>> handler,
                                                   Context vertxContext) {
    fetchInstances(entity.getQuery(), entity.getLimit(), entity.getOffset(),
      routingContext, headers);
  }

  private Response marcUpsertFailure(Throwable cause) {
    if (PgExceptionUtil.isForeignKeyViolation(cause) && cause.getMessage().contains(INSTANCE_SOURCE_MARC_TABLE)) {
      return PutInstanceStorageInstancesSourceRecordMarcJsonByInstanceIdResponse
        .respond404WithTextPlain(cause.getMessage());
    }
    return PutInstanceStorageInstancesSourceRecordMarcJsonByInstanceIdResponse
      .respond500WithTextPlain(cause.getMessage());
  }

  private void fetchInstances(String query, int limit, int offset,
                              RoutingContext routingContext,
                              Map<String, String> headers) {
    instanceService.streamInstances(query, offset, limit, headers)
      .onSuccess(result -> StreamingJsonResponse.write(result, "instances", routingContext.response()))
      .onFailure(e -> StreamingJsonResponse.writeFailure(e, routingContext.response()));
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
}
