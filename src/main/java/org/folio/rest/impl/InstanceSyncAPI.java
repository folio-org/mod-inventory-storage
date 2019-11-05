package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.InstancesWithId;
import org.folio.rest.jaxrs.resource.InstanceStorageSync;
import javax.ws.rs.core.Response;
import java.util.Map;

public class InstanceSyncAPI implements InstanceStorageSync {
  @Validate
  @Override
  public void postInstanceStorageSync(InstancesWithId entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    StorageHelper.postSync(InstanceStorageAPI.INSTANCE_TABLE, entity.getInstances(),
        okapiHeaders, asyncResultHandler, vertxContext,
        InstanceStorageSync.PostInstanceStorageSyncResponse::respond201);
  }
}
