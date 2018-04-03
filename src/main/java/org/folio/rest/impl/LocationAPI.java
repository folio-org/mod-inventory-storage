package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.Locations;
import org.folio.rest.jaxrs.resource.LocationsResource;
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
public class LocationAPI implements LocationsResource {
  private final Messages messages = Messages.getInstance();
  public static final String LOCATION_TABLE = "location";
  private final Logger logger = LoggerFactory.getLogger(LocationAPI.class);
  public static final String URL_PREFIX = "/locations";
  public static final String LOCATION_SCHEMA_PATH = "apidocs/raml/location.json";
  public static final String ID_FIELD_NAME = "'id'";

  private String logAndSaveError(Throwable err) {
    String message = err.getLocalizedMessage();
    logger.error(message, err);
    return message;
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
  {
    String tenantId = TenantTool.tenantId(okapiHeaders);
    PostgresClient.getInstance(vertxContext.owner(), TenantTool.calculateTenantId(tenantId))
      .mutate(String.format("DELETE FROM %s_%s.%s",
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
  }

  // Note, this is the way to get rid of unnecessary try-catch blocks. Use the
  // same everywhere!
  @Override
  public void getLocations(
    String query,
    int offset,
    int limit,
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    String tenantId = getTenant(okapiHeaders);
    CQLWrapper cql;
    try {
      cql = getCQL(query, limit, offset, LOCATION_TABLE);
    } catch (FieldException e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
        GetLocationsResponse.withPlainBadRequest(message)));
      return;
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .get(LOCATION_TABLE, Location.class,
        new String[]{"*"}, cql, true, true, reply -> {
          // netbeans, please indent here!
          if (reply.failed()) {
            String message = logAndSaveError(reply.cause());
            asyncResultHandler.handle(Future.succeededFuture(
              GetLocationsResponse.withPlainBadRequest(message)));
          } else {
            Locations shelfLocations = new Locations();
            List<Location> shelfLocationsList = (List<Location>) reply.result().getResults();
            shelfLocations.setLocations(shelfLocationsList);
            shelfLocations.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
            asyncResultHandler.handle(Future.succeededFuture(
              GetLocationsResponse.withJsonOK(shelfLocations)));
          }
        });
  }

  @Override
  public void postLocations(
    String lang,
    Location entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>>asyncResultHandler,
    Context vertxContext) {

    String tenantId = getTenant(okapiHeaders);
    String id = entity.getId();
    if (id == null) {
      id = UUID.randomUUID().toString();
      entity.setId(id);
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .save(LOCATION_TABLE, id, entity, reply -> {
        if (reply.failed()) {
          String message = logAndSaveError(reply.cause());
          if (message != null
            && message.contains("duplicate key value violates unique constraint")) {
            asyncResultHandler.handle(Future.succeededFuture(
              PostLocationsResponse.withJsonUnprocessableEntity(
                ValidationHelper.createValidationErrorMessage(
                  "shelflocation", entity.getId(),
                  "Location already exists"))));
          } else {
            asyncResultHandler.handle(Future.succeededFuture(
              PostLocationsResponse.withPlainInternalServerError(message)));
          }
        } else {
          Object responseObject = reply.result();
          entity.setId((String) responseObject);
          OutStream stream = new OutStream();
          stream.setData(entity);
          asyncResultHandler.handle(Future.succeededFuture(
            PostLocationsResponse.withJsonCreated(URL_PREFIX + responseObject, stream)));
        }
      });
  }

  @Override
  public void getLocationsById(
    String id,
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>>asyncResultHandler,
    Context vertxContext) {

    String tenantId = getTenant(okapiHeaders);
    Criterion criterion;
    try {
      Criteria criteria = new Criteria(LOCATION_SCHEMA_PATH);
      criteria.addField(ID_FIELD_NAME);
      criteria.setOperation("=");
      criteria.setValue(id);
      criterion = new Criterion(criteria);
    } catch (Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
        GetLocationsByIdResponse.withPlainInternalServerError(message)));
      return;
    }

    PostgresClient.getInstance(vertxContext.owner(), tenantId).get(
      LOCATION_TABLE, Location.class, criterion, true, false, getReply -> {
        if (getReply.failed()) {
          String message = logAndSaveError(getReply.cause());
          asyncResultHandler.handle(Future.succeededFuture(
            GetLocationsByIdResponse.withPlainInternalServerError(message)));
        } else {
          List<Location> locationList = (List<Location>) getReply.result().getResults();
          if (locationList.isEmpty()) {
            asyncResultHandler.handle(Future.succeededFuture(
              GetLocationsByIdResponse.withPlainNotFound(
                messages.getMessage(lang, MessageConsts.ObjectDoesNotExist))));
          } else if (locationList.size() > 1) {
            String message = "Multiple locations found with the same id";
            logger.error(message);
            asyncResultHandler.handle(Future.succeededFuture(
              GetLocationsByIdResponse.withPlainInternalServerError(message)));
          } else {
            asyncResultHandler.handle(Future.succeededFuture(GetLocationsByIdResponse.withJsonOK(locationList.get(0))));
          }
        }
      });
  }

  @Override
  public void deleteLocationsById(
    String id,
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>>asyncResultHandler,
    Context vertxContext) {

    String tenantId = getTenant(okapiHeaders);
    Criterion criterion;
    try {
      Criteria criteria = new Criteria(LOCATION_SCHEMA_PATH);
      criteria.addField(ID_FIELD_NAME);
      criteria.setOperation("=");
      criteria.setValue(id);
      criterion = new Criterion(criteria);
    } catch (Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
        GetLocationsByIdResponse.withPlainInternalServerError(message)));
      return;
    }

    locationInUse(id, tenantId, vertxContext).setHandler(res -> {
      if (res.failed()) {
        String message = logAndSaveError(res.cause());
        DeleteLocationsByIdResponse.withPlainInternalServerError(message);
      } else {
        if (res.result()) {
          asyncResultHandler.handle(Future.succeededFuture(
            DeleteLocationsByIdResponse.withPlainBadRequest("Cannot delete location, as it is in use")));
        } else {
          PostgresClient.getInstance(vertxContext.owner(), tenantId).delete(LOCATION_TABLE, criterion, deleteReply -> {
            if (deleteReply.failed()) {
              logAndSaveError(deleteReply.cause());
              asyncResultHandler.handle(Future.succeededFuture(
                DeleteLocationsByIdResponse.withPlainNotFound("Not found")));
            } else {
              asyncResultHandler.handle(Future.succeededFuture(
                DeleteLocationsByIdResponse.withNoContent()));
            }
          });
        }
      }
    });
  }

  @Override
  public void putLocationsById(
    String id,
    String lang,
    Location entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>>asyncResultHandler,
    Context vertxContext) {

    if (!id.equals(entity.getId())) {
      String message = "Illegal operation: id cannot be changed";
      asyncResultHandler.handle(Future.succeededFuture(
        PutLocationsByIdResponse.withPlainBadRequest(message)));
      return;
    }

    String tenantId = getTenant(okapiHeaders);
    Criterion criterion;

    try {
      Criteria criteria = new Criteria(LOCATION_SCHEMA_PATH);
      criteria.addField(ID_FIELD_NAME);
      criteria.setOperation("=");
      criteria.setValue(id);
      criterion = new Criterion(criteria);
    } catch (Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
        PutLocationsByIdResponse.withPlainInternalServerError(message)));
      return;
    }

    PostgresClient.getInstance(vertxContext.owner(), tenantId).update(
      LOCATION_TABLE, entity, criterion, false, updateReply -> {
        if (updateReply.failed()) {
          String message = logAndSaveError(updateReply.cause());
          asyncResultHandler.handle(Future.succeededFuture(
            PutLocationsByIdResponse.withPlainInternalServerError(message)));
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
