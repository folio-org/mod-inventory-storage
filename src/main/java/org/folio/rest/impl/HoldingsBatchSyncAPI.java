package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.HoldingsrecordsPost;
import org.folio.rest.jaxrs.resource.HoldingsStorageBatchSynchronous;
import javax.ws.rs.core.Response;
import java.util.Map;

public class HoldingsBatchSyncAPI implements HoldingsStorageBatchSynchronous {
  @Validate
  @Override
  public void postHoldingsStorageBatchSynchronous(HoldingsrecordsPost entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    StorageHelper.postSync(HoldingsStorageAPI.HOLDINGS_RECORD_TABLE, entity.getHoldingsRecords(),
        okapiHeaders, asyncResultHandler, vertxContext,
        HoldingsStorageBatchSynchronous.PostHoldingsStorageBatchSynchronousResponse::respond201);
  }
}
