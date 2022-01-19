package org.folio.rest.impl;

import java.util.Map;

import javax.validation.constraints.Pattern;
import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.model.RelatedInstanceType;
import org.folio.rest.jaxrs.model.RelatedInstanceTypes;
import org.folio.rest.persist.PgUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

/**
 * Implements the related instancetype persistency using postgres jsonb.
 */
public class RelatedInstanceTypeAPI implements org.folio.rest.jaxrs.resource.RelatedInstanceTypes {

  public static final String RELATED_INSTANCE_TYPE_TABLE = "related_instance_type";

  @Override
  public void getRelatedInstanceTypes(String query, int offset, int limit, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    PgUtil.get(RELATED_INSTANCE_TYPE_TABLE, RelatedInstanceType.class, RelatedInstanceTypes.class, query, offset, limit,
      okapiHeaders, vertxContext, GetRelatedInstanceTypesResponse.class, asyncResultHandler);
  }

  @Override
  public void postRelatedInstanceTypes(String lang, RelatedInstanceType entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    PgUtil.post(RELATED_INSTANCE_TYPE_TABLE, entity, okapiHeaders, vertxContext,
      PostRelatedInstanceTypesResponse.class, asyncResultHandler);
  }

  @Override
  public void getRelatedInstanceTypesByRelatedInstanceTypeId(String instanceTypeId, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    PgUtil.getById(RELATED_INSTANCE_TYPE_TABLE, RelatedInstanceType.class, instanceTypeId,
        okapiHeaders, vertxContext, GetRelatedInstanceTypesByRelatedInstanceTypeIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteRelatedInstanceTypesByRelatedInstanceTypeId(String instanceTypeId, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    PgUtil.deleteById(RELATED_INSTANCE_TYPE_TABLE, instanceTypeId, okapiHeaders, vertxContext,
      DeleteRelatedInstanceTypesByRelatedInstanceTypeIdResponse.class, asyncResultHandler);
  }

  @Override
  public void putRelatedInstanceTypesByRelatedInstanceTypeId(String instanceTypeId, String lang, RelatedInstanceType entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    PgUtil.put(RELATED_INSTANCE_TYPE_TABLE, entity, instanceTypeId, okapiHeaders, vertxContext,
      PutRelatedInstanceTypesByRelatedInstanceTypeIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteRelatedInstanceTypes(@Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.delete(RELATED_INSTANCE_TYPE_TABLE, "id=*", okapiHeaders, vertxContext,
      DeleteRelatedInstanceTypesByRelatedInstanceTypeIdResponse.class, asyncResultHandler);
  }

}
