package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static java.lang.Boolean.TRUE;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.folio.rest.support.ResponseHandlerUtil.firstErrorMessage;
import static org.folio.rest.support.ResponseHandlerUtil.handleFailure;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.util.Map;
import java.util.UUID;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.ServicePoint;
import org.folio.rest.tools.utils.ValidationHelper;
import org.folio.services.servicepoint.ServicePointService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("java:S6813")
public class ServicePointApi implements org.folio.rest.jaxrs.resource.ServicePoints {
  public static final String SERVICE_POINT_TABLE = "service_point";
  public static final String LOCATION_PREFIX = "/service-points/";
  public static final String SERVICE_POINT_CREATE_ERR_MSG_WITHOUT_HOLD_EXPIRY =
    "Hold shelf expiry period must be specified when service point can be used for pickup.";
  public static final String SERVICE_POINT_CREATE_ERR_MSG_WITHOUT_BEING_PICKUP_LOC =
    "Hold shelf expiry period cannot be specified when service point cannot be used for pickup";
  private static final String ECS_ROUTING_QUERY_FILTER = "cql.allRecords=1 NOT ecsRequestRouting=true";
  private static final Logger logger = LogManager.getLogger();

  @Autowired
  private ServicePointService servicePointService;

  public ServicePointApi() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

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
                               Map<String, String> headers,
                               Handler<AsyncResult<Response>> asyncResultHandler,
                               Context vertxContext) {

    query = updateGetServicePointsQuery(query, includeRoutingServicePoints);
    servicePointService.getByQuery(query, offset, limit, totalRecords, headers)
      .map(GetServicePointsResponse::respond200WithApplicationJson)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void postServicePoints(ServicePoint entity,
                                Map<String, String> headers,
                                Handler<AsyncResult<Response>> asyncResultHandler,
                                Context vertxContext) {
    try {
      if (!validateAndPrepareEntity(entity, asyncResultHandler)) {
        return;
      }

      servicePointService.createServicePoint(entity.getId(), entity, headers)
        .map(created -> PostServicePointsResponse.respond201WithApplicationJson(created,
          PostServicePointsResponse.headersFor201().withLocation(LOCATION_PREFIX + created.getId())))
        .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
        .onFailure(throwable -> handlePostServicePointFailure(throwable, entity, asyncResultHandler));
    } catch (Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
        PostServicePointsResponse.respond500WithTextPlain(
          getErrorResponse(message))));
    }
  }

  @Validate
  @Override
  public void deleteServicePoints(Map<String, String> headers,
                                  Handler<AsyncResult<Response>> asyncResultHandler,
                                  Context vertxContext) {
    servicePointService.deleteAll(headers)
      .onSuccess(notUsed -> asyncResultHandler.handle(succeededFuture(DeleteServicePointsResponse.respond204())))
      .onFailure(cause -> {
        String message = logAndSaveError(cause);
        asyncResultHandler.handle(succeededFuture(
          DeleteServicePointsResponse.respond500WithTextPlain(getErrorResponse(message))));
      });
  }

  @Validate
  @Override
  public void putServicePointsByServicepointId(String servicepointId,
                                               ServicePoint entity, Map<String, String> headers,
                                               Handler<AsyncResult<Response>> asyncResultHandler,
                                               Context vertxContext) {

    try {
      String validateSvcptResult = validateServicePoint(entity);
      if (validateSvcptResult != null) {
        asyncResultHandler.handle(Future.succeededFuture(
          PutServicePointsByServicepointIdResponse
            .respond422WithApplicationJson(ValidationHelper.createValidationErrorMessage("name",
              entity.getName(), validateSvcptResult))));
        return;
      }
      servicePointService.updateServicePoint(servicepointId, entity, headers)
        .map(notUsed -> PutServicePointsByServicepointIdResponse.respond204())
        .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
        .onFailure(handleFailure(asyncResultHandler));
    } catch (Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
        PutServicePointsByServicepointIdResponse
          .respond500WithTextPlain(getErrorResponse(message))));
    }
  }

  @Validate
  @Override
  public void getServicePointsByServicepointId(String servicepointId,
                                               Map<String, String> headers,
                                               Handler<AsyncResult<Response>> asyncResultHandler,
                                               Context vertxContext) {

    servicePointService.getById(servicepointId, headers)
      .map(GetServicePointsByServicepointIdResponse::respond200WithApplicationJson)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler, errors ->
        GetServicePointsByServicepointIdResponse.respond404WithTextPlain(firstErrorMessage(errors))));
  }

  @Validate
  @Override
  public void deleteServicePointsByServicepointId(String servicepointId,
                                                  Map<String, String> headers,
                                                  Handler<AsyncResult<Response>> asyncResultHandler,
                                                  Context vertxContext) {
    try {
      checkServicePointInUse().onComplete(inUseRes ->
        handleServicePointInUseCheck(inUseRes, servicepointId, headers, asyncResultHandler));
    } catch (Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
        DeleteServicePointsByServicepointIdResponse
          .respond500WithTextPlain(getErrorResponse(message))));
    }
  }

  private boolean validateAndPrepareEntity(ServicePoint entity,
                                           Handler<AsyncResult<Response>> asyncResultHandler) {
    String validateSvcptResult = validateServicePoint(entity);
    if (validateSvcptResult != null) {
      asyncResultHandler.handle(Future.succeededFuture(
        PostServicePointsResponse.respond422WithApplicationJson(
          ValidationHelper.createValidationErrorMessage("name",
            entity.getName(), validateSvcptResult))));
      return false;
    }

    if (entity.getId() == null) {
      entity.setId(UUID.randomUUID().toString());
    }
    return true;
  }

  private void handlePostServicePointFailure(Throwable throwable, ServicePoint entity,
                                             Handler<AsyncResult<Response>> asyncResultHandler) {
    String message = logAndSaveError(throwable);
    if (isDuplicate(message)) {
      asyncResultHandler.handle(Future.succeededFuture(
        PostServicePointsResponse.respond422WithApplicationJson(
          ValidationHelper.createValidationErrorMessage("name",
            entity.getName(), "Service Point Exists"))));
    } else {
      asyncResultHandler.handle(Future.succeededFuture(
        PostServicePointsResponse.respond500WithTextPlain(
          getErrorResponse(message))));
    }
  }

  private void handleServicePointInUseCheck(AsyncResult<Boolean> inUseRes, String servicepointId,
                                            Map<String, String> headers,
                                            Handler<AsyncResult<Response>> asyncResultHandler) {
    if (inUseRes.failed()) {
      String message = logAndSaveError(inUseRes.cause());
      asyncResultHandler.handle(Future.succeededFuture(
        DeleteServicePointsByServicepointIdResponse
          .respond500WithTextPlain(getErrorResponse(message))));
    } else if (TRUE.equals(inUseRes.result())) {
      asyncResultHandler.handle(Future.succeededFuture(
        DeleteServicePointsByServicepointIdResponse
          .respond400WithTextPlain("Cannot delete service point, as it is in use")));
    } else {
      performServicePointDeletion(servicepointId, headers, asyncResultHandler);
    }
  }

  private void performServicePointDeletion(String servicepointId, Map<String, String> headers,
                                           Handler<AsyncResult<Response>> asyncResultHandler) {
    servicePointService.deleteServicePoint(servicepointId, headers)
      .onSuccess(deleted -> {
        if (TRUE.equals(deleted)) {
          asyncResultHandler.handle(Future.succeededFuture(
            DeleteServicePointsByServicepointIdResponse
              .respond204()));
        } else {
          asyncResultHandler.handle(Future.succeededFuture(
            DeleteServicePointsByServicepointIdResponse
              .respond404WithTextPlain("Not found")));
        }
      })
      .onFailure(throwable -> {
        String message = logAndSaveError(throwable);
        asyncResultHandler.handle(Future.succeededFuture(
          DeleteServicePointsByServicepointIdResponse
            .respond500WithTextPlain(getErrorResponse(message))));
      });
  }

  private String getErrorResponse(String response) {
    //Check to see if we're suppressing messages or not
    return response;
  }

  private String logAndSaveError(Throwable err) {
    String message = err.getLocalizedMessage();
    logger.error(message, err);
    return message;
  }

  private boolean isDuplicate(String errorMessage) {
    return errorMessage != null && errorMessage.contains(
      "duplicate key value violates unique constraint");
  }

  private Future<Boolean> checkServicePointInUse() {
    return Future.succeededFuture(false);
  }

  private static String updateGetServicePointsQuery(String query, boolean includeRoutingServicePoints) {
    if (includeRoutingServicePoints) {
      return query;
    }

    logger.debug("updateGetServicePointsQuery:: original query: {}", query);
    String newQuery = ECS_ROUTING_QUERY_FILTER;
    if (isNotBlank(query)) {
      newQuery += " and " + query;
    }
    logger.debug("updateGetServicePointsQuery:: updated query: {}", newQuery);

    return newQuery;
  }
}
