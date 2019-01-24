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
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.HoldShelfExpiryPeriod;
import org.folio.rest.jaxrs.model.Servicepoint;
import org.folio.rest.jaxrs.model.Servicepoints;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;

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
  public static final String SERVICE_POINT_CREATE_ERR_MSG_WITHOUT_BEING_PICKUP_LOC= "Hold shelf expiry period cannot be specified when service point cannot be used for pickup";

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
        pgClient.mutate(DELETE_ALL_QUERY, mutateReply -> {
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

        String validateSvcptResult = validateServicePoint(entity) ;
        if (validateSvcptResult != null && !validateSvcptResult.isEmpty()){
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
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = getTenant(okapiHeaders);
        PostgresClient pgClient = getPGClient(vertxContext, tenantId);
        Criteria idCrit = new Criteria()
                .addField(ID_FIELD)
                .setOperation("=")
                .setValue(servicepointId);
        pgClient.get(SERVICE_POINT_TABLE, Servicepoint.class,
                new Criterion(idCrit), true, false, getReply -> {
          if(getReply.failed()) {
            String message = logAndSaveError(getReply.cause());
            asyncResultHandler.handle(Future.succeededFuture(
                    GetServicePointsByServicepointIdResponse
                    .respond500WithTextPlain(getErrorResponse(message))));
          } else {
            List<Servicepoint> servicepointList = getReply.result().getResults();
            if(servicepointList.isEmpty()) {
              asyncResultHandler.handle(Future.succeededFuture(
                      GetServicePointsByServicepointIdResponse
                      .respond404WithTextPlain(String.format(
                      "No service point exists with id '%s'", servicepointId))));
            } else {
              Servicepoint servicepoint = servicepointList.get(0);
              asyncResultHandler.handle(Future.succeededFuture(
                      GetServicePointsByServicepointIdResponse.respond200WithApplicationJson(
                      servicepoint)));
            }
          }
        });
      } catch(Exception e) {
        String message = logAndSaveError(e);
        asyncResultHandler.handle(Future.succeededFuture(
                GetServicePointsByServicepointIdResponse
                .respond500WithTextPlain(getErrorResponse(message))));
      }
    });
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
        checkServicepointInUse().setHandler(inUseRes -> {
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
                if(deleteReply.result().getUpdated() == 0) {
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
        if (validateSvcptResult != null && !validateSvcptResult.isEmpty()){
          asyncResultHandler.handle(Future.succeededFuture(
            PutServicePointsByServicepointIdResponse
              .respond422WithApplicationJson(ValidationHelper.createValidationErrorMessage("name",
                entity.getName(), validateSvcptResult))));
          return;
        }

        String tenantId = getTenant(okapiHeaders);
        PostgresClient pgClient = getPGClient(vertxContext, tenantId);
        Criteria idCrit = new Criteria()
                .addField(ID_FIELD)
                .setOperation("=")
                .setValue(servicepointId);
        pgClient.update(SERVICE_POINT_TABLE, entity, new Criterion(idCrit),
                false, updateReply -> {
          if(updateReply.failed()) {
            String message = logAndSaveError(updateReply.cause());
            asyncResultHandler.handle(Future.succeededFuture(
                    PutServicePointsByServicepointIdResponse
                    .respond500WithTextPlain(getErrorResponse(message))));
          } else if(updateReply.result().getUpdated() == 0) {
            asyncResultHandler.handle(Future.succeededFuture(
                    PutServicePointsByServicepointIdResponse
                    .respond404WithTextPlain("Not found")));
          } else {
            asyncResultHandler.handle(Future.succeededFuture(
                    PutServicePointsByServicepointIdResponse
                    .respond204()));
          }
        });
      } catch(Exception e) {
        String message = logAndSaveError(e);
        asyncResultHandler.handle(Future.succeededFuture(
                PutServicePointsByServicepointIdResponse
                .respond500WithTextPlain(getErrorResponse(message))));
      }
    });
  }

  public void putServicePoints(Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext){
    throw new UnsupportedOperationException();
  }

  private String validateServicePoint(Servicepoint svcpt){

    HoldShelfExpiryPeriod holdShelfExpiryPeriod = svcpt.getHoldShelfExpiryPeriod();
    Boolean pickupLocation = svcpt.getPickupLocation() == null ? false: svcpt.getPickupLocation();

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
