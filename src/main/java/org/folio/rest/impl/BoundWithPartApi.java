package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.EndpointFailureHandler.handleFailure;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.BoundWithPart;
import org.folio.rest.jaxrs.model.BoundWithParts;
import org.folio.rest.jaxrs.resource.InventoryStorageBoundWithParts;
import org.folio.rest.persist.PgUtil;
import org.folio.services.instance.BoundWithPartService;

public class BoundWithPartApi implements org.folio.rest.jaxrs.resource.InventoryStorageBoundWithParts {
  public static final String BOUND_WITH_TABLE = "bound_with_part";

  @Validate
  @Override
  public void getInventoryStorageBoundWithParts(String query, int offset, int limit, String lang,
                                                Map<String, String> okapiHeaders,
                                                Handler<AsyncResult<Response>> asyncResultHandler,
                                                Context vertxContext) {
    PgUtil.get(BOUND_WITH_TABLE, BoundWithPart.class, BoundWithParts.class, query, offset, limit,
      okapiHeaders, vertxContext, InventoryStorageBoundWithParts.GetInventoryStorageBoundWithPartsResponse.class,
      asyncResultHandler);
  }

  @Validate
  @Override
  public void postInventoryStorageBoundWithParts(String lang, BoundWithPart entity, Map<String, String> okapiHeaders,
                                                 Handler<AsyncResult<Response>> asyncResultHandler,
                                                 Context vertxContext) {
    new BoundWithPartService(vertxContext, okapiHeaders).create(entity)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void getInventoryStorageBoundWithPartsById(String id, String lang, Map<String, String> okapiHeaders,
                                                    Handler<AsyncResult<Response>> asyncResultHandler,
                                                    Context vertxContext) {
    PgUtil.getById(BOUND_WITH_TABLE, BoundWithPart.class, id,
      okapiHeaders, vertxContext, GetInventoryStorageBoundWithPartsByIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteInventoryStorageBoundWithPartsById(String id, String lang, Map<String, String> okapiHeaders,
                                                       Handler<AsyncResult<Response>> asyncResultHandler,
                                                       Context vertxContext) {
    new BoundWithPartService(vertxContext, okapiHeaders).delete(id)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void putInventoryStorageBoundWithPartsById(String id, String lang, BoundWithPart entity,
                                                    Map<String, String> okapiHeaders,
                                                    Handler<AsyncResult<Response>> asyncResultHandler,
                                                    Context vertxContext) {
    new BoundWithPartService(vertxContext, okapiHeaders).update(entity, id)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }
}
