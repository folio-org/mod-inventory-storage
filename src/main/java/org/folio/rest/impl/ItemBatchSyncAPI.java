package org.folio.rest.impl;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.ItemsPost;
import org.folio.rest.jaxrs.resource.ItemStorageBatchSynchronous;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.EndpointFailureHandler;
import org.folio.rest.support.HridManager;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.services.ItemEffectiveCallNumberComponentsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

public class ItemBatchSyncAPI implements ItemStorageBatchSynchronous {
  private static final Logger log = LoggerFactory.getLogger(ItemBatchSyncAPI.class);

  @Validate
  @Override
  public void postItemStorageBatchSynchronous(ItemsPost entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    final List<Item> items = entity.getItems();
    final PostgresClient postgresClient = PostgresClient.getInstance(
          vertxContext.owner(), TenantTool.tenantId(okapiHeaders));
    final ItemEffectiveCallNumberComponentsService effectiveCallNumberService =
      new ItemEffectiveCallNumberComponentsService(postgresClient);

    // Currently, there is no method on CompositeFuture to accept List<Future<String>>
    @SuppressWarnings("rawtypes")
    final List<Future> futures = new ArrayList<>();
    final HridManager hridManager = new HridManager(Vertx.currentContext(), postgresClient);

    for (Item item : items) {
      futures.add(setHrid(item, hridManager));
    }

    CompositeFuture.all(futures)
      .compose(result -> effectiveCallNumberService.populateEffectiveCallNumberComponents(items))
      .map(result -> {
        StorageHelper.postSync(ItemStorageAPI.ITEM_TABLE, entity.getItems(),
          okapiHeaders, asyncResultHandler, vertxContext,
          PostItemStorageBatchSynchronousResponse::respond201);
        return result;
      }).otherwise(EndpointFailureHandler.handleFailure(asyncResultHandler,
      PostItemStorageBatchSynchronousResponse::respond422WithApplicationJson,
      PostItemStorageBatchSynchronousResponse::respond500WithTextPlain
    ));
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
