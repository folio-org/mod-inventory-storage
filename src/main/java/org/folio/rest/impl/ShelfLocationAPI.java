/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.folio.rest.impl;

import java.util.Map;
import org.folio.rest.jaxrs.model.Shelflocation;
import org.folio.rest.jaxrs.resource.ShelfLocationsResource;

import javax.ws.rs.core.Response;
import io.vertx.core.Handler;
import io.vertx.core.Context;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import org.folio.rest.RestVerticle;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantTool;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.List;
import org.folio.rest.jaxrs.model.ShelflocationsJson;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.ValidationHelper;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;

/**
 *
 * @author kurt
 */
public class ShelfLocationAPI implements ShelfLocationsResource {
  public static final String SHELF_LOCATION_TABLE = "shelflocation";
  public static final Logger logger = LoggerFactory.getLogger(ShelfLocationAPI.class);
  public static final String URL_PREFIX = "/shelflocations";
  
  private String getErrorResponse(String response) {
    //Check to see if we're suppressing messages or not
    return response;
  }
  
  private String logAndSaveError(Throwable err) {
    String message = err.getLocalizedMessage();
    logger.error(message, err);
    return message;
  }
 
  private boolean isDuplicate(String message) {
    if(message != null && message.contains("duplicate key value violates unique constraint")) {
      return true;
    }
    return false;
  }
  
  private CQLWrapper getCQL(String query, int limit, int offset, String tableName) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(tableName + ".jsonb");
    return new CQLWrapper(cql2pgJson, query).setLimit(new Limit(limit)).setOffset(new Offset(offset));
  }
  
  private String getTenant(Map<String, String> headers)  {
    return TenantTool.calculateTenantId(headers.get(RestVerticle.OKAPI_HEADER_TENANT));
  }

  @Override
  public void deleteShelfLocations(
          String lang, 
          Map<String, String> okapiHeaders, 
          Handler<AsyncResult<Response>>asyncResultHandler, 
          Context vertxContext) 
          throws Exception{
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public void getShelfLocations(     
        String query,       
        int offset,   
        int limit,  
        String lang, 
        Map<String, String>okapiHeaders, 
        Handler<AsyncResult<Response>>asyncResultHandler, 
        Context vertxContext)
        throws Exception {
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = getTenant(okapiHeaders);
        CQLWrapper cql = getCQL(query, limit, offset, SHELF_LOCATION_TABLE);
        PostgresClient.getInstance(vertxContext.owner(), tenantId)
                .get(
                SHELF_LOCATION_TABLE, Shelflocation.class, new String[]{"*"},
                cql, true, true, reply -> {
          try {
            if(reply.failed()) {
              String message = logAndSaveError(reply.cause());
              asyncResultHandler.handle(Future.succeededFuture(
                      GetShelfLocationsResponse.withPlainBadRequest(
                              getErrorResponse(message))));
            } else {
              ShelflocationsJson shelflocations = new ShelflocationsJson();
              List<Shelflocation> shelflocationList = (List<Shelflocation>)reply.result()[0];
              shelflocations.setShelflocations(shelflocationList);
              shelflocations.setTotalRecords((Integer)reply.result()[1]);
              asyncResultHandler.handle(Future.succeededFuture(GetShelfLocationsResponse.withJsonOK(shelflocations)));
            }
          } catch(Exception e) {
            String message = logAndSaveError(e);
            asyncResultHandler.handle(Future.succeededFuture(
                    GetShelfLocationsResponse.withPlainInternalServerError(
                            getErrorResponse(message))));   
          }
        });
      } catch(Exception e) {
        String message = logAndSaveError(e);
        asyncResultHandler.handle(Future.succeededFuture(
                GetShelfLocationsResponse.withPlainInternalServerError(
                            getErrorResponse(message))));  
      }
    });
  }
  

  @Override
  public void postShelfLocations(
          String lang, 
          Shelflocation entity, 
          Map<String, String> okapiHeaders, 
          Handler<AsyncResult<Response>>asyncResultHandler, 
          Context vertxContext) 
          throws Exception {
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = getTenant(okapiHeaders);
        PostgresClient.getInstance(vertxContext.owner(), tenantId).save(SHELF_LOCATION_TABLE, entity, reply -> {
          try {
            if(reply.failed()) {
              String message = logAndSaveError(reply.cause());
              if(isDuplicate(message)) {
                asyncResultHandler.handle(Future.succeededFuture(
                        PostShelfLocationsResponse.withJsonUnprocessableEntity(
                                ValidationHelper.createValidationErrorMessage(
                                        "shelflocation", entity.getId(), 
                                        "Location already exists"))));
              } else {
                asyncResultHandler.handle(Future.succeededFuture(
                        PostShelfLocationsResponse.withPlainInternalServerError(
                                getErrorResponse(message))));
              }
            } else {
              Object responseObject = reply.result();
              entity.setId((String)responseObject);
              OutStream stream = new OutStream();
              stream.setData(entity);
              asyncResultHandler.handle(Future.succeededFuture(
                      PostShelfLocationsResponse.withJsonCreated(
                              URL_PREFIX + responseObject, stream)));
            }
          } catch(Exception e) {
            String message = logAndSaveError(e);
            asyncResultHandler.handle(Future.succeededFuture(
                    PostShelfLocationsResponse.withPlainInternalServerError(
                            getErrorResponse(message))));
          }
        });
      } catch(Exception e) {
        String message = logAndSaveError(e);
        asyncResultHandler.handle(Future.succeededFuture(
                PostShelfLocationsResponse.withPlainInternalServerError(
                        getErrorResponse(message))));
      }
    });
  }

  @Override
  public void getShelfLocationsByMaterialtypeId(
          String materialtypeId, 
          String lang, 
          Map<String, String> okapiHeaders, 
          Handler<AsyncResult<Response>>asyncResultHandler, 
          Context vertxContext) 
          throws Exception {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public void deleteShelfLocationsByMaterialtypeId(
          String materialtypeId, 
          String lang, Map<String, String> okapiHeaders, 
          Handler<AsyncResult<Response>>asyncResultHandler, 
          Context vertxContext) 
          throws Exception {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public void putShelfLocationsByMaterialtypeId(
          String materialtypeId, 
          String lang, 
          Shelflocation entity, 
          Map<String, String> okapiHeaders,
          Handler<AsyncResult<Response>>asyncResultHandler, 
          Context vertxContext) 
          throws Exception {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }
  
}
