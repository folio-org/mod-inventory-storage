/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
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
import org.folio.rest.jaxrs.model.Servicepoint;
import org.folio.rest.jaxrs.model.Servicepoints;
import org.folio.rest.jaxrs.resource.ServicePointsResource;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;

/**
 *
 * @author kurt
 */
public class ServicePointAPI implements ServicePointsResource {
  public static final Logger logger = LoggerFactory.getLogger(
          ServicePointAPI.class);
  public static final String SERVICE_POINT_TABLE = "service_point";
  public static final String LOCATION_PREFIX = "/service-points/";
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
          Context vertxContext) throws Exception {
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
                    DeleteServicePointsResponse.withPlainInternalServerError(
                    getErrorResponse(message))));
          } else {
            asyncResultHandler.handle(Future.succeededFuture(
                    DeleteServicePointsResponse.noContent().build()));
          }
        });
      } catch(Exception e) {
        String message = logAndSaveError(e);
        asyncResultHandler.handle(Future.succeededFuture(
                DeleteServicePointsResponse.withPlainInternalServerError(
                getErrorResponse(message))));
      }
    });
  }

  @Override
  public void getServicePoints(String query, int offset, int limit, String lang,
          Map<String, String> okapiHeaders,
          Handler<AsyncResult<Response>> asyncResultHandler,
          Context vertxContext) throws Exception {
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
                    GetServicePointsResponse.withPlainInternalServerError(
                    getErrorResponse(message))));
          } else {
            Servicepoints servicepoints = new Servicepoints();
            List<Servicepoint> servicepointList = (List<Servicepoint>) getReply
                    .result().getResults();
            servicepoints.setServicepoints(servicepointList);
            servicepoints.setTotalRecords(getReply.result().getResultInfo()
                    .getTotalRecords());
            asyncResultHandler.handle(Future.succeededFuture(
                    GetServicePointsResponse.withJsonOK(servicepoints)));
          }
        });        
      } catch(Exception e) {
        String message = logAndSaveError(e);
        if(isCQLError(e)) {
          message = String.format("CQL Error: %s", message);
        }
        asyncResultHandler.handle(Future.succeededFuture(
                GetServicePointsResponse.withPlainInternalServerError(
                getErrorResponse(message))));
      }
    });
  }

  @Override
  public void postServicePoints(String lang, Servicepoint entity,
          Map<String, String> okapiHeaders,
          Handler<AsyncResult<Response>> asyncResultHandler,
          Context vertxContext) throws Exception {
    vertxContext.runOnContext(v -> {
      try {
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
                      PostServicePointsResponse.withJsonUnprocessableEntity(
                      ValidationHelper.createValidationErrorMessage("name",
                      entity.getName(), "Service Point Exists"))));
            } else {
              asyncResultHandler.handle(Future.succeededFuture(
                      PostServicePointsResponse.withPlainInternalServerError(
                      getErrorResponse(message))));
            }
          } else {
            String ret = saveReply.result();
            entity.setId(ret);
            OutStream stream = new OutStream();
            stream.setData(entity);
            asyncResultHandler.handle(Future.succeededFuture(
                    PostServicePointsResponse.withJsonCreated(LOCATION_PREFIX 
                    + ret, stream)));
          }
        });
      } catch(Exception e) {
        String message = logAndSaveError(e);
        asyncResultHandler.handle(Future.succeededFuture(
                PostServicePointsResponse.withPlainInternalServerError(
                getErrorResponse(message))));
      }
    });
  }

  @Override
  public void getServicePointsByServicepointId(String servicepointId,
          String lang, Map<String, String> okapiHeaders,
          Handler<AsyncResult<Response>> asyncResultHandler,
          Context vertxContext) throws Exception {
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
                    .withPlainInternalServerError(getErrorResponse(message))));
          } else {
            List<Servicepoint> servicepointList = (List<Servicepoint>) getReply
                    .result().getResults();
            if(servicepointList.isEmpty()) {
              asyncResultHandler.handle(Future.succeededFuture(
                      GetServicePointsByServicepointIdResponse
                      .withPlainNotFound(String.format(
                      "No service point exists with id '%s'", servicepointId))));
            } else {
              Servicepoint servicepoint = servicepointList.get(0);
              asyncResultHandler.handle(Future.succeededFuture(
                      GetServicePointsByServicepointIdResponse.withJsonOK(
                      servicepoint)));
            }
          }
        });        
      } catch(Exception e) {
        String message = logAndSaveError(e);
        asyncResultHandler.handle(Future.succeededFuture(
                GetServicePointsByServicepointIdResponse
                .withPlainInternalServerError(getErrorResponse(message))));
      }
    });
  }

  @Override
  public void deleteServicePointsByServicepointId(String servicepointId,
          String lang, Map<String, String> okapiHeaders,
          Handler<AsyncResult<Response>> asyncResultHandler,
          Context vertxContext) throws Exception {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public void putServicePointsByServicepointId(String servicepointId,
          String lang, Servicepoint entity, Map<String, String> okapiHeaders,
          Handler<AsyncResult<Response>> asyncResultHandler,
          Context vertxContext) throws Exception {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }
  
}
