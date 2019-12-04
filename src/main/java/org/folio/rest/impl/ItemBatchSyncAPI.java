package org.folio.rest.impl;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.rest.jaxrs.resource.ItemStorageBatchSynchronous.PostItemStorageBatchSynchronousResponse.respond500WithTextPlain;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.ItemsPost;
import org.folio.rest.jaxrs.resource.ItemStorageBatchSynchronous;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.HridManager;
import org.folio.rest.tools.utils.TenantTool;

import javax.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ItemBatchSyncAPI implements ItemStorageBatchSynchronous {
  @Validate
  @Override
  public void postItemStorageBatchSynchronous(ItemsPost entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    final List<Item> items = entity.getItems();
    final PostgresClient postgresClient = PostgresClient.getInstance(
          vertxContext.owner(), TenantTool.tenantId(okapiHeaders));
    // Currently, there is no method on CompositeFuture to accept List<Future<String>>
    @SuppressWarnings("rawtypes")
    final List<Future> futures = new ArrayList<>();
    final HridManager hridManager = new HridManager(Vertx.currentContext(), postgresClient);

    for (Item item : items) {
      futures.add(setHrid(item, hridManager));
    }

    CompositeFuture.all(futures).setHandler(ar -> {
      if (ar.succeeded()) {
        StorageHelper.postSync(ItemStorageAPI.ITEM_TABLE, entity.getItems(),
            okapiHeaders, asyncResultHandler, vertxContext,
            PostItemStorageBatchSynchronousResponse::respond201);
      } else {
        asyncResultHandler.handle(
            Future.succeededFuture(respond500WithTextPlain(ar.cause().getMessage())));
      }
    });
  }

  private Future<Void> setHrid(Item item, HridManager hridManager) {
    final Future<String> hridFuture;

    if (isBlank(item.getHrid())) {
      hridFuture = hridManager.getNextItemHrid();
    } else {
      hridFuture = StorageHelper.completeFuture(item.getHrid());
    }

    return hridFuture.map(hrid -> {
      item.setHrid(hrid);
      return null;
    });
  }
}
