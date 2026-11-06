package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.ResponseHandlerUtil.handleFailure;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.InstanceDateTypePatchRequest;
import org.folio.rest.jaxrs.resource.InstanceDateTypes;
import org.folio.services.instance.InstanceDateTypeService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("java:S6813")
public class InstanceDateTypeApi implements InstanceDateTypes {

  @Autowired
  private InstanceDateTypeService service;

  public InstanceDateTypeApi() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Validate
  @Override
  public void getInstanceDateTypes(String query, String totalRecords, int offset, int limit,
                                   Map<String, String> headers, Handler<AsyncResult<Response>> asyncResultHandler,
                                   Context vertxContext) {
    service.getInstanceDateTypes(query, offset, limit, headers)
      .map(GetInstanceDateTypesResponse::respond200WithApplicationJson)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void patchInstanceDateTypesById(String id, InstanceDateTypePatchRequest entity,
                                         Map<String, String> headers,
                                         Handler<AsyncResult<Response>> asyncResultHandler,
                                         Context vertxContext) {
    service.patchInstanceDateTypes(id, entity, headers)
      .map(notUsed -> PatchInstanceDateTypesByIdResponse.respond204())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }
}
