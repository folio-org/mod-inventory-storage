package org.folio.rest.impl;

import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.model.Shelflocation;
import org.folio.rest.jaxrs.model.Shelflocations;
import org.folio.rest.jaxrs.resource.ShelfLocationsResource;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.ArrayList;
import org.apache.commons.lang.NotImplementedException;
import static org.folio.rest.impl.LocationAPI.LOCATION_SCHEMA_PATH;
import static org.folio.rest.impl.LocationAPI.LOCATION_TABLE;
import org.folio.rest.jaxrs.model.Location;

/**
 * This is the old shelf-location interface, now deprecated. We are working on
 * making this a read-only proxy to the new locations interface, to make
 * transition easier. In the end all this will have to die!
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

  /**
   * Get a list of the new locations, and fake old kind of shelf-locations out
   * of them.
   */
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
    try {
      String tenantId = LocationUnitAPI.getTenant(okapiHeaders);
      CQLWrapper cql = LocationUnitAPI.getCQL(query, limit, offset, LocationAPI.LOCATION_TABLE);
      PostgresClient.getInstance(vertxContext.owner(), tenantId)
        .get(
          LocationAPI.LOCATION_TABLE, Location.class, new String[]{"*"},
          cql, true, true, reply -> {
            try {
              if (reply.failed()) {
                String message = LocationUnitAPI.logAndSaveError(reply.cause());
                asyncResultHandler.handle(Future.succeededFuture(
                  GetShelfLocationsResponse.withPlainBadRequest(message)));
              } else {
                Shelflocations shelfLocations = new Shelflocations();
                List<Location> locationsList = (List<Location>) reply.result().getResults();
                List<Shelflocation> shelfLocationsList = new ArrayList<>(locationsList.size());
                for (Location loc : locationsList) {
                  Shelflocation sl = new Shelflocation();
                  sl.setId(loc.getId());
                  sl.setName(loc.getName());
                  shelfLocationsList.add(sl);
                }
                shelfLocations.setShelflocations(shelfLocationsList);
                shelfLocations.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
                asyncResultHandler.handle(Future.succeededFuture(GetShelfLocationsResponse.withJsonOK(shelfLocations)));
              }
            } catch (Exception e) {
              String message = LocationUnitAPI.logAndSaveError(e);
              asyncResultHandler.handle(Future.succeededFuture(
                GetShelfLocationsResponse.withPlainInternalServerError(message)));
            }
          });
    } catch (Exception e) {
      String message = LocationUnitAPI.logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
        GetShelfLocationsResponse.withPlainInternalServerError(message)));
    }
  }

  /**
   * Get a new-kind of Location object, and convert it to old-style
   * shelf-location.
   */
  @Override
  public void getShelfLocationsById(
    String id,
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext)
    throws Exception {
    try {
      String tenantId = LocationUnitAPI.getTenant(okapiHeaders);
      Criteria criteria = new Criteria(LOCATION_SCHEMA_PATH);
      criteria.addField(ID_FIELD_NAME);
      criteria.setOperation("=");
      criteria.setValue(id);
      Criterion criterion = new Criterion(criteria);
      PostgresClient.getInstance(vertxContext.owner(), tenantId).get(
        LOCATION_TABLE, Location.class, criterion, true,
        false, getReply -> {
          if (getReply.failed()) {
            String message = LocationUnitAPI.logAndSaveError(getReply.cause());
            asyncResultHandler.handle(Future.succeededFuture(
              GetShelfLocationsByIdResponse.withPlainInternalServerError(
                message)));
          } else {
            List<Location> locationList = (List<Location>) getReply.result().getResults();
            if (locationList.size() < 1) {
              asyncResultHandler.handle(Future.succeededFuture(
                GetShelfLocationsByIdResponse.withPlainNotFound(
                  messages.getMessage(lang, MessageConsts.ObjectDoesNotExist))));
            } else if (locationList.size() > 1) {
              String message = "Multiple locations found with the same id";
              logger.error(message);
              asyncResultHandler.handle(Future.succeededFuture(
                GetShelfLocationsByIdResponse
                  .withPlainInternalServerError(message)));
            } else {
              Location loc = locationList.get(0);
              Shelflocation sl = new Shelflocation();
              sl.setId(loc.getId());
              sl.setName(loc.getName());
              asyncResultHandler.handle(Future.succeededFuture(
                GetShelfLocationsByIdResponse.withJsonOK(sl)));
            }
          }
        });
    } catch (Exception e) {
      String message = LocationUnitAPI.logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
        GetShelfLocationsByIdResponse.withPlainInternalServerError(message)));
    }
  }

  @Override
  public void postShelfLocations(
          String lang,
          Shelflocation entity,
          Map<String, String> okapiHeaders,
          Handler<AsyncResult<Response>>asyncResultHandler,
          Context vertxContext)
    throws Exception {
    throw new NotImplementedException("Creating shelf-locations is DEPRECATED. "
      + "Use the new locations insterface instead");
  }

  @Override
  public void deleteShelfLocations(
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext)
    throws Exception {
    throw new NotImplementedException("Deleting shelf-locations is DEPRECATED. "
      + "Use the new locations insterface instead");
  }

  @Override
  public void deleteShelfLocationsById(
          String id,
          String lang, Map<String, String> okapiHeaders,
          Handler<AsyncResult<Response>>asyncResultHandler,
          Context vertxContext)
          throws Exception {
    throw new NotImplementedException("Deleting shelf-locations is DEPRECATED. "
      + "Use the new locations insterface instead");
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
    throw new NotImplementedException("Creating shelf-locations is DEPRECATED. "
      + "Use the new locations insterface instead");
  }

}
