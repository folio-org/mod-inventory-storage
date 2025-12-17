package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.EndpointFailureHandler.handleFailure;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.ItemsPostRequest;
import org.folio.rest.jaxrs.resource.ItemStorageBatchSynchronous;
import org.folio.services.item.ItemService;

public class ItemBatchSyncApi implements ItemStorageBatchSynchronous {
  @Validate
  @Override
  public void postItemStorageBatchSynchronous(boolean upsert, ItemsPostRequest entity, Map<String, String> okapiHeaders,
                                              Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    new ItemService(vertxContext, okapiHeaders).createItems(entity.getItems(), upsert, true)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }
}
