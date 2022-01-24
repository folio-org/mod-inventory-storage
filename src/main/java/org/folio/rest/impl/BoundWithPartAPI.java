package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.BoundWithPart;
import org.folio.rest.jaxrs.model.BoundWithParts;
import org.folio.rest.persist.PgUtil;

import javax.ws.rs.core.Response;
import java.util.Map;

public class BoundWithPartAPI implements org.folio.rest.jaxrs.resource.InventoryStorageBoundWithParts {
  private static final String BOUND_WITH_TABLE = "bound_with_part";

  @Validate
  @Override
  public void getInventoryStorageBoundWithParts(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(BOUND_WITH_TABLE, BoundWithPart.class, BoundWithParts.class, query, offset, limit,
      okapiHeaders, vertxContext, org.folio.rest.jaxrs.resource.InventoryStorageBoundWithParts.GetInventoryStorageBoundWithPartsResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void postInventoryStorageBoundWithParts(String lang, BoundWithPart entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(BOUND_WITH_TABLE, entity, okapiHeaders, vertxContext,
      PostInventoryStorageBoundWithPartsResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void getInventoryStorageBoundWithPartsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(BOUND_WITH_TABLE, BoundWithPart.class, id,
      okapiHeaders, vertxContext, GetInventoryStorageBoundWithPartsByIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteInventoryStorageBoundWithPartsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(BOUND_WITH_TABLE, id, okapiHeaders, vertxContext,
      DeleteInventoryStorageBoundWithPartsByIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void putInventoryStorageBoundWithPartsById(String id, String lang, BoundWithPart entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(BOUND_WITH_TABLE, entity, id, okapiHeaders, vertxContext,
      PutInventoryStorageBoundWithPartsByIdResponse.class, asyncResultHandler);
  }
}
