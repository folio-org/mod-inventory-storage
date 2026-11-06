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
import org.folio.rest.jaxrs.model.LocationCampus;
import org.folio.rest.jaxrs.model.LocationInstitution;
import org.folio.rest.jaxrs.model.LocationLibrary;
import org.folio.rest.jaxrs.resource.LocationUnits;
import org.folio.services.locationunit.CampusService;
import org.folio.services.locationunit.InstitutionService;
import org.folio.services.locationunit.LibraryService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("java:S6813")
public class LocationUnitApi implements LocationUnits {

  @Autowired
  private InstitutionService institutionService;
  @Autowired
  private CampusService campusService;
  @Autowired
  private LibraryService libraryService;

  public LocationUnitApi() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Validate
  @Override
  public void getLocationUnitsInstitutions(boolean includeShadow, String query, String totalRecords, int offset,
                                           int limit, Map<String, String> headers,
                                           Handler<AsyncResult<Response>> asyncResultHandler,
                                           Context vertxContext) {
    institutionService.getByQuery(query, offset, limit, totalRecords, includeShadow, headers)
      .map(GetLocationUnitsInstitutionsResponse::respond200WithApplicationJson)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void postLocationUnitsInstitutions(LocationInstitution entity, Map<String, String> headers,
                                            Handler<AsyncResult<Response>> asyncResultHandler,
                                            Context vertxContext) {
    institutionService.create(entity, headers)
      .map(created -> PostLocationUnitsInstitutionsResponse.respond201WithApplicationJson(created,
        PostLocationUnitsInstitutionsResponse.headersFor201().withLocation(created.getId())))
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void deleteLocationUnitsInstitutions(Map<String, String> headers,
                                              Handler<AsyncResult<Response>> asyncResultHandler,
                                              Context vertxContext) {
    institutionService.deleteAll(headers)
      .map(notUsed -> DeleteLocationUnitsInstitutionsResponse.respond204())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(e -> asyncResultHandler.handle(succeededFuture(
        DeleteLocationUnitsInstitutionsResponse.respond500WithTextPlain(e.getMessage()))));
  }

  @Validate
  @Override
  public void getLocationUnitsInstitutionsById(String id, Map<String, String> headers,
                                               Handler<AsyncResult<Response>> asyncResultHandler,
                                               Context vertxContext) {
    institutionService.getById(id, headers)
      .map(GetLocationUnitsInstitutionsByIdResponse::respond200WithApplicationJson)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler, errors ->
        GetLocationUnitsInstitutionsByIdResponse.respond404WithTextPlain(firstErrorMessage(errors))));
  }

  @Validate
  @Override
  public void putLocationUnitsInstitutionsById(String id, LocationInstitution entity, Map<String, String> headers,
                                               Handler<AsyncResult<Response>> asyncResultHandler,
                                               Context vertxContext) {
    institutionService.update(id, entity, headers)
      .map(notUsed -> PutLocationUnitsInstitutionsByIdResponse.respond204())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler, errors ->
        PutLocationUnitsInstitutionsByIdResponse.respond400WithTextPlain(errors)));
  }

  @Validate
  @Override
  public void deleteLocationUnitsInstitutionsById(String id, Map<String, String> headers,
                                                  Handler<AsyncResult<Response>> asyncResultHandler,
                                                  Context vertxContext) {
    institutionService.delete(id, headers)
      .map(notUsed -> DeleteLocationUnitsInstitutionsByIdResponse.respond204())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler, errors ->
        DeleteLocationUnitsInstitutionsByIdResponse.respond400WithTextPlain(firstErrorMessage(errors))));
  }

  @Validate
  @Override
  public void getLocationUnitsCampuses(boolean includeShadow, String query, String totalRecords, int offset, int limit,
                                       Map<String, String> headers,
                                       Handler<AsyncResult<Response>> asyncResultHandler,
                                       Context vertxContext) {

    campusService.getByQuery(query, offset, limit, totalRecords, includeShadow, headers)
      .map(GetLocationUnitsCampusesResponse::respond200WithApplicationJson)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void postLocationUnitsCampuses(LocationCampus entity, Map<String, String> headers,
                                        Handler<AsyncResult<Response>> asyncResultHandler,
                                        Context vertxContext) {
    campusService.create(entity, headers)
      .map(created -> PostLocationUnitsCampusesResponse.respond201WithApplicationJson(created,
        PostLocationUnitsCampusesResponse.headersFor201().withLocation(created.getId())))
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void deleteLocationUnitsCampuses(Map<String, String> headers,
                                          Handler<AsyncResult<Response>> asyncResultHandler,
                                          Context vertxContext) {
    campusService.deleteAll(headers)
      .map(notUsed -> DeleteLocationUnitsCampusesResponse.respond204())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(e -> asyncResultHandler.handle(succeededFuture(
        DeleteLocationUnitsCampusesResponse.respond500WithTextPlain(e.getMessage()))));
  }

  @Validate
  @Override
  public void getLocationUnitsCampusesById(String id, Map<String, String> headers,
                                           Handler<AsyncResult<Response>> asyncResultHandler,
                                           Context vertxContext) {
    campusService.getById(id, headers)
      .map(GetLocationUnitsCampusesByIdResponse::respond200WithApplicationJson)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler, errors ->
        GetLocationUnitsCampusesByIdResponse.respond404WithTextPlain(firstErrorMessage(errors))));
  }

  @Validate
  @Override
  public void putLocationUnitsCampusesById(String id, LocationCampus entity, Map<String, String> headers,
                                           Handler<AsyncResult<Response>> asyncResultHandler,
                                           Context vertxContext) {
    campusService.update(id, entity, headers)
      .map(notUsed -> PutLocationUnitsCampusesByIdResponse.respond204())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler, errors ->
        PutLocationUnitsCampusesByIdResponse.respond400WithTextPlain(errors)));
  }

  @Validate
  @Override
  public void deleteLocationUnitsCampusesById(String id, Map<String, String> headers,
                                              Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    campusService.delete(id, headers)
      .map(notUsed -> DeleteLocationUnitsCampusesByIdResponse.respond204())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler, errors ->
        DeleteLocationUnitsCampusesByIdResponse.respond400WithTextPlain(firstErrorMessage(errors))));
  }

  @Validate
  @Override
  public void getLocationUnitsLibraries(boolean includeShadow, String query, String totalRecords, int offset, int limit,
                                        Map<String, String> headers,
                                        Handler<AsyncResult<Response>> asyncResultHandler,
                                        Context vertxContext) {
    libraryService.getByQuery(query, offset, limit, totalRecords, includeShadow, headers)
      .map(GetLocationUnitsLibrariesResponse::respond200WithApplicationJson)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void postLocationUnitsLibraries(LocationLibrary entity, Map<String, String> headers,
                                         Handler<AsyncResult<Response>> asyncResultHandler,
                                         Context vertxContext) {
    libraryService.create(entity, headers)
      .map(created -> PostLocationUnitsLibrariesResponse.respond201WithApplicationJson(created,
        PostLocationUnitsLibrariesResponse.headersFor201().withLocation(created.getId())))
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void deleteLocationUnitsLibraries(Map<String, String> headers,
                                           Handler<AsyncResult<Response>> asyncResultHandler,
                                           Context vertxContext) {
    libraryService.deleteAll(headers)
      .map(notUsed -> DeleteLocationUnitsLibrariesResponse.respond204())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(e -> asyncResultHandler.handle(succeededFuture(
        DeleteLocationUnitsLibrariesResponse.respond500WithTextPlain(e.getMessage()))));
  }

  @Validate
  @Override
  public void getLocationUnitsLibrariesById(String id, Map<String, String> headers,
                                            Handler<AsyncResult<Response>> asyncResultHandler,
                                            Context vertxContext) {
    libraryService.getById(id, headers)
      .map(GetLocationUnitsLibrariesByIdResponse::respond200WithApplicationJson)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler, errors ->
        GetLocationUnitsLibrariesByIdResponse.respond404WithTextPlain(firstErrorMessage(errors))));
  }

  @Validate
  @Override
  public void putLocationUnitsLibrariesById(String id, LocationLibrary entity, Map<String, String> headers,
                                            Handler<AsyncResult<Response>> asyncResultHandler,
                                            Context vertxContext) {
    libraryService.update(id, entity, headers)
      .map(notUsed -> PutLocationUnitsLibrariesByIdResponse.respond204())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler, errors ->
        PutLocationUnitsLibrariesByIdResponse.respond400WithTextPlain(errors)));
  }

  @Validate
  @Override
  public void deleteLocationUnitsLibrariesById(String id, Map<String, String> headers,
                                               Handler<AsyncResult<Response>> asyncResultHandler,
                                               Context vertxContext) {
    libraryService.delete(id, headers)
      .map(notUsed -> DeleteLocationUnitsLibrariesByIdResponse.respond204())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler, errors ->
        DeleteLocationUnitsLibrariesByIdResponse.respond400WithTextPlain(firstErrorMessage(errors))));
  }
}
