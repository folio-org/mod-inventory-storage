package org.folio.rest.impl;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.HoldingsrecordsPost;
import org.folio.rest.jaxrs.resource.HoldingsStorageBatchSynchronous;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.HridManager;
import org.folio.rest.tools.utils.TenantTool;

import javax.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HoldingsBatchSyncAPI implements HoldingsStorageBatchSynchronous {
  @Validate
  @Override
  public void postHoldingsStorageBatchSynchronous(boolean upsert, HoldingsrecordsPost entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    final List<HoldingsRecord> holdingsRecords = entity.getHoldingsRecords();
    final PostgresClient postgresClient = PostgresClient.getInstance(
          vertxContext.owner(), TenantTool.tenantId(okapiHeaders));
    // Currently, there is no method on CompositeFuture to accept List<Future<String>>
    @SuppressWarnings("rawtypes")
    final List<Future> futures = new ArrayList<>();
    final HridManager hridManager = new HridManager(Vertx.currentContext(), postgresClient);

    for (HoldingsRecord holdingsRecord : holdingsRecords) {
      futures.add(setHrid(holdingsRecord, hridManager));
    }

    CompositeFuture.all(futures).setHandler(ar -> {
      if (ar.succeeded()) {
        StorageHelper.postSync(HoldingsStorageAPI.HOLDINGS_RECORD_TABLE, holdingsRecords,
            okapiHeaders, upsert, asyncResultHandler, vertxContext,
            HoldingsStorageBatchSynchronous.PostHoldingsStorageBatchSynchronousResponse::respond201);
      } else {
        asyncResultHandler.handle(
            Future.succeededFuture(PostHoldingsStorageBatchSynchronousResponse
                .respond500WithTextPlain(ar.cause().getMessage())));
      }
    });
  }

  private Future<Void> setHrid(HoldingsRecord holdingsRecord, HridManager hridManager) {
    final Future<String> hridFuture;

    if (isBlank(holdingsRecord.getHrid())) {
      hridFuture = hridManager.getNextHoldingsHrid();
    } else {
      hridFuture = StorageHelper.completeFuture(holdingsRecord.getHrid());
    }

    return hridFuture.map(hrid -> {
      holdingsRecord.setHrid(hrid);
      return null;
    });
  }
}
