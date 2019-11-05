package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.ItemsWithId;
import org.folio.rest.jaxrs.resource.ItemStorageSync;

import javax.ws.rs.core.Response;
import java.util.Map;

public class ItemSyncAPI implements ItemStorageSync {
  @Validate
  @Override
  public void postItemStorageSync(ItemsWithId entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    StorageHelper.postSync(ItemStorageAPI.ITEM_TABLE, entity.getItems(),
        okapiHeaders, asyncResultHandler, vertxContext,
        ItemStorageSync.PostItemStorageSyncResponse::respond201);
  }
}
