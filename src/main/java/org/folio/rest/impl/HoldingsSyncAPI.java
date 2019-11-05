package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.HoldingsrecordsWithId;
import org.folio.rest.jaxrs.resource.HoldingsStorageSync;
import org.folio.rest.jaxrs.resource.InstanceStorageSync;
import javax.ws.rs.core.Response;
import java.util.Map;

public class HoldingsSyncAPI implements HoldingsStorageSync {
  @Validate
  @Override
  public void postHoldingsStorageSync(HoldingsrecordsWithId entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    StorageHelper.postSync(HoldingsStorageAPI.HOLDINGS_RECORD_TABLE, entity.getHoldingsRecords(),
        okapiHeaders, asyncResultHandler, vertxContext,
        InstanceStorageSync.PostInstanceStorageSyncResponse::respond201);
  }
}
