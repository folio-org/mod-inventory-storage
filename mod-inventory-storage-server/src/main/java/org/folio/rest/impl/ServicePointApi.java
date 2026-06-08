package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.EndpointFailureHandler.handleFailure;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.ServicePoint;
import org.folio.services.servicepoint.ServicePointService;

public class ServicePointApi implements org.folio.rest.jaxrs.resource.ServicePoints {

  public static final String SERVICE_POINT_CREATE_ERR_MSG_WITHOUT_HOLD_EXPIRY =
    "Hold shelf expiry period must be specified when service point can be used for pickup.";
  public static final String SERVICE_POINT_CREATE_ERR_MSG_WITHOUT_BEING_PICKUP_LOC =
    "Hold shelf expiry period cannot be specified when service point cannot be used for pickup";

  public static String validateServicePoint(ServicePoint svcpt) {
    var holdShelfExpiryPeriod = svcpt.getHoldShelfExpiryPeriod();
    boolean pickupLocation = svcpt.getPickupLocation() == null ? Boolean.FALSE : svcpt.getPickupLocation();
    if (!pickupLocation && holdShelfExpiryPeriod != null) {
      return SERVICE_POINT_CREATE_ERR_MSG_WITHOUT_BEING_PICKUP_LOC;
    } else if (pickupLocation && holdShelfExpiryPeriod == null) {
      return SERVICE_POINT_CREATE_ERR_MSG_WITHOUT_HOLD_EXPIRY;
    }
    return null;
  }

  @Validate
  @Override
  public void getServicePoints(boolean includeRoutingServicePoints, String query,
                               String totalRecords, int offset, int limit,
                               Map<String, String> okapiHeaders,
                               Handler<AsyncResult<Response>> asyncResultHandler,
                               Context vertxContext) {
    new ServicePointService(vertxContext, okapiHeaders)
      .getByQuery(query, offset, limit, includeRoutingServicePoints)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void postServicePoints(ServicePoint entity,
                                Map<String, String> okapiHeaders,
                                Handler<AsyncResult<Response>> asyncResultHandler,
                                Context vertxContext) {
    new ServicePointService(vertxContext, okapiHeaders)
      .create(entity)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void deleteServicePoints(Map<String, String> okapiHeaders,
                                  Handler<AsyncResult<Response>> asyncResultHandler,
                                  Context vertxContext) {
    new ServicePointService(vertxContext, okapiHeaders)
      .deleteAll()
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void putServicePointsByServicepointId(String servicepointId,
                                               ServicePoint entity, Map<String, String> okapiHeaders,
                                               Handler<AsyncResult<Response>> asyncResultHandler,
                                               Context vertxContext) {
    new ServicePointService(vertxContext, okapiHeaders)
      .update(servicepointId, entity)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void getServicePointsByServicepointId(String servicepointId,
                                               Map<String, String> okapiHeaders,
                                               Handler<AsyncResult<Response>> asyncResultHandler,
                                               Context vertxContext) {
    new ServicePointService(vertxContext, okapiHeaders)
      .getById(servicepointId)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void deleteServicePointsByServicepointId(String servicepointId,
                                                  Map<String, String> okapiHeaders,
                                                  Handler<AsyncResult<Response>> asyncResultHandler,
                                                  Context vertxContext) {
    new ServicePointService(vertxContext, okapiHeaders)
      .delete(servicepointId)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }
}
