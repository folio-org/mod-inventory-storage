package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.util.Map;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.folio.persist.entity.InstanceSetParts;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.resource.InventoryViewInstanceSet;
import org.folio.rest.support.ResponseHandlerUtil;
import org.folio.services.instanceset.InstanceSetService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("java:S6813")
public class InstanceSetApi implements InventoryViewInstanceSet {

  @Autowired
  private InstanceSetService instanceSetService;

  public InstanceSetApi() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Validate
  @Override
  public void getInventoryViewInstanceSet(boolean instance, boolean holdingsRecords, boolean items,
                                          boolean precedingTitles, boolean succeedingTitles,
                                          boolean superInstanceRelationships, boolean subInstanceRelationships,
                                          int offset, int limit, String query,
                                          Map<String, String> headers,
                                          Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    var parts = new InstanceSetParts(instance, holdingsRecords, items, precedingTitles, succeedingTitles,
      superInstanceRelationships, subInstanceRelationships);

    instanceSetService.getInstanceSet(query, parts, offset, limit, headers)
      .map(json -> Response.ok(json, MediaType.APPLICATION_JSON_TYPE).build())
      .onComplete(ResponseHandlerUtil.handle(asyncResultHandler));
  }
}
