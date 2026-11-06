package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.ResponseHandlerUtil.handleFailureWith;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.ItemsPostRequest;
import org.folio.rest.jaxrs.resource.ItemStorageBatchSynchronousUnsafe;
import org.folio.rest.support.ResponseHandlerUtil;
import org.folio.services.item.ItemService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("java:S6813")
public class ItemBatchSyncUnsafeApi implements ItemStorageBatchSynchronousUnsafe {

  @Autowired
  private ItemService itemService;

  public ItemBatchSyncUnsafeApi() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Validate
  @Override
  public void postItemStorageBatchSynchronousUnsafe(ItemsPostRequest entity, Map<String, String> headers,
                                                    Handler<AsyncResult<Response>> asyncResultHandler,
                                                    Context vertxContext) {

    itemService.createItems(entity.getItems(), true, false, headers)
      .map(notUsed -> PostItemStorageBatchSynchronousUnsafeResponse.respond201())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailureWith(asyncResultHandler, ResponseHandlerUtil::handleHridError));
  }
}
