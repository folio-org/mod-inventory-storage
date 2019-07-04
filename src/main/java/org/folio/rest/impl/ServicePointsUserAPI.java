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
import org.folio.rest.jaxrs.model.ServicePointsUser;
import org.folio.rest.jaxrs.model.Servicepointsusers;
import org.folio.rest.jaxrs.resource.ServicePointsUsers;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;

public class ServicePointsUserAPI implements ServicePointsUsers {

  public static final Logger logger = LoggerFactory.getLogger(
          ServicePointsUserAPI.class);
  public static final String SERVICE_POINT_USER_TABLE = "service_point_user";
  public static final String LOCATION_PREFIX = "/service-points-users/";
  public static final String ID_FIELD = "'id'";

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

  private boolean isNotPresent(String errorMessage) {
    if(errorMessage != null && errorMessage.contains(
       "is not present in table")) {
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
  public void deleteServicePointsUsers(String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      String tenantId = getTenant(okapiHeaders);
      PostgresClient pgClient = getPGClient(vertxContext, tenantId);
      final String DELETE_ALL_QUERY = String.format("DELETE FROM %s_%s.%s",
          tenantId, "mod_inventory_storage", SERVICE_POINT_USER_TABLE);
      logger.info(String.format("Deleting all service points users with query %s",
          DELETE_ALL_QUERY));
      pgClient.execute(DELETE_ALL_QUERY, mutateReply -> {
        if(mutateReply.failed()) {
          String message = logAndSaveError(mutateReply.cause());
          asyncResultHandler.handle(Future.succeededFuture(
              DeleteServicePointsUsersResponse.respond500WithTextPlain(
              getErrorResponse(message))));
          } else {
            asyncResultHandler.handle(Future.succeededFuture(
                DeleteServicePointsUsersResponse.noContent().build()));
          }
        });
    } catch(Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
          DeleteServicePointsUsersResponse.respond500WithTextPlain(
          getErrorResponse(message))));
    }
  }

  @Override
  public void getServicePointsUsers(String query, int offset, int limit,
      String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      String tenantId = getTenant(okapiHeaders);
      PostgresClient pgClient = getPGClient(vertxContext, tenantId);
      CQLWrapper cql = getCQL(query, limit, offset, SERVICE_POINT_USER_TABLE);
      pgClient.get(SERVICE_POINT_USER_TABLE, ServicePointsUser.class,
          new String[]{"*"}, cql, true, true, getReply -> {
        if(getReply.failed()) {
          String message = logAndSaveError(getReply.cause());
          asyncResultHandler.handle(Future.succeededFuture(
              GetServicePointsUsersResponse.respond500WithTextPlain(
              getErrorResponse(message))));
        } else {
          List<ServicePointsUser> spuList = getReply.result().getResults();
          Servicepointsusers spus = new Servicepointsusers();
          spus.setServicePointsUsers(spuList);
          spus.setTotalRecords(getReply.result().getResultInfo().getTotalRecords());
          asyncResultHandler.handle(Future.succeededFuture(
              GetServicePointsUsersResponse.respond200WithApplicationJson(spus)));
        }
      });
    } catch(Exception e) {
      String message = logAndSaveError(e);
      if(isCQLError(e)) {
        message = String.format("CQL Error: %s", message);
      }
      asyncResultHandler.handle(Future.succeededFuture(
          GetServicePointsUsersResponse.respond500WithTextPlain(
          getErrorResponse(message))));
    }
  }

  @Override
  public void postServicePointsUsers(String lang, ServicePointsUser entity,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      String tenantId = getTenant(okapiHeaders);
      String id = entity.getId();
      if(id == null){
        id = UUID.randomUUID().toString();
        entity.setId(id);
      }
      PostgresClient pgClient = getPGClient(vertxContext, tenantId);
      pgClient.save(SERVICE_POINT_USER_TABLE, id, entity, saveReply -> {
        if(saveReply.failed()) {
          String message = logAndSaveError(saveReply.cause());
          if(isDuplicate(message)) {
            asyncResultHandler.handle(Future.succeededFuture(
                PostServicePointsUsersResponse.respond422WithApplicationJson(
                ValidationHelper.createValidationErrorMessage("userId",
                entity.getUserId(), "Service Point User Exists"))));
          } else if(isNotPresent(message)) {
            asyncResultHandler.handle(Future.succeededFuture(
                PostServicePointsUsersResponse.respond422WithApplicationJson(
                ValidationHelper.createValidationErrorMessage("userId",
                entity.getUserId(), "Referenced Service Point does not exist"))));
          } else {
            asyncResultHandler.handle(Future.succeededFuture(
                PostServicePointsUsersResponse.respond500WithTextPlain(
                getErrorResponse(message))));
          }
        } else {
          String ret = saveReply.result();
            entity.setId(ret);
            asyncResultHandler.handle(Future.succeededFuture(
                PostServicePointsUsersResponse
                  .respond201WithApplicationJson(entity,
                    PostServicePointsUsersResponse.headersFor201().withLocation(LOCATION_PREFIX + ret))));
        }
      });
    } catch(Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
          PostServicePointsUsersResponse.respond500WithTextPlain(
          getErrorResponse(message))));
    }
  }

  @Override
  public void getServicePointsUsersByServicepointsuserId(String servicepointsuserId,
      String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {
    PgUtil.getById(SERVICE_POINT_USER_TABLE, ServicePointsUser.class, servicepointsuserId, okapiHeaders, vertxContext,
        GetServicePointsUsersByServicepointsuserIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteServicePointsUsersByServicepointsuserId(String servicepointsuserId,
      String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(SERVICE_POINT_USER_TABLE, servicepointsuserId, okapiHeaders, vertxContext,
        DeleteServicePointsUsersByServicepointsuserIdResponse.class, asyncResultHandler);
  }

  @Override
  public void putServicePointsUsersByServicepointsuserId(String servicepointsuserId,
      String lang, ServicePointsUser entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(SERVICE_POINT_USER_TABLE, entity, servicepointsuserId, okapiHeaders, vertxContext,
        PutServicePointsUsersByServicepointsuserIdResponse.class, asyncResultHandler);
  }
}
