package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static java.lang.Boolean.TRUE;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.folio.rest.support.EndpointFailureHandler.handleFailure;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import java.util.Map;
import java.util.UUID;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.RestVerticle;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.HoldShelfExpiryPeriod;
import org.folio.rest.jaxrs.model.Servicepoint;
import org.folio.rest.jaxrs.model.Servicepoints;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;
import org.folio.services.servicepoint.ServicePointService;

public class ServicePointApi implements org.folio.rest.jaxrs.resource.ServicePoints {
  public static final String SERVICE_POINT_TABLE = "service_point";
  public static final String LOCATION_PREFIX = "/service-points/";
  public static final String SERVICE_POINT_CREATE_ERR_MSG_WITHOUT_HOLD_EXPIRY =
    "Hold shelf expiry period must be specified when service point can be used for pickup.";
  public static final String SERVICE_POINT_CREATE_ERR_MSG_WITHOUT_BEING_PICKUP_LOC =
    "Hold shelf expiry period cannot be specified when service point cannot be used for pickup";
  private static final String ECS_ROUTING_QUERY_FILTER = "cql.allRecords=1 NOT ecsRequestRouting=true";
  private static final Logger logger = LogManager.getLogger();

  @Validate
  @Override
  public void getServicePoints(boolean includeRoutingServicePoints, String query,
                               String totalRecords, int offset, int limit,
                               Map<String, String> okapiHeaders,
                               Handler<AsyncResult<Response>> asyncResultHandler,
                               Context vertxContext) {

    query = updateGetServicePointsQuery(query, includeRoutingServicePoints);
    PgUtil.get(SERVICE_POINT_TABLE, Servicepoint.class, Servicepoints.class,
      query, offset, limit, okapiHeaders, vertxContext, GetServicePointsResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void postServicePoints(Servicepoint entity,
                                Map<String, String> okapiHeaders,
                                Handler<AsyncResult<Response>> asyncResultHandler,
                                Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {

        String validateSvcptResult = validateServicePoint(entity);
        if (validateSvcptResult != null) {
          asyncResultHandler.handle(Future.succeededFuture(
            PostServicePointsResponse.respond422WithApplicationJson(
              ValidationHelper.createValidationErrorMessage("name",
                entity.getName(), validateSvcptResult))));
          return;
        }

        String id = entity.getId();
        if (id == null) {
          id = UUID.randomUUID().toString();
          entity.setId(id);
        }
        new ServicePointService(vertxContext, okapiHeaders)
          .createServicePoint(id, entity)
          .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
          .onFailure(throwable -> {
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
          });
      } catch (Exception e) {
        String message = logAndSaveError(e);
        asyncResultHandler.handle(Future.succeededFuture(
          PostServicePointsResponse.respond500WithTextPlain(
            getErrorResponse(message))));
      }
    });
  }

  @Validate
  @Override
  public void deleteServicePoints(Map<String, String> okapiHeaders,
                                  Handler<AsyncResult<Response>> asyncResultHandler,
                                  Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = getTenant(okapiHeaders);
        PostgresClient pgClient = getPgClient(vertxContext, tenantId);
        final String deleteAllQuery = String.format("DELETE FROM %s_%s.%s",
          tenantId, "mod_inventory_storage", SERVICE_POINT_TABLE);
        logger.info("Deleting all service points with query {}", deleteAllQuery);
        pgClient.execute(deleteAllQuery, mutateReply -> {
          if (mutateReply.failed()) {
            String message = logAndSaveError(mutateReply.cause());
            asyncResultHandler.handle(Future.succeededFuture(
              DeleteServicePointsResponse.respond500WithTextPlain(getErrorResponse(message))));
          } else {
            asyncResultHandler.handle(Future.succeededFuture(DeleteServicePointsResponse.respond204()));
          }
        });
      } catch (Exception e) {
        String message = logAndSaveError(e);
        asyncResultHandler.handle(Future.succeededFuture(
          DeleteServicePointsResponse.respond500WithTextPlain(
            getErrorResponse(message))));
      }
    });
  }

  @Validate
  @Override
  public void putServicePointsByServicepointId(String servicepointId,
    Servicepoint entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    vertxContext.runOnContext(v -> {
      try {
        String validateSvcptResult = validateServicePoint(entity);
        if (validateSvcptResult != null) {
          asyncResultHandler.handle(Future.succeededFuture(
            PutServicePointsByServicepointIdResponse
              .respond422WithApplicationJson(ValidationHelper.createValidationErrorMessage("name",
                entity.getName(), validateSvcptResult))));
          return;
        }
        new ServicePointService(vertxContext, okapiHeaders)
          .updateServicePoint(servicepointId, entity)
          .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
          .onFailure(handleFailure(asyncResultHandler));
      } catch (Exception e) {
        String message = logAndSaveError(e);
        asyncResultHandler.handle(Future.succeededFuture(
          PutServicePointsByServicepointIdResponse
            .respond500WithTextPlain(getErrorResponse(message))));
      }
    });
  }

  @Validate
  @Override
  public void getServicePointsByServicepointId(String servicepointId,
                                               Map<String, String> okapiHeaders,
                                               Handler<AsyncResult<Response>> asyncResultHandler,
                                               Context vertxContext) {

    PgUtil.getById(SERVICE_POINT_TABLE, Servicepoint.class, servicepointId, okapiHeaders, vertxContext,
      GetServicePointsByServicepointIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteServicePointsByServicepointId(String servicepointId,
                                                  Map<String, String> okapiHeaders,
                                                  Handler<AsyncResult<Response>> asyncResultHandler,
                                                  Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        checkServicePointInUse().onComplete(inUseRes -> {
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
            new ServicePointService(vertxContext, okapiHeaders)
              .deleteServicePoint(servicepointId)
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
        });
      } catch (Exception e) {
        String message = logAndSaveError(e);
        asyncResultHandler.handle(Future.succeededFuture(
          DeleteServicePointsByServicepointIdResponse
            .respond500WithTextPlain(getErrorResponse(message))));
      }
    });
  }

  PostgresClient getPgClient(Context vertxContext, String tenantId) {
    return PostgresClient.getInstance(vertxContext.owner(), tenantId);
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

  private String getTenant(Map<String, String> headers) {
    return TenantTool.calculateTenantId(headers.get(
      RestVerticle.OKAPI_HEADER_TENANT));
  }

  private boolean isDuplicate(String errorMessage) {
    return errorMessage != null && errorMessage.contains(
      "duplicate key value violates unique constraint");
  }

  public static String validateServicePoint(Servicepoint svcpt) {

    HoldShelfExpiryPeriod holdShelfExpiryPeriod = svcpt.getHoldShelfExpiryPeriod();
    boolean pickupLocation = svcpt.getPickupLocation() == null ? Boolean.FALSE : svcpt.getPickupLocation();

    if (!pickupLocation && holdShelfExpiryPeriod != null) {
      return SERVICE_POINT_CREATE_ERR_MSG_WITHOUT_BEING_PICKUP_LOC;
    } else if (pickupLocation && holdShelfExpiryPeriod == null) {
      return SERVICE_POINT_CREATE_ERR_MSG_WITHOUT_HOLD_EXPIRY;
    }
    return null;
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
