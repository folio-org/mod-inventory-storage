package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.EndpointFailureHandler.handleFailure;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.ServicePointsUser;
import org.folio.services.servicepoint.ServicePointsUserService;

public class ServicePointsUserApi implements org.folio.rest.jaxrs.resource.ServicePointsUsers {

  @Validate
  @Override
  public void getServicePointsUsers(String query, String totalRecords, int offset, int limit,
                                    Map<String, String> okapiHeaders,
                                    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    new ServicePointsUserService(vertxContext, okapiHeaders)
      .getByQuery(query, offset, limit, totalRecords)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void postServicePointsUsers(ServicePointsUser entity,
                                     Map<String, String> okapiHeaders,
                                     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    new ServicePointsUserService(vertxContext, okapiHeaders)
      .create(entity)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void deleteServicePointsUsers(Map<String, String> okapiHeaders,
                                       Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    new ServicePointsUserService(vertxContext, okapiHeaders)
      .deleteAll()
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void getServicePointsUsersByServicePointsUserId(String servicePointsUserId,
                                                         Map<String, String> okapiHeaders,
                                                         Handler<AsyncResult<Response>> asyncResultHandler,
                                                         Context vertxContext) {
    new ServicePointsUserService(vertxContext, okapiHeaders)
      .getById(servicePointsUserId)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void deleteServicePointsUsersByServicePointsUserId(String servicePointsUserId,
                                                            Map<String, String> okapiHeaders,
                                                            Handler<AsyncResult<Response>> asyncResultHandler,
                                                            Context vertxContext) {
    new ServicePointsUserService(vertxContext, okapiHeaders)
      .delete(servicePointsUserId)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void putServicePointsUsersByServicePointsUserId(String servicePointsUserId,
                                                         ServicePointsUser entity,
                                                         Map<String, String> okapiHeaders,
                                                         Handler<AsyncResult<Response>> asyncResultHandler,
                                                         Context vertxContext) {
    new ServicePointsUserService(vertxContext, okapiHeaders)
      .update(servicePointsUserId, entity)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }
}
