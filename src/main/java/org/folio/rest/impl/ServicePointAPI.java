package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.ws.rs.core.Response;

import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.HoldShelfExpiryPeriod;
import org.folio.rest.jaxrs.model.Servicepoint;
import org.folio.rest.jaxrs.model.Servicepoints;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;

/**
 *
 * @author kurt
 */
public class ServicePointAPI implements org.folio.rest.jaxrs.resource.ServicePoints {
  public static final Logger logger = LoggerFactory.getLogger(
          ServicePointAPI.class);
  public static final String SERVICE_POINT_TABLE = "service_point";
  public static final String LOCATION_PREFIX = "/service-points/";
  public static final String ID_FIELD = "'id'";
  public static final String SERVICE_POINT_CREATE_ERR_MSG_WITHOUT_HOLD_EXPIRY = "Hold shelf expiry period must be specified when service point can be used for pickup.";
  public static final String SERVICE_POINT_CREATE_ERR_MSG_WITHOUT_BEING_PICKUP_LOC = "Hold shelf expiry period cannot be specified when service point cannot be used for pickup";

  PostgresClient getPGClient(Context vertxContext, String tenantId) {
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

  private String getTenant(Map<String, String> headers)  {
    return TenantTool.calculateTenantId(headers.get(
            RestVerticle.OKAPI_HEADER_TENANT));
  }

  private CQLWrapper getCQL(String query, int limit, int offset,
          String tableName) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(tableName + ".jsonb");
    return new CQLWrapper(cql2pgJson, query).setLimit(new Limit(limit))
            .setOffset(new Offset(offset));
  }

  private boolean isDuplicate(String errorMessage){
    if(errorMessage != null && errorMessage.contains(
            "duplicate key value violates unique constraint")){
      return true;
    }
    return false;
  }

  private boolean isCQLError(Throwable err) {
    if(err.getCause() != null && err.getCause().getClass().getSimpleName()
            .endsWith("CQLParseException")) {
      return true;
    }
    return false;
  }

  @Override
  public void deleteServicePoints(String lang, Map<String, String> okapiHeaders,
          Handler<AsyncResult<Response>> asyncResultHandler,
          Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = getTenant(okapiHeaders);
        PostgresClient pgClient = getPGClient(vertxContext, tenantId);
        final String DELETE_ALL_QUERY = String.format("DELETE FROM %s_%s.%s",
                tenantId, "mod_inventory_storage", SERVICE_POINT_TABLE);
        logger.info(String.format("Deleting all service points with query %s",
                DELETE_ALL_QUERY));
        pgClient.execute(DELETE_ALL_QUERY, mutateReply -> {
          if(mutateReply.failed()) {
            String message = logAndSaveError(mutateReply.cause());
            asyncResultHandler.handle(Future.succeededFuture(
                    DeleteServicePointsResponse.respond500WithTextPlain(
                    getErrorResponse(message))));
          } else {
            asyncResultHandler.handle(Future.succeededFuture(
                    DeleteServicePointsResponse.noContent().build()));
          }
        });
      } catch(Exception e) {
        String message = logAndSaveError(e);
        asyncResultHandler.handle(Future.succeededFuture(
                DeleteServicePointsResponse.respond500WithTextPlain(
                getErrorResponse(message))));
      }
    });
  }

  @Override
  public void getServicePoints(String query, int offset, int limit, String lang,
          Map<String, String> okapiHeaders,
          Handler<AsyncResult<Response>> asyncResultHandler,
          Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = getTenant(okapiHeaders);
        PostgresClient pgClient = getPGClient(vertxContext, tenantId);
        CQLWrapper cql = getCQL(query, limit, offset, SERVICE_POINT_TABLE);
        pgClient.get(SERVICE_POINT_TABLE, Servicepoint.class, new String[]{"*"},
                cql, true, true, getReply -> {
          if(getReply.failed()) {
            String message = logAndSaveError(getReply.cause());
            asyncResultHandler.handle(Future.succeededFuture(
                    GetServicePointsResponse.respond500WithTextPlain(
                    getErrorResponse(message))));
          } else {
            Servicepoints servicepoints = new Servicepoints();
            List<Servicepoint> servicepointList = getReply.result().getResults();
            servicepoints.setServicepoints(servicepointList);
            servicepoints.setTotalRecords(getReply.result().getResultInfo()
                    .getTotalRecords());
            asyncResultHandler.handle(Future.succeededFuture(
                    GetServicePointsResponse.respond200WithApplicationJson(servicepoints)));
          }
        });
      } catch(Exception e) {
        String message = logAndSaveError(e);
        if(isCQLError(e)) {
          message = String.format("CQL Error: %s", message);
        }
        asyncResultHandler.handle(Future.succeededFuture(
                GetServicePointsResponse.respond500WithTextPlain(
                getErrorResponse(message))));
      }
    });
  }

  @Override
  public void postServicePoints(String lang, Servicepoint entity,
          Map<String, String> okapiHeaders,
          Handler<AsyncResult<Response>> asyncResultHandler,
          Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {

        String validateSvcptResult = validateServicePoint(entity);
        if (validateSvcptResult != null){
          asyncResultHandler.handle(Future.succeededFuture(
            PostServicePointsResponse.respond422WithApplicationJson(
              ValidationHelper.createValidationErrorMessage("name",
                entity.getName(), validateSvcptResult))));
          return;
        }

        String id = entity.getId();
        if(id == null) {
          id = UUID.randomUUID().toString();
          entity.setId(id);
        }
        String tenantId = getTenant(okapiHeaders);
        PostgresClient pgClient = getPGClient(vertxContext, tenantId);
        pgClient.save(SERVICE_POINT_TABLE, id, entity, saveReply -> {
          if(saveReply.failed()) {
            String message = logAndSaveError(saveReply.cause());
            if(isDuplicate(message)) {
              asyncResultHandler.handle(Future.succeededFuture(
                      PostServicePointsResponse.respond422WithApplicationJson(
                      ValidationHelper.createValidationErrorMessage("name",
                      entity.getName(), "Service Point Exists"))));
            } else {
              asyncResultHandler.handle(Future.succeededFuture(
                      PostServicePointsResponse.respond500WithTextPlain(
                      getErrorResponse(message))));
            }
          } else {
            String ret = saveReply.result();
            entity.setId(ret);
            asyncResultHandler.handle(Future.succeededFuture(
                    PostServicePointsResponse
                      .respond201WithApplicationJson(entity,
                        PostServicePointsResponse.headersFor201().withLocation((LOCATION_PREFIX + ret)))));
          }
        });
      } catch(Exception e) {
        String message = logAndSaveError(e);
        asyncResultHandler.handle(Future.succeededFuture(
                PostServicePointsResponse.respond500WithTextPlain(
                getErrorResponse(message))));
      }
    });
  }

  @Override
  public void getServicePointsByServicepointId(String servicepointId,
          String lang, Map<String, String> okapiHeaders,
          Handler<AsyncResult<Response>> asyncResultHandler,
          Context vertxContext) {

    PgUtil.getById(SERVICE_POINT_TABLE, Servicepoint.class, servicepointId, okapiHeaders, vertxContext,
        GetServicePointsByServicepointIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteServicePointsByServicepointId(String servicepointId,
          String lang, Map<String, String> okapiHeaders,
          Handler<AsyncResult<Response>> asyncResultHandler,
          Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = getTenant(okapiHeaders);
        PostgresClient pgClient = getPGClient(vertxContext, tenantId);
        checkServicepointInUse().onComplete(inUseRes -> {
          if(inUseRes.failed()) {
            String message = logAndSaveError(inUseRes.cause());
            asyncResultHandler.handle(Future.succeededFuture(
                    DeleteServicePointsByServicepointIdResponse
                    .respond500WithTextPlain(getErrorResponse(message))));
          } else if(inUseRes.result()) {
            asyncResultHandler.handle(Future.succeededFuture(
                    DeleteServicePointsByServicepointIdResponse
                    .respond400WithTextPlain("Cannot delete service point, as it is in use")));
          } else {
            pgClient.delete(SERVICE_POINT_TABLE, servicepointId, deleteReply -> {
              if(deleteReply.failed()) {
                String message = logAndSaveError(deleteReply.cause());
                asyncResultHandler.handle(Future.succeededFuture(
                        DeleteServicePointsByServicepointIdResponse
                        .respond500WithTextPlain(getErrorResponse(message))));
              } else {
                if(deleteReply.result().rowCount() == 0) {
                  asyncResultHandler.handle(Future.succeededFuture(
                          DeleteServicePointsByServicepointIdResponse
                          .respond404WithTextPlain("Not found")));
                } else {
                  asyncResultHandler.handle(Future.succeededFuture(
                          DeleteServicePointsByServicepointIdResponse
                          .respond204()));
                }
              }
            });
          }
        });
      } catch(Exception e) {
        String message = logAndSaveError(e);
        asyncResultHandler.handle(Future.succeededFuture(
                DeleteServicePointsByServicepointIdResponse
                .respond500WithTextPlain(getErrorResponse(message))));
      }
    });
  }

  @Override
  public void putServicePointsByServicepointId(String servicepointId,
          String lang, Servicepoint entity, Map<String, String> okapiHeaders,
          Handler<AsyncResult<Response>> asyncResultHandler,
          Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {

        String validateSvcptResult = validateServicePoint(entity) ;
        if (validateSvcptResult != null){
          asyncResultHandler.handle(Future.succeededFuture(
            PutServicePointsByServicepointIdResponse
              .respond422WithApplicationJson(ValidationHelper.createValidationErrorMessage("name",
                entity.getName(), validateSvcptResult))));
          return;
        }

        PgUtil.put(SERVICE_POINT_TABLE, entity, servicepointId, okapiHeaders, vertxContext,
            PutServicePointsByServicepointIdResponse.class, asyncResultHandler);

      } catch(Exception e) {
        String message = logAndSaveError(e);
        asyncResultHandler.handle(Future.succeededFuture(
                PutServicePointsByServicepointIdResponse
                .respond500WithTextPlain(getErrorResponse(message))));
      }
    });
  }

  private String validateServicePoint(Servicepoint svcpt){

    HoldShelfExpiryPeriod holdShelfExpiryPeriod = svcpt.getHoldShelfExpiryPeriod();
    Boolean pickupLocation = svcpt.getPickupLocation() == null ? Boolean.FALSE : svcpt.getPickupLocation();

    if (!pickupLocation && holdShelfExpiryPeriod != null ) {
      return SERVICE_POINT_CREATE_ERR_MSG_WITHOUT_BEING_PICKUP_LOC;
    } else if (pickupLocation && holdShelfExpiryPeriod == null) {
      return SERVICE_POINT_CREATE_ERR_MSG_WITHOUT_HOLD_EXPIRY;
    }
    return null;
  }

  private Future<Boolean> checkServicepointInUse() {
    return Future.succeededFuture(false);
  }

}
