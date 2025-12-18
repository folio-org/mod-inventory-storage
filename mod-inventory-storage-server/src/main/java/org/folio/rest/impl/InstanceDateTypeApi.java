package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.EndpointFailureHandler.handleFailure;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.InstanceDateTypePatchRequest;
import org.folio.rest.jaxrs.resource.InstanceDateTypes;
import org.folio.services.instance.InstanceDateTypeService;

public class InstanceDateTypeApi implements InstanceDateTypes {

  @Validate
  @Override
  public void getInstanceDateTypes(String query, String totalRecords, int offset, int limit,
                                   Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
                                   Context vertxContext) {
    new InstanceDateTypeService(vertxContext, okapiHeaders)
      .getInstanceDateTypes(query, offset, limit)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void patchInstanceDateTypesById(String id, InstanceDateTypePatchRequest entity,
                                         Map<String, String> okapiHeaders,
                                         Handler<AsyncResult<Response>> asyncResultHandler,
                                         Context vertxContext) {
    new InstanceDateTypeService(vertxContext, okapiHeaders)
      .patchInstanceDateTypes(id, entity)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }
}
