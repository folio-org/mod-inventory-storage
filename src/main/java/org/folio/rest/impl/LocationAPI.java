package org.folio.rest.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Response;

import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.Locations;
import org.folio.rest.jaxrs.resource.LocationsResource;

/**
 *
 * @author kurt
 */
public class LocationAPI implements LocationsResource {
  private final Messages messages = Messages.getInstance();
  public static final String LOCATION_TABLE = "location";
  public static final Logger logger = LoggerFactory.getLogger(LocationAPI.class);
  public static final String URL_PREFIX = "/locations";
  public static final String SHELF_LOCATION_SCHEMA_PATH = "apidocs/raml/location.json";
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
  public void deleteLocations(          String lang,
          Map<String, String> okapiHeaders,
          Handler<AsyncResult<Response>>asyncResultHandler,
          Context vertxContext)
          throws Exception{
     String tenantId = TenantTool.tenantId(okapiHeaders);

    try {
      PostgresClient postgresClient = PostgresClient.getInstance(
        vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

      postgresClient.mutate(String.format("DELETE FROM %s_%s.%s",
        tenantId, "mod_inventory_storage", LOCATION_TABLE),
        reply -> {
          if (reply.succeeded()) {
            asyncResultHandler.handle(Future.succeededFuture(
              LocationsResource.DeleteLocationsResponse.noContent()
                .build()));
          } else {
            asyncResultHandler.handle(Future.succeededFuture(
              LocationsResource.DeleteLocationsResponse.
                withPlainInternalServerError(reply.cause().getMessage())));
          }
        });
    }    catch(Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(
        LocationsResource.DeleteLocationsResponse.
          withPlainInternalServerError(e.getMessage())));
    }

  }

  @Override
  public void getLocations(        String query,
        int offset,
        int limit,
        String lang,
        Map<String, String>okapiHeaders,
        Handler<AsyncResult<Response>>asyncResultHandler,
        Context vertxContext)
        throws Exception {
    try {
      String tenantId = getTenant(okapiHeaders);
      CQLWrapper cql = getCQL(query, limit, offset, LOCATION_TABLE);
      PostgresClient.getInstance(vertxContext.owner(), tenantId)
        .get(
          LOCATION_TABLE, Location.class,
          new String[]{"*"}, cql, true, true, reply -> {
            try {
              if (reply.failed()) {
                String message = logAndSaveError(reply.cause());
                asyncResultHandler.handle(Future.succeededFuture(
                  GetLocationsResponse.withPlainBadRequest(getErrorResponse(message))));
              } else {
                Locations shelfLocations = new Locations();
                List<Location> shelfLocationsList = (List<Location>) reply.result().getResults();
                shelfLocations.setLocations(shelfLocationsList);
                shelfLocations.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
                asyncResultHandler.handle(Future.succeededFuture(
                  GetLocationsResponse.withJsonOK(shelfLocations)));
              }
            } catch (Exception e) {
              String message = logAndSaveError(e);
              asyncResultHandler.handle(Future.succeededFuture(
                GetLocationsResponse.withPlainInternalServerError(getErrorResponse(message))));
            }
          });
    } catch (Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
        GetLocationsResponse.withPlainInternalServerError(
          getErrorResponse(message))));
    }
  }


  @Override
  public void postLocations(          String lang,
    Location entity,          Map<String, String> okapiHeaders,
          Handler<AsyncResult<Response>>asyncResultHandler,
          Context vertxContext)
          throws Exception {
    try {
      String tenantId = getTenant(okapiHeaders);
      String id = entity.getId();
      if (id == null) {
        id = UUID.randomUUID().toString();
        entity.setId(id);
      }
      PostgresClient.getInstance(vertxContext.owner(), tenantId)
        .save(LOCATION_TABLE, id, entity, reply -> {
        try {
          if (reply.failed()) {
            String message = logAndSaveError(reply.cause());
            if (isDuplicate(message)) {
              asyncResultHandler.handle(Future.succeededFuture(
                PostLocationsResponse.withJsonUnprocessableEntity(
                  ValidationHelper.createValidationErrorMessage(
                    "shelflocation", entity.getId(),
                    "Location already exists"))));
            } else {
              asyncResultHandler.handle(Future.succeededFuture(
                PostLocationsResponse.withPlainInternalServerError(getErrorResponse(message))));
            }
          } else {
            Object responseObject = reply.result();
            entity.setId((String) responseObject);
            OutStream stream = new OutStream();
            stream.setData(entity);
            asyncResultHandler.handle(Future.succeededFuture(
              PostLocationsResponse.withJsonCreated(URL_PREFIX + responseObject, stream)));
          }
        } catch (Exception e) {
          String message = logAndSaveError(e);
          asyncResultHandler.handle(Future.succeededFuture(
            PostLocationsResponse.withPlainInternalServerError(getErrorResponse(message))));
        }
      });
    } catch (Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
        PostLocationsResponse.withPlainInternalServerError(getErrorResponse(message))));
    }
  }

  @Override
  public void getLocationsById(          String id,
          String lang,
          Map<String, String> okapiHeaders,
          Handler<AsyncResult<Response>>asyncResultHandler,
          Context vertxContext)
          throws Exception {
    try {
      String tenantId = getTenant(okapiHeaders);
      Criteria criteria = new Criteria(SHELF_LOCATION_SCHEMA_PATH);
      criteria.addField(ID_FIELD_NAME);
      criteria.setOperation("=");
      criteria.setValue(id);
      Criterion criterion = new Criterion(criteria);
      PostgresClient.getInstance(vertxContext.owner(), tenantId).get(
        LOCATION_TABLE, Location.class, criterion, true, false, getReply -> {
          if (getReply.failed()) {
            String message = logAndSaveError(getReply.cause());
            asyncResultHandler.handle(Future.succeededFuture(
              GetLocationsByIdResponse.withPlainInternalServerError(getErrorResponse(message))));
          } else {
            List<Location> locationList = (List<Location>) getReply.result().getResults();
            if (locationList.size() < 1) {
              asyncResultHandler.handle(Future.succeededFuture(
                GetLocationsByIdResponse.withPlainNotFound(messages.getMessage(lang, MessageConsts.ObjectDoesNotExist))));
            } else if (locationList.size() > 1) {
              String message = "Multiple locations found with the same id";
              logger.error(message);
              asyncResultHandler.handle(Future.succeededFuture(
                GetLocationsByIdResponse.withPlainInternalServerError(getErrorResponse(message))));
            } else {
              asyncResultHandler.handle(Future.succeededFuture(GetLocationsByIdResponse.withJsonOK(locationList.get(0))));
            }
          }
        });
    } catch (Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
        GetLocationsByIdResponse.withPlainInternalServerError(getErrorResponse(message))));
    }
  }

  @Override
  public void deleteLocationsById(          String id,
          String lang, Map<String, String> okapiHeaders,
          Handler<AsyncResult<Response>>asyncResultHandler,
          Context vertxContext)
          throws Exception {
    try {
      String tenantId = getTenant(okapiHeaders);
      Criteria criteria = new Criteria(SHELF_LOCATION_SCHEMA_PATH);
      criteria.addField(ID_FIELD_NAME);
      criteria.setOperation("=");
      criteria.setValue(id);
      Criterion criterion = new Criterion(criteria);
      try {
        locationInUse(id, tenantId, vertxContext).setHandler(res -> {
          if (res.failed()) {
            String message = logAndSaveError(res.cause());
            DeleteLocationsByIdResponse.withPlainInternalServerError(getErrorResponse(message));
          } else {
            if (res.result()) {
              asyncResultHandler.handle(Future.succeededFuture(
                DeleteLocationsByIdResponse.withPlainBadRequest("Cannot delete location, as it is in use")));
            } else {
              try {
                PostgresClient.getInstance(vertxContext.owner(), tenantId).delete(LOCATION_TABLE, criterion, deleteReply -> {
                  if (deleteReply.failed()) {
                    String message = logAndSaveError(deleteReply.cause());
                    asyncResultHandler.handle(Future.succeededFuture(
                      DeleteLocationsByIdResponse.withPlainNotFound("Not found")));
                  } else {
                    asyncResultHandler.handle(Future.succeededFuture(
                      DeleteLocationsByIdResponse.withNoContent()));
                  }
                });
              } catch (Exception e) {
                String message = logAndSaveError(e);
                asyncResultHandler.handle(Future.succeededFuture(
                  DeleteLocationsByIdResponse.withPlainInternalServerError(getErrorResponse(message))));
              }
            }
          }
        });
      } catch (Exception e) {
        String message = logAndSaveError(e);
        asyncResultHandler.handle(Future.succeededFuture(
          DeleteLocationsByIdResponse.withPlainInternalServerError(getErrorResponse(message))));
      }
    } catch (Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
        DeleteLocationsByIdResponse.withPlainInternalServerError(getErrorResponse(message))));
    }
  }

  @Override
  public void putLocationsById(          String id,
          String lang,
    Location entity,          Map<String, String> okapiHeaders,
          Handler<AsyncResult<Response>>asyncResultHandler,
          Context vertxContext)
          throws Exception {
    try {
      if (!id.equals(entity.getId())) {
        String message = "Illegal operation: id cannot be changed";
        asyncResultHandler.handle(Future.succeededFuture(
          PutLocationsByIdResponse.withPlainBadRequest(message)));
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
          LOCATION_TABLE, entity, criterion, false, updateReply -> {
            if (updateReply.failed()) {
              String message = logAndSaveError(updateReply.cause());
              asyncResultHandler.handle(Future.succeededFuture(
                PutLocationsByIdResponse.withPlainInternalServerError(getErrorResponse(message))));
            } else {
              if (updateReply.result().getUpdated() == 0) {
                asyncResultHandler.handle(Future.succeededFuture(
                  PutLocationsByIdResponse.withPlainNotFound("Not found")));
                //Not found
              } else {
                asyncResultHandler.handle(Future.succeededFuture(
                  PutLocationsByIdResponse.withNoContent()));
              }
            }
          });
      } catch (Exception e) {
        String message = logAndSaveError(e);
        asyncResultHandler.handle(Future.succeededFuture(
          PutLocationsByIdResponse.withPlainInternalServerError(getErrorResponse(message))));
      }
    } catch (Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
        PutLocationsByIdResponse.withPlainInternalServerError(getErrorResponse(message))));
    }
  }

  Future<Boolean> locationInUse(String locationId, String tenantId, Context vertxContext) {
    Future<Boolean> future = Future.future();
    //Get all items where the temporary future or permanent future is this location id
    String query = "permanentLocation == " + locationId + " OR temporarylocation == " + locationId;
    try {
      CQLWrapper cql = getCQL(query, 10, 0, ItemStorageAPI.ITEM_TABLE);
      String[] fieldList = {"*"};
      PostgresClient.getInstance(vertxContext.owner(), tenantId).get(
        ItemStorageAPI.ITEM_TABLE, Item.class, fieldList, cql, true, false,
        getReply -> {
          if (getReply.failed()) {
            future.fail(getReply.cause());
          } else {
            List<Item> itemList = (List<Item>) getReply.result().getResults();
            if (itemList.isEmpty()) {
              future.complete(false);
            } else {
              future.complete(true);
            }
          }
        });
    } catch (Exception e) {
      future.fail(e);
    }
    return future;
  }
}
