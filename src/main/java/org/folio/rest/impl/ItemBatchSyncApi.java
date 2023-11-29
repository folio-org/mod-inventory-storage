package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.ItemsPost;
import org.folio.rest.jaxrs.resource.ItemStorageBatchSynchronous;
import org.folio.rest.support.EndpointFailureHandler;
import org.folio.services.item.ItemService;

public class ItemBatchSyncApi implements ItemStorageBatchSynchronous {
  private static final Logger log = LogManager.getLogger();

  @Validate
  @Override
  public void postItemStorageBatchSynchronous(boolean upsert, ItemsPost entity, Map<String, String> okapiHeaders,
                                              Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    new ItemService(vertxContext, okapiHeaders).createItems(entity.getItems(), upsert, true)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(error -> {
        log.error("postItemStorageBatchSynchronous: Error occurred: {}", error.getClass().getName());
        log.warn("postItemStorageBatchSynchronous: Error occurred: {}", error.getClass().getName());
        EndpointFailureHandler.handleFailure(error, asyncResultHandler,
          PostItemStorageBatchSynchronousResponse::respond422WithApplicationJson,
          PostItemStorageBatchSynchronousResponse::respond500WithTextPlain);
      });
  }
}
