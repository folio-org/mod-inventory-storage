package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.jaxrs.model.BoundWithPart;
import org.folio.rest.jaxrs.model.BoundWithParts;
import org.folio.rest.persist.PgUtil;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;
import javax.ws.rs.core.Response;
import java.util.Map;

public class BoundWithPartAPI implements org.folio.rest.jaxrs.resource.InventoryStorageBoundWithParts {
  private static final String BOUND_WITH_TABLE = "bound_with_part";

  @Override
  public void getInventoryStorageBoundWithParts(String query, @Min(0) @Max(2147483647) int offset, @Min(0) @Max(2147483647) int limit, @Pattern(regexp = "[a-zA-Z]{2}") String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(BOUND_WITH_TABLE, BoundWithPart.class, BoundWithParts.class, query, offset, limit,
      okapiHeaders, vertxContext, org.folio.rest.jaxrs.resource.InventoryStorageBoundWithParts.GetInventoryStorageBoundWithPartsResponse.class, asyncResultHandler);
  }

  @Override
  public void postInventoryStorageBoundWithParts(@Pattern(regexp = "[a-zA-Z]{2}") String lang, BoundWithPart entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(BOUND_WITH_TABLE, entity, okapiHeaders, vertxContext,
      PostInventoryStorageBoundWithPartsResponse.class, asyncResultHandler);
  }

  @Override
  public void getInventoryStorageBoundWithPartsById(String id, @Pattern(regexp = "[a-zA-Z]{2}") String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(BOUND_WITH_TABLE, BoundWithPart.class, id,
      okapiHeaders, vertxContext, GetInventoryStorageBoundWithPartsByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteInventoryStorageBoundWithPartsById(String id, @Pattern(regexp = "[a-zA-Z]{2}") String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(BOUND_WITH_TABLE, id, okapiHeaders, vertxContext,
      DeleteInventoryStorageBoundWithPartsByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void putInventoryStorageBoundWithPartsById(String id, @Pattern(regexp = "[a-zA-Z]{2}") String lang, BoundWithPart entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(BOUND_WITH_TABLE, entity, id, okapiHeaders, vertxContext,
      PutInventoryStorageBoundWithPartsByIdResponse.class, asyncResultHandler);
  }
}
