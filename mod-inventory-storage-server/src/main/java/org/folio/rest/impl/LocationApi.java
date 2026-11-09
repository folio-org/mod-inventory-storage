package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.ResponseHandlerUtil.firstErrorMessage;
import static org.folio.rest.support.ResponseHandlerUtil.handleFailure;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Location;
import org.folio.services.location.LocationService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("java:S6813")
public class LocationApi implements org.folio.rest.jaxrs.resource.Locations {

  @Autowired
  private LocationService service;

  public LocationApi() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Validate
  @Override
  public void getLocations(boolean includeShadowLocations, String query, String totalRecords, int offset, int limit,
                           Map<String, String> headers, Handler<AsyncResult<Response>> asyncResultHandler,
                           Context vertxContext) {

    service.getByQuery(query, offset, limit, totalRecords, includeShadowLocations, headers)
      .map(GetLocationsResponse::respond200WithApplicationJson)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void postLocations(Location entity, Map<String, String> headers,
                            Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    service.create(entity, headers)
      .map(created -> PostLocationsResponse.respond201WithApplicationJson(created,
        PostLocationsResponse.headersFor201().withLocation(created.getId())))
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void deleteLocations(Map<String, String> headers, Handler<AsyncResult<Response>> asyncResultHandler,
                              Context vertxContext) {
    service.deleteAll(headers)
      .map(notUsed -> DeleteLocationsResponse.respond204())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(e -> asyncResultHandler.handle(succeededFuture(
        DeleteLocationsResponse.respond500WithTextPlain(e.getMessage()))));
  }

  @Validate
  @Override
  public void getLocationsById(String id, Map<String, String> headers,
                               Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    service.getById(id, headers)
      .map(GetLocationsByIdResponse::respond200WithApplicationJson)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler, errors ->
        GetLocationsByIdResponse.respond404WithTextPlain(firstErrorMessage(errors))));
  }

  @Validate
  @Override
  public void putLocationsById(String id, Location entity, Map<String, String> headers,
                               Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    service.update(id, entity, headers)
      .map(notUsed -> PutLocationsByIdResponse.respond204())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void deleteLocationsById(String id, Map<String, String> headers,
                                  Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    service.delete(id, headers)
      .map(notUsed -> DeleteLocationsByIdResponse.respond204())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler, errors ->
        DeleteLocationsByIdResponse.respond400WithTextPlain(firstErrorMessage(errors))));
  }
}
