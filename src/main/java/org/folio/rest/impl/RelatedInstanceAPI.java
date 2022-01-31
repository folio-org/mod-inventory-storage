package org.folio.rest.impl;

import java.util.Map;

import javax.validation.constraints.Pattern;
import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.model.RelatedInstance;
import org.folio.rest.jaxrs.model.RelatedInstances;
import org.folio.rest.persist.PgUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

/**
 * Implements the related instancetype persistency using postgres jsonb.
 */
public class RelatedInstanceAPI implements org.folio.rest.jaxrs.resource.RelatedInstances {

  public static final String RELATED_INSTANCE_TABLE = "related_instance";

  @Override
  public void getRelatedInstances(String query, int offset, int limit, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    PgUtil.get(RELATED_INSTANCE_TABLE, RelatedInstance.class, RelatedInstances.class, query, offset, limit,
      okapiHeaders, vertxContext, GetRelatedInstancesResponse.class, asyncResultHandler);
  }

  @Override
  public void postRelatedInstances(String lang, RelatedInstance entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    PgUtil.post(RELATED_INSTANCE_TABLE, entity, okapiHeaders, vertxContext,
      PostRelatedInstancesResponse.class, asyncResultHandler);
  }

  @Override
  public void getRelatedInstancesByRelatedInstanceId(String instanceId, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    PgUtil.getById(RELATED_INSTANCE_TABLE, RelatedInstance.class, instanceId,
        okapiHeaders, vertxContext, GetRelatedInstancesByRelatedInstanceIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteRelatedInstancesByRelatedInstanceId(String instanceId, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    PgUtil.deleteById(RELATED_INSTANCE_TABLE, instanceId, okapiHeaders, vertxContext,
      DeleteRelatedInstancesByRelatedInstanceIdResponse.class, asyncResultHandler);
  }

  @Override
  public void putRelatedInstancesByRelatedInstanceId(String instanceId, String lang, RelatedInstance entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    PgUtil.put(RELATED_INSTANCE_TABLE, entity, instanceId, okapiHeaders, vertxContext,
      PutRelatedInstancesByRelatedInstanceIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteRelatedInstances(@Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.delete(RELATED_INSTANCE_TABLE, "id=*", okapiHeaders, vertxContext,
      DeleteRelatedInstancesByRelatedInstanceIdResponse.class, asyncResultHandler);
  }

}
