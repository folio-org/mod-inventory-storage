package org.folio.rest.impl;

import java.util.Map;

import javax.validation.constraints.Pattern;
import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.model.RelatedInstance;
import org.folio.rest.jaxrs.model.RelatedInstances;
import org.folio.rest.persist.PgUtil;
import org.folio.services.relatedInstance.RelatedInstanceService;
import static org.folio.rest.support.EndpointFailureHandler.handleFailure;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;

/**
 * Implements the related instance persistency using postgres jsonb.
 */
public class RelatedInstanceAPI implements org.folio.rest.jaxrs.resource.InstanceStorageRelatedInstances {
  public static final String RELATED_INSTANCE_TABLE = "related_instance";

  @Override
  public void getInstanceStorageRelatedInstances(String query, int offset, int limit, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    PgUtil.get(RELATED_INSTANCE_TABLE, RelatedInstance.class, RelatedInstances.class, query, offset, limit,
      okapiHeaders, vertxContext, GetInstanceStorageRelatedInstancesResponse.class, asyncResultHandler);
  }

  @Override
  public void postInstanceStorageRelatedInstances(String lang, RelatedInstance entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    if (checkInstanceRelatedToSelf(entity)) {
      asyncResultHandler.handle(cannotBeRelatedToSelf());
      return;
    }

    new RelatedInstanceService(vertxContext, okapiHeaders)
      .createRelatedInstance(entity)
      .onSuccess(response -> asyncResultHandler.handle(Future.succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Override
  public void getInstanceStorageRelatedInstancesByRelatedInstanceId(String instanceId, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    PgUtil.getById(RELATED_INSTANCE_TABLE, RelatedInstance.class, instanceId,
        okapiHeaders, vertxContext, GetInstanceStorageRelatedInstancesByRelatedInstanceIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteInstanceStorageRelatedInstancesByRelatedInstanceId(String instanceId, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    PgUtil.deleteById(RELATED_INSTANCE_TABLE, instanceId, okapiHeaders, vertxContext,
      DeleteInstanceStorageRelatedInstancesByRelatedInstanceIdResponse.class, asyncResultHandler);
  }

  @Override
  public void putInstanceStorageRelatedInstancesByRelatedInstanceId(String instanceId, String lang, RelatedInstance entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {
    if (checkInstanceRelatedToSelf(entity)) {
        asyncResultHandler.handle(cannotBeRelatedToSelf());
        return;
    }
    new RelatedInstanceService(vertxContext, okapiHeaders)
      .updateRelatedInstance(entity)
      .onSuccess(response -> asyncResultHandler.handle(Future.succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Override
  public void deleteInstanceStorageRelatedInstances(@Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.delete(RELATED_INSTANCE_TABLE, "id=*", okapiHeaders, vertxContext,
      DeleteInstanceStorageRelatedInstancesByRelatedInstanceIdResponse.class, asyncResultHandler);
  }
  private Future<Response> cannotBeRelatedToSelf() {
    return Future.succeededFuture(PutInstanceStorageRelatedInstancesByRelatedInstanceIdResponse
    .respond400WithTextPlain("Instance cannot be related to itself."));
  }

  private boolean checkInstanceRelatedToSelf(RelatedInstance entity) {
    if (entity.getInstanceId().equals(entity.getRelatedInstanceId())) {
      return true;
    }
    return false;
  }

}
