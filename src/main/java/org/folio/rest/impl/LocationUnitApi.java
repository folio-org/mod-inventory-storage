package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.EndpointFailureHandler.handleFailure;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Loccamp;
import org.folio.rest.jaxrs.model.Locinst;
import org.folio.rest.jaxrs.model.Loclib;
import org.folio.rest.jaxrs.model.Loclibs;
import org.folio.rest.jaxrs.resource.LocationUnits;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;
import org.folio.services.locationunit.CampusService;
import org.folio.services.locationunit.InstitutionService;

public class LocationUnitApi implements LocationUnits {
  public static final String URL_PREFIX = "/location-units";
  public static final String CAMPUS_TABLE = "loccampus";
  public static final String LIBRARY_TABLE = "loclibrary";
  private static final String MOD_NAME = "mod_inventory_storage";
  private static final String DELETE_SQL_TEMPLATE = "DELETE FROM %s_%s.%s";

  @Validate
  @Override
  public void getLocationUnitsInstitutions(String query, String totalRecords, int offset, int limit,
                                           Map<String, String> okapiHeaders,
                                           Handler<AsyncResult<Response>> asyncResultHandler,
                                           Context vertxContext) {
    new InstitutionService(vertxContext, okapiHeaders)
      .getByQuery(query, offset, limit)
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
  public void getLocationUnitsCampuses(String query, String totalRecords, int offset, int limit,
                                       Map<String, String> okapiHeaders,
                                       Handler<AsyncResult<Response>> asyncResultHandler,
                                       Context vertxContext) {

    new CampusService(vertxContext, okapiHeaders)
      .getByQuery(query, offset, limit)
      .onSuccess(response ->  asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void postLocationUnitsCampuses(Loccamp entity, Map<String, String> okapiHeaders,
                                        Handler<AsyncResult<Response>> asyncResultHandler,
                                        Context vertxContext) {
    new CampusService(vertxContext, okapiHeaders)
      .create(entity)
      .onSuccess(response ->  asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void deleteLocationUnitsCampuses(Map<String, String> okapiHeaders,
                                          Handler<AsyncResult<Response>> asyncResultHandler,
                                          Context vertxContext) {
    new CampusService(vertxContext, okapiHeaders)
      .deleteAll()
      .onSuccess(response ->  asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void getLocationUnitsCampusesById(String id, Map<String, String> okapiHeaders,
                                           Handler<AsyncResult<Response>> asyncResultHandler,
                                           Context vertxContext) {
    new CampusService(vertxContext, okapiHeaders)
      .getById(id)
      .onSuccess(response ->  asyncResultHandler.handle(succeededFuture(response)))
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
  public void getLocationUnitsLibraries(String query, String totalRecords, int offset, int limit,
                                        Map<String, String> okapiHeaders,
                                        Handler<AsyncResult<Response>> asyncResultHandler,
                                        Context vertxContext) {

    String tenantId = TenantTool.tenantId(okapiHeaders);
    CQLWrapper cql;
    try {
      cql = StorageHelper.getCql(query, limit, offset, LIBRARY_TABLE);
    } catch (Exception e) {
      String message = StorageHelper.logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
        GetLocationUnitsLibrariesResponse
          .respond500WithTextPlain(message)));
      return;
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .get(LIBRARY_TABLE, Loclib.class, new String[] {"*"},
        cql, true, true, reply -> {
          if (reply.failed()) {
            String message = StorageHelper.logAndSaveError(reply.cause());
            asyncResultHandler.handle(Future.succeededFuture(
              GetLocationUnitsLibrariesResponse
                .respond400WithTextPlain(message)));
          } else {
            Loclibs lib = new Loclibs();
            List<Loclib> items = reply.result().getResults();
            lib.setLoclibs(items);
            lib.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
            asyncResultHandler.handle(Future.succeededFuture(
              GetLocationUnitsLibrariesResponse.respond200WithApplicationJson(lib)));
          }
        });
  }

  @Validate
  @Override
  public void postLocationUnitsLibraries(Loclib entity, Map<String, String> okapiHeaders,
                                         Handler<AsyncResult<Response>> asyncResultHandler,
                                         Context vertxContext) {

    String tenantId = TenantTool.tenantId(okapiHeaders);
    String id = entity.getId();
    if (id == null) {
      id = UUID.randomUUID().toString();
      entity.setId(id);
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .save(LIBRARY_TABLE, id, entity, reply -> {
        if (reply.failed()) {
          String message = StorageHelper.logAndSaveError(reply.cause());
          if (StorageHelper.isDuplicate(message)) {
            asyncResultHandler.handle(Future.succeededFuture(
              PostLocationUnitsLibrariesResponse
                .respond422WithApplicationJson(
                  ValidationHelper.createValidationErrorMessage(
                    "loclib", entity.getId(),
                    "Library already exists"))));
          } else {
            asyncResultHandler.handle(Future.succeededFuture(
              PostLocationUnitsLibrariesResponse.respond500WithTextPlain(message)));
          }
        } else {
          String responseObject = reply.result();
          entity.setId(responseObject);
          asyncResultHandler.handle(Future.succeededFuture(
            PostLocationUnitsLibrariesResponse
              .respond201WithApplicationJson(entity,
                PostLocationUnitsLibrariesResponse.headersFor201().withLocation(URL_PREFIX + responseObject))));
        }
      });
  }

  ////////////////////////////////////////////
  @Validate
  @Override
  public void deleteLocationUnitsLibraries(Map<String, String> okapiHeaders,
                                           Handler<AsyncResult<Response>> asyncResultHandler,
                                           Context vertxContext) {

    String tenantId = TenantTool.tenantId(okapiHeaders);
    PostgresClient postgresClient = PostgresClient.getInstance(
      vertxContext.owner(), TenantTool.calculateTenantId(tenantId));
    postgresClient.execute(String.format(DELETE_SQL_TEMPLATE,
        tenantId, MOD_NAME, LIBRARY_TABLE),
      reply -> {
        if (reply.succeeded()) {
          asyncResultHandler.handle(Future.succeededFuture(
            DeleteLocationUnitsLibrariesResponse.respond204()));
        } else {
          asyncResultHandler.handle(Future.succeededFuture(
            DeleteLocationUnitsLibrariesResponse
              .respond500WithTextPlain(reply.cause().getMessage())));
        }
      });
  }

  @Validate
  @Override
  public void getLocationUnitsLibrariesById(String id, Map<String, String> okapiHeaders,
                                            Handler<AsyncResult<Response>> asyncResultHandler,
                                            Context vertxContext) {

    PgUtil.getById(LIBRARY_TABLE, Loclib.class, id, okapiHeaders, vertxContext,
      GetLocationUnitsLibrariesByIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void putLocationUnitsLibrariesById(String id, Loclib entity, Map<String, String> okapiHeaders,
                                            Handler<AsyncResult<Response>> asyncResultHandler,
                                            Context vertxContext) {

    if (!id.equals(entity.getId())) {
      String message = "Illegal operation: Library id cannot be changed";
      asyncResultHandler.handle(Future.succeededFuture(
        PutLocationUnitsLibrariesByIdResponse.respond400WithTextPlain(message)));
      return;
    }
    PgUtil.put(LIBRARY_TABLE, entity, id, okapiHeaders, vertxContext,
      PutLocationUnitsLibrariesByIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteLocationUnitsLibrariesById(String id, Map<String, String> okapiHeaders,
                                               Handler<AsyncResult<Response>> asyncResultHandler,
                                               Context vertxContext) {

    PgUtil.deleteById(LIBRARY_TABLE, id, okapiHeaders, vertxContext,
      DeleteLocationUnitsLibrariesByIdResponse.class, asyncResultHandler);
  }
}
