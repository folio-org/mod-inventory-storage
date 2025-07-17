package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.EndpointFailureHandler.handleFailure;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Location;
import org.folio.services.location.LocationService;

public class LocationApi implements org.folio.rest.jaxrs.resource.Locations {

  @Validate
  @Override
  public void getLocations(boolean includeShadowLocations, String query, String totalRecords, int offset, int limit,
                           Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
                           Context vertxContext) {

    new LocationService(vertxContext, okapiHeaders)
      .getByQuery(query, offset, limit, totalRecords, includeShadowLocations)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void postLocations(Location entity, Map<String, String> okapiHeaders,
                            Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    new LocationService(vertxContext, okapiHeaders).create(entity)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void deleteLocations(Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
                              Context vertxContext) {
    new LocationService(vertxContext, okapiHeaders).deleteAll()
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void getLocationsById(String id, Map<String, String> okapiHeaders,
                               Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    new LocationService(vertxContext, okapiHeaders).getById(id)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void putLocationsById(String id, Location entity, Map<String, String> okapiHeaders,
                               Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    new LocationService(vertxContext, okapiHeaders).update(id, entity)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void deleteLocationsById(String id, Map<String, String> okapiHeaders,
                                  Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    new LocationService(vertxContext, okapiHeaders).delete(id)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }
}
