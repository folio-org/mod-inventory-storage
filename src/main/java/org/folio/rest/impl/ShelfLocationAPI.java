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
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.Shelflocation;
import org.folio.rest.jaxrs.model.Shelflocations;
import org.folio.rest.jaxrs.resource.ShelfLocationsResource;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 *
 * @author kurt
 */
public class ShelfLocationAPI implements ShelfLocationsResource {
  private final Messages messages = Messages.getInstance();
  public static final String SHELF_LOCATION_TABLE = "shelflocation";
  public static final Logger logger = LoggerFactory.getLogger(ShelfLocationAPI.class);
  public static final String URL_PREFIX = "/shelflocations";
  public static final String SHELF_LOCATION_SCHEMA_PATH = "apidocs/raml/shelflocation.json";
  public static final String ID_FIELD_NAME = "'id'";

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
     String tenantId = TenantTool.tenantId(okapiHeaders);

    try {
      vertxContext.runOnContext(v -> {
        PostgresClient postgresClient = PostgresClient.getInstance(
          vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

        postgresClient.mutate(String.format("DELETE FROM %s_%s.%s",
          tenantId, "mod_inventory_storage", SHELF_LOCATION_TABLE),
          reply -> {
            if (reply.succeeded()) {
              asyncResultHandler.handle(Future.succeededFuture(
                ShelfLocationsResource.DeleteShelfLocationsResponse.noContent()
                  .build()));
            } else {
              asyncResultHandler.handle(Future.succeededFuture(
                ShelfLocationsResource.DeleteShelfLocationsResponse.
                  withPlainInternalServerError(reply.cause().getMessage())));
            }
          });
      });
    }
    catch(Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(
        ShelfLocationsResource.DeleteShelfLocationsResponse.
          withPlainInternalServerError(e.getMessage())));
    }

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
              Shelflocations shelfLocations = new Shelflocations();
              List<Shelflocation> shelfLocationsList = (List<Shelflocation>)reply.result()[0];
              shelfLocations.setShelflocations(shelfLocationsList);
              shelfLocations.setTotalRecords((Integer)reply.result()[1]);
              asyncResultHandler.handle(Future.succeededFuture(GetShelfLocationsResponse.withJsonOK(shelfLocations)));
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
        String id = entity.getId();
        if(id == null) {
          id = UUID.randomUUID().toString();
          entity.setId(id);
        }
        PostgresClient.getInstance(vertxContext.owner(), tenantId).save(SHELF_LOCATION_TABLE, id, entity, reply -> {
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
  public void getShelfLocationsById(
          String id,
          String lang,
          Map<String, String> okapiHeaders,
          Handler<AsyncResult<Response>>asyncResultHandler,
          Context vertxContext)
          throws Exception {
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = getTenant(okapiHeaders);
        Criteria criteria = new Criteria(SHELF_LOCATION_SCHEMA_PATH);
        criteria.addField(ID_FIELD_NAME);
        criteria.setOperation("=");
        criteria.setValue(id);
        Criterion criterion = new Criterion(criteria);
        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(
                SHELF_LOCATION_TABLE, Shelflocation.class, criterion, true,
                false, getReply -> {
          if(getReply.failed()) {
            String message = logAndSaveError(getReply.cause());
            asyncResultHandler.handle(Future.succeededFuture(
                      GetShelfLocationsByIdResponse.withPlainInternalServerError(
                              getErrorResponse(message))));
          } else {
            List<Shelflocation> locationList = (List<Shelflocation>)getReply.result()[0];
            if(locationList.size() < 1) {
              asyncResultHandler.handle(Future.succeededFuture(
                      GetShelfLocationsByIdResponse.withPlainNotFound(
                              messages.getMessage(lang, MessageConsts.ObjectDoesNotExist))));
            } else if(locationList.size() > 1) {
              String message = "Multiple locations found with the same id";
              logger.error(message);
              asyncResultHandler.handle(Future.succeededFuture(
                      GetShelfLocationsByIdResponse.withPlainInternalServerError(
                              getErrorResponse(message))));
            } else {
              asyncResultHandler.handle(Future.succeededFuture(GetShelfLocationsByIdResponse.withJsonOK(locationList.get(0))));
            }
          }
        });
      } catch(Exception e) {
        String message = logAndSaveError(e);
        asyncResultHandler.handle(Future.succeededFuture(
              GetShelfLocationsByIdResponse.withPlainInternalServerError(
                  getErrorResponse(message))));
      }
    });
  }

  @Override
  public void deleteShelfLocationsById(
          String id,
          String lang, Map<String, String> okapiHeaders,
          Handler<AsyncResult<Response>>asyncResultHandler,
          Context vertxContext)
          throws Exception {
     vertxContext.runOnContext(v -> {
      try {
        String tenantId = getTenant(okapiHeaders);
        Criteria criteria = new Criteria(SHELF_LOCATION_SCHEMA_PATH);
        criteria.addField(ID_FIELD_NAME);
        criteria.setOperation("=");
        criteria.setValue(id);
        Criterion criterion = new Criterion(criteria);
        try {
          locationInUse(id, tenantId, vertxContext).setHandler(res -> {
            if(res.failed()) {
              String message = logAndSaveError(res.cause());
              DeleteShelfLocationsByIdResponse.withPlainInternalServerError(
                      getErrorResponse(message));
            } else {
              if(res.result()) {
                asyncResultHandler.handle(Future.succeededFuture(
                        DeleteShelfLocationsByIdResponse.withPlainBadRequest(
                                "Cannot delete location, as it is in use")));
              } else {
                try {
                  PostgresClient.getInstance(vertxContext.owner(), tenantId).delete(SHELF_LOCATION_TABLE, criterion, deleteReply -> {
                    if(deleteReply.failed()) {
                      String message = logAndSaveError(deleteReply.cause());
                      asyncResultHandler.handle(Future.succeededFuture(
                              DeleteShelfLocationsByIdResponse.withPlainNotFound("Not found")));
                    } else {
                      asyncResultHandler.handle(Future.succeededFuture(
                              DeleteShelfLocationsByIdResponse.withNoContent()));
                    }
                  });
                } catch(Exception e) {
                  String message = logAndSaveError(e);
                  asyncResultHandler.handle(Future.succeededFuture(
                    DeleteShelfLocationsByIdResponse.withPlainInternalServerError(
                            getErrorResponse(message))));
                }
              }
            }
          });
        } catch(Exception e) {
          String message = logAndSaveError(e);
          asyncResultHandler.handle(Future.succeededFuture(
                      DeleteShelfLocationsByIdResponse.withPlainInternalServerError(
                              getErrorResponse(message))));
        }
      } catch(Exception e) {
        String message = logAndSaveError(e);
        asyncResultHandler.handle(Future.succeededFuture(
                    DeleteShelfLocationsByIdResponse.withPlainInternalServerError(
                            getErrorResponse(message))));
      }
    });
  }

  @Override
  public void putShelfLocationsById(
          String id,
          String lang,
          Shelflocation entity,
          Map<String, String> okapiHeaders,
          Handler<AsyncResult<Response>>asyncResultHandler,
          Context vertxContext)
          throws Exception {
    vertxContext.runOnContext(v -> {
      try {
        if(!id.equals(entity.getId())) {
          String message = "Illegal operation: id cannot be changed";
          asyncResultHandler.handle(Future.succeededFuture(
                      PutShelfLocationsByIdResponse.withPlainBadRequest(message)));
          return;
        }
        String tenantId = getTenant(okapiHeaders);
        Criteria criteria = new Criteria(SHELF_LOCATION_SCHEMA_PATH);
        criteria.addField(ID_FIELD_NAME);
        criteria.setOperation("=");
        criteria.setValue(id);
        Criterion criterion = new Criterion(criteria);
        try {
          PostgresClient.getInstance(vertxContext.owner(), tenantId).update(
                  SHELF_LOCATION_TABLE, entity, criterion, false, updateReply -> {
            if(updateReply.failed()) {
              String message = logAndSaveError(updateReply.cause());
              asyncResultHandler.handle(Future.succeededFuture(
                      PutShelfLocationsByIdResponse.withPlainInternalServerError(
                              getErrorResponse(message))));
            } else {
              if(updateReply.result().getUpdated() == 0) {
                asyncResultHandler.handle(Future.succeededFuture(
                      PutShelfLocationsByIdResponse.withPlainNotFound("Not found")));
              //Not found
              } else {
                asyncResultHandler.handle(Future.succeededFuture(
                      PutShelfLocationsByIdResponse.withNoContent()));
              }
            }
          });
        } catch(Exception e) {
          String message = logAndSaveError(e);
          asyncResultHandler.handle(Future.succeededFuture(
                      PutShelfLocationsByIdResponse.withPlainInternalServerError(
                              getErrorResponse(message))));
        }
      } catch(Exception e) {
        String message = logAndSaveError(e);
        asyncResultHandler.handle(Future.succeededFuture(
                    PutShelfLocationsByIdResponse.withPlainInternalServerError(
                            getErrorResponse(message))));
      }
    });
  }

  Future<Boolean> locationInUse(String locationId, String tenantId, Context vertxContext) {
    Future<Boolean> future = Future.future();
    //Get all items where the temporary future or permanent future is this location id
    String query = "permanentLocation == " + locationId + " OR temporarylocation == " + locationId;
    vertxContext.runOnContext(v -> {
      try {
        CQLWrapper cql = getCQL(query, 10, 0, ItemStorageAPI.ITEM_TABLE);
        String[] fieldList = {"*"};
        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(
                ItemStorageAPI.ITEM_TABLE, Item.class, fieldList, cql, true, false,
                getReply -> {
          if(getReply.failed()) {
            future.fail(getReply.cause());
          } else {
            List<Item> itemList = (List<Item>)getReply.result()[0];
            if(itemList.isEmpty()) {
              future.complete(false);
            } else {
              future.complete(true);
            }
          }
        });
      } catch(Exception e) {
        future.fail(e);
      }
    });
    return future;
  }
}
