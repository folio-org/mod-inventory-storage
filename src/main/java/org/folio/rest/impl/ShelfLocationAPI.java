package org.folio.rest.impl;

import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.model.Shelflocation;
import org.folio.rest.jaxrs.model.Shelflocations;
import org.folio.rest.jaxrs.resource.ShelfLocations;
import org.folio.rest.jaxrs.resource.Locations.GetLocationsByIdResponse;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.ArrayList;
import org.apache.commons.lang.NotImplementedException;
import static org.folio.rest.impl.LocationAPI.LOCATION_TABLE;
import static org.folio.rest.impl.StorageHelper.*;
import org.folio.rest.jaxrs.model.Location;

/**
 * This is the old shelf-location interface, now deprecated. We are working on
 * making this a read-only proxy to the new locations interface, to make
 * transition easier. In the end all this will have to die!
 *
 * @author kurt
 */
public class ShelfLocationAPI implements ShelfLocations {
  public static final String SHELF_LOCATION_TABLE = "shelflocation";
  public static final Logger logger = LoggerFactory.getLogger(ShelfLocationAPI.class);
  public static final String URL_PREFIX = "/shelflocations";
  public static final String USE_NEW = "Use the new /locations interface instead.";

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
        Context vertxContext) {
    try {
      String tenantId = getTenant(okapiHeaders);
      CQLWrapper cql = getCQL(query, limit, offset, LocationAPI.LOCATION_TABLE);
      PostgresClient.getInstance(vertxContext.owner(), tenantId)
        .get(
          LocationAPI.LOCATION_TABLE, Location.class, new String[]{"*"},
          cql, true, true, reply -> {
            try {
              if (reply.failed()) {
                String message = logAndSaveError(reply.cause());
                asyncResultHandler.handle(Future.succeededFuture(
                  GetShelfLocationsResponse.respond400WithTextPlain(message)));
              } else {
                Shelflocations shelfLocations = new Shelflocations();
                List<Location> locationsList = reply.result().getResults();
                List<Shelflocation> shelfLocationsList = new ArrayList<>(locationsList.size());
                for (Location loc : locationsList) {
                  Shelflocation sl = new Shelflocation();
                  sl.setId(loc.getId());
                  sl.setName(loc.getName());
                  shelfLocationsList.add(sl);
                }
                shelfLocations.setShelflocations(shelfLocationsList);
                shelfLocations.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
                asyncResultHandler.handle(Future.succeededFuture(GetShelfLocationsResponse.respond200WithApplicationJson(shelfLocations)));
              }
            } catch (Exception e) {
              String message = logAndSaveError(e);
              asyncResultHandler.handle(Future.succeededFuture(
                GetShelfLocationsResponse.respond500WithTextPlain(message)));
            }
          });
    } catch (Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
        GetShelfLocationsResponse.respond500WithTextPlain(message)));
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
    Context vertxContext) {

    PgUtil.getById(LOCATION_TABLE, Location.class, id, okapiHeaders, vertxContext,
        GetLocationsByIdResponse.class, result -> {
          if (result.failed()) {
            asyncResultHandler.handle(Future.failedFuture(result.cause()));
            return;
          }
          if (result.result().getStatus() != 200) {
            asyncResultHandler.handle(Future.succeededFuture(result.result()));
            return;
          }
          Location location = (Location) result.result().getEntity();
          // convert from new-style Location to old-style Shelflocation
          Shelflocation selfLocation = new Shelflocation().withId(location.getId()).withName(location.getName());
          Response response = GetShelfLocationsByIdResponse.respond200WithApplicationJson(selfLocation);
          asyncResultHandler.handle(Future.succeededFuture(response));
        });
  }

  @Override
  public void postShelfLocations(
          String lang,
          Shelflocation entity,
          Map<String, String> okapiHeaders,
          Handler<AsyncResult<Response>>asyncResultHandler,
          Context vertxContext) {
    throw new NotImplementedException("Creating shelf-locations is DEPRECATED. " + USE_NEW);
  }

  @Override
  public void deleteShelfLocations(
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {
    throw new NotImplementedException("Deleting shelf-locations is DEPRECATED. " + USE_NEW);
  }

  @Override
  public void deleteShelfLocationsById(
          String id,
          String lang, Map<String, String> okapiHeaders,
          Handler<AsyncResult<Response>>asyncResultHandler,
          Context vertxContext) {
    throw new NotImplementedException("Deleting shelf-locations is DEPRECATED. " + USE_NEW);
  }

  @Override
  public void putShelfLocationsById(
          String id,
          String lang,
          Shelflocation entity,
          Map<String, String> okapiHeaders,
          Handler<AsyncResult<Response>>asyncResultHandler,
          Context vertxContext) {
    throw new NotImplementedException("Creating shelf-locations is DEPRECATED. " + USE_NEW);
  }

}
