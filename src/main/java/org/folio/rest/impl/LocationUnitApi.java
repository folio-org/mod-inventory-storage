package org.folio.rest.impl;

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
import org.folio.rest.jaxrs.model.Loccamps;
import org.folio.rest.jaxrs.model.Locinst;
import org.folio.rest.jaxrs.model.Locinsts;
import org.folio.rest.jaxrs.model.Loclib;
import org.folio.rest.jaxrs.model.Loclibs;
import org.folio.rest.jaxrs.resource.LocationUnits;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;

public class LocationUnitApi implements LocationUnits {
  public static final String URL_PREFIX = "/location-units";
  public static final String INSTITUTION_TABLE = "locinstitution";
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
    String tenantId = TenantTool.tenantId(okapiHeaders);
    CQLWrapper cql;
    try {
      cql = StorageHelper.getCql(query, limit, offset, INSTITUTION_TABLE);
    } catch (Exception e) {
      String message = StorageHelper.logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
        GetLocationUnitsInstitutionsResponse
          .respond500WithTextPlain(message)));
      return;
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .get(INSTITUTION_TABLE, Locinst.class, new String[] {"*"},
        cql, true, true, reply -> {
          if (reply.failed()) {
            String message = StorageHelper.logAndSaveError(reply.cause());
            asyncResultHandler.handle(Future.succeededFuture(
              GetLocationUnitsInstitutionsResponse
                .respond400WithTextPlain(message)));
          } else {
            Locinsts insts = new Locinsts();
            List<Locinst> items = reply.result().getResults();
            insts.setLocinsts(items);
            insts.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
            asyncResultHandler.handle(Future.succeededFuture(
              GetLocationUnitsInstitutionsResponse.respond200WithApplicationJson(insts)));
          }
        });
  }

  @Validate
  @Override
  public void postLocationUnitsInstitutions(Locinst entity, Map<String, String> okapiHeaders,
                                            Handler<AsyncResult<Response>> asyncResultHandler,
                                            Context vertxContext) {

    String tenantId = TenantTool.tenantId(okapiHeaders);
    String id = entity.getId();
    if (id == null) {
      id = UUID.randomUUID().toString();
      entity.setId(id);
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .save(INSTITUTION_TABLE, id, entity, reply -> {
        if (reply.failed()) {
          String message = StorageHelper.logAndSaveError(reply.cause());
          if (StorageHelper.isDuplicate(message)) {
            asyncResultHandler.handle(Future.succeededFuture(
              PostLocationUnitsInstitutionsResponse
                .respond422WithApplicationJson(
                  ValidationHelper.createValidationErrorMessage(
                    "locinst", entity.getId(),
                    "Institution already exists"))));
          } else {
            asyncResultHandler.handle(Future.succeededFuture(
              PostLocationUnitsInstitutionsResponse
                .respond500WithTextPlain(message)));
          }
        } else {
          String responseObject = reply.result();
          entity.setId(responseObject);
          asyncResultHandler.handle(Future.succeededFuture(
            PostLocationUnitsInstitutionsResponse
              .respond201WithApplicationJson(entity,
                PostLocationUnitsInstitutionsResponse.headersFor201().withLocation(URL_PREFIX + responseObject))));
        }
      });
  }

  @Validate
  @Override
  public void deleteLocationUnitsInstitutions(Map<String, String> okapiHeaders,
                                              Handler<AsyncResult<Response>> asyncResultHandler,
                                              Context vertxContext) {
    String tenantId = TenantTool.tenantId(okapiHeaders);
    PostgresClient.getInstance(vertxContext.owner(),
        TenantTool.calculateTenantId(tenantId))
      .execute(String.format(DELETE_SQL_TEMPLATE, tenantId, MOD_NAME, INSTITUTION_TABLE),
        reply -> {
          if (reply.succeeded()) {
            asyncResultHandler.handle(Future.succeededFuture(
              DeleteLocationUnitsInstitutionsResponse.respond204()));
          } else {
            asyncResultHandler.handle(Future.succeededFuture(
              DeleteLocationUnitsInstitutionsResponse
                .respond500WithTextPlain(reply.cause().getMessage())));
          }
        });
  }

  @Validate
  @Override
  public void getLocationUnitsInstitutionsById(String id, Map<String, String> okapiHeaders,
                                               Handler<AsyncResult<Response>> asyncResultHandler,
                                               Context vertxContext) {

    PgUtil.getById(INSTITUTION_TABLE, Locinst.class, id, okapiHeaders, vertxContext,
      GetLocationUnitsInstitutionsByIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void putLocationUnitsInstitutionsById(String id, Locinst entity, Map<String, String> okapiHeaders,
                                               Handler<AsyncResult<Response>> asyncResultHandler,
                                               Context vertxContext) {

    if (!id.equals(entity.getId())) {
      String message = "Illegal operation: Institution id cannot be changed";
      asyncResultHandler.handle(Future.succeededFuture(
        PutLocationUnitsInstitutionsByIdResponse
          .respond400WithTextPlain(message)));
      return;
    }

    PgUtil.put(INSTITUTION_TABLE, entity, id, okapiHeaders, vertxContext,
      PutLocationUnitsInstitutionsByIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteLocationUnitsInstitutionsById(String id, Map<String, String> okapiHeaders,
                                                  Handler<AsyncResult<Response>> asyncResultHandler,
                                                  Context vertxContext) {

    PgUtil.deleteById(INSTITUTION_TABLE, id, okapiHeaders, vertxContext,
      DeleteLocationUnitsInstitutionsByIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void getLocationUnitsCampuses(String query, String totalRecords, int offset, int limit,
                                       Map<String, String> okapiHeaders,
                                       Handler<AsyncResult<Response>> asyncResultHandler,
                                       Context vertxContext) {

    String tenantId = TenantTool.tenantId(okapiHeaders);
    CQLWrapper cql;
    try {
      cql = StorageHelper.getCql(query, limit, offset, CAMPUS_TABLE);
    } catch (Exception e) {
      String message = StorageHelper.logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
        GetLocationUnitsCampusesResponse
          .respond500WithTextPlain(message)));
      return;
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .get(CAMPUS_TABLE, Loccamp.class, new String[] {"*"},
        cql, true, true, reply -> {
          if (reply.failed()) {
            String message = StorageHelper.logAndSaveError(reply.cause());
            asyncResultHandler.handle(Future.succeededFuture(
              GetLocationUnitsCampusesResponse
                .respond400WithTextPlain(message)));
          } else {
            Loccamps camps = new Loccamps();
            List<Loccamp> items = reply.result().getResults();
            camps.setLoccamps(items);
            camps.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
            asyncResultHandler.handle(Future.succeededFuture(
              GetLocationUnitsCampusesResponse.respond200WithApplicationJson(camps)));
          }
        });
  }

  @Validate
  @Override
  public void postLocationUnitsCampuses(Loccamp entity, Map<String, String> okapiHeaders,
                                        Handler<AsyncResult<Response>> asyncResultHandler,
                                        Context vertxContext) {

    String tenantId = TenantTool.tenantId(okapiHeaders);
    String id = entity.getId();
    if (id == null) {
      id = UUID.randomUUID().toString();
      entity.setId(id);
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .save(CAMPUS_TABLE, id, entity, reply -> {
        if (reply.failed()) {
          String message = StorageHelper.logAndSaveError(reply.cause());
          if (StorageHelper.isDuplicate(message)) {
            asyncResultHandler.handle(Future.succeededFuture(
              PostLocationUnitsCampusesResponse
                .respond422WithApplicationJson(
                  ValidationHelper.createValidationErrorMessage(
                    "loccamp", entity.getId(),
                    "Campus already exists"))));
          } else {
            asyncResultHandler.handle(Future.succeededFuture(
              PostLocationUnitsCampusesResponse.respond500WithTextPlain(message)));
          }
        } else {
          String responseObject = reply.result();
          entity.setId(responseObject);
          asyncResultHandler.handle(Future.succeededFuture(
            PostLocationUnitsCampusesResponse
              .respond201WithApplicationJson(entity,
                PostLocationUnitsCampusesResponse.headersFor201().withLocation(URL_PREFIX + responseObject))));
        }
      });
  }

  @Validate
  @Override
  public void deleteLocationUnitsCampuses(Map<String, String> okapiHeaders,
                                          Handler<AsyncResult<Response>> asyncResultHandler,
                                          Context vertxContext) {
    String tenantId = TenantTool.tenantId(okapiHeaders);
    PostgresClient.getInstance(vertxContext.owner(),
        TenantTool.calculateTenantId(tenantId))
      .execute(String.format(DELETE_SQL_TEMPLATE,
          tenantId, MOD_NAME, CAMPUS_TABLE),
        reply -> {
          if (reply.succeeded()) {
            asyncResultHandler.handle(Future.succeededFuture(
              DeleteLocationUnitsCampusesResponse.respond204()));
          } else {
            asyncResultHandler.handle(Future.succeededFuture(
              DeleteLocationUnitsCampusesResponse
                .respond500WithTextPlain(reply.cause().getMessage())));
          }
        });
  }

  @Validate
  @Override
  public void getLocationUnitsCampusesById(String id, Map<String, String> okapiHeaders,
                                           Handler<AsyncResult<Response>> asyncResultHandler,
                                           Context vertxContext) {

    PgUtil.getById(CAMPUS_TABLE, Loccamp.class, id, okapiHeaders, vertxContext,
      GetLocationUnitsCampusesByIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void putLocationUnitsCampusesById(String id, Loccamp entity, Map<String, String> okapiHeaders,
                                           Handler<AsyncResult<Response>> asyncResultHandler,
                                           Context vertxContext) {

    if (!id.equals(entity.getId())) {
      String message = "Illegal operation:Campus  id cannot be changed";
      asyncResultHandler.handle(Future.succeededFuture(
        PutLocationUnitsCampusesByIdResponse.respond400WithTextPlain(message)));
      return;
    }
    PgUtil.put(CAMPUS_TABLE, entity, id, okapiHeaders, vertxContext,
      PutLocationUnitsCampusesByIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteLocationUnitsCampusesById(String id, Map<String, String> okapiHeaders,
                                              Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    PgUtil.deleteById(CAMPUS_TABLE, id, okapiHeaders, vertxContext,
      DeleteLocationUnitsCampusesByIdResponse.class, asyncResultHandler);
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
