package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.EndpointFailureHandler.handleFailure;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Loccamp;
import org.folio.rest.jaxrs.model.Locinst;
import org.folio.rest.jaxrs.model.Loclib;
import org.folio.rest.jaxrs.resource.LocationUnits;
import org.folio.services.locationunit.CampusService;
import org.folio.services.locationunit.InstitutionService;
import org.folio.services.locationunit.LibraryService;

public class LocationUnitApi implements LocationUnits {

  public static final String CAMPUS_TABLE = "loccampus";

  @Validate
  @Override
  public void getLocationUnitsInstitutions(boolean includeShadow, String query, String totalRecords, int offset,
                                           int limit, Map<String, String> okapiHeaders,
                                           Handler<AsyncResult<Response>> asyncResultHandler,
                                           Context vertxContext) {
    new InstitutionService(vertxContext, okapiHeaders)
      .getByQuery(query, offset, limit, totalRecords, includeShadow)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void postLocationUnitsInstitutions(Locinst entity, Map<String, String> okapiHeaders,
                                            Handler<AsyncResult<Response>> asyncResultHandler,
                                            Context vertxContext) {
    new InstitutionService(vertxContext, okapiHeaders)
      .create(entity)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void deleteLocationUnitsInstitutions(Map<String, String> okapiHeaders,
                                              Handler<AsyncResult<Response>> asyncResultHandler,
                                              Context vertxContext) {
    new InstitutionService(vertxContext, okapiHeaders)
      .deleteAll()
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void getLocationUnitsInstitutionsById(String id, Map<String, String> okapiHeaders,
                                               Handler<AsyncResult<Response>> asyncResultHandler,
                                               Context vertxContext) {
    new InstitutionService(vertxContext, okapiHeaders)
      .getById(id)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void putLocationUnitsInstitutionsById(String id, Locinst entity, Map<String, String> okapiHeaders,
                                               Handler<AsyncResult<Response>> asyncResultHandler,
                                               Context vertxContext) {
    new InstitutionService(vertxContext, okapiHeaders)
      .update(id, entity)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void deleteLocationUnitsInstitutionsById(String id, Map<String, String> okapiHeaders,
                                                  Handler<AsyncResult<Response>> asyncResultHandler,
                                                  Context vertxContext) {
    new InstitutionService(vertxContext, okapiHeaders)
      .delete(id)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void getLocationUnitsCampuses(boolean includeShadow, String query, String totalRecords, int offset, int limit,
                                       Map<String, String> okapiHeaders,
                                       Handler<AsyncResult<Response>> asyncResultHandler,
                                       Context vertxContext) {

    new CampusService(vertxContext, okapiHeaders)
      .getByQuery(query, offset, limit, totalRecords, includeShadow)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void postLocationUnitsCampuses(Loccamp entity, Map<String, String> okapiHeaders,
                                        Handler<AsyncResult<Response>> asyncResultHandler,
                                        Context vertxContext) {
    new CampusService(vertxContext, okapiHeaders)
      .create(entity)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void deleteLocationUnitsCampuses(Map<String, String> okapiHeaders,
                                          Handler<AsyncResult<Response>> asyncResultHandler,
                                          Context vertxContext) {
    new CampusService(vertxContext, okapiHeaders)
      .deleteAll()
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void getLocationUnitsCampusesById(String id, Map<String, String> okapiHeaders,
                                           Handler<AsyncResult<Response>> asyncResultHandler,
                                           Context vertxContext) {
    new CampusService(vertxContext, okapiHeaders)
      .getById(id)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void putLocationUnitsCampusesById(String id, Loccamp entity, Map<String, String> okapiHeaders,
                                           Handler<AsyncResult<Response>> asyncResultHandler,
                                           Context vertxContext) {
    new CampusService(vertxContext, okapiHeaders)
      .update(id, entity)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void deleteLocationUnitsCampusesById(String id, Map<String, String> okapiHeaders,
                                              Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    new CampusService(vertxContext, okapiHeaders)
      .delete(id)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void getLocationUnitsLibraries(boolean includeShadow, String query, String totalRecords, int offset, int limit,
                                        Map<String, String> okapiHeaders,
                                        Handler<AsyncResult<Response>> asyncResultHandler,
                                        Context vertxContext) {
    new LibraryService(vertxContext, okapiHeaders)
      .getByQuery(query, offset, limit, totalRecords, includeShadow)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void postLocationUnitsLibraries(Loclib entity, Map<String, String> okapiHeaders,
                                         Handler<AsyncResult<Response>> asyncResultHandler,
                                         Context vertxContext) {
    new LibraryService(vertxContext, okapiHeaders)
      .create(entity)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void deleteLocationUnitsLibraries(Map<String, String> okapiHeaders,
                                           Handler<AsyncResult<Response>> asyncResultHandler,
                                           Context vertxContext) {
    new LibraryService(vertxContext, okapiHeaders)
      .deleteAll()
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void getLocationUnitsLibrariesById(String id, Map<String, String> okapiHeaders,
                                            Handler<AsyncResult<Response>> asyncResultHandler,
                                            Context vertxContext) {
    new LibraryService(vertxContext, okapiHeaders)
      .getById(id)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void putLocationUnitsLibrariesById(String id, Loclib entity, Map<String, String> okapiHeaders,
                                            Handler<AsyncResult<Response>> asyncResultHandler,
                                            Context vertxContext) {
    new LibraryService(vertxContext, okapiHeaders)
      .update(id, entity)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void deleteLocationUnitsLibrariesById(String id, Map<String, String> okapiHeaders,
                                               Handler<AsyncResult<Response>> asyncResultHandler,
                                               Context vertxContext) {
    new LibraryService(vertxContext, okapiHeaders)
      .delete(id)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }
}
