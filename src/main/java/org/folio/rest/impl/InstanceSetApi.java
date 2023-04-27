package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.resource.InventoryViewInstanceSet;
import org.folio.rest.support.EndpointHandler;
import org.folio.services.instance.InstanceService;

public class InstanceSetApi implements InventoryViewInstanceSet {

  @Validate
  @Override
  public void getInventoryViewInstanceSet(boolean instance, boolean holdingsRecords, boolean items,
                                          boolean precedingTitles, boolean succeedingTitles,
                                          boolean superInstanceRelationships, boolean subInstanceRelationships,
                                          int offset, int limit, String query, String lang,
                                          Map<String, String> okapiHeaders,
                                          Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    new InstanceService(vertxContext, okapiHeaders)
      .getInstanceSet(instance, holdingsRecords, items,
        precedingTitles, succeedingTitles, superInstanceRelationships, subInstanceRelationships,
        offset, limit, query)
      .onComplete(EndpointHandler.handle(asyncResultHandler));
  }
}
