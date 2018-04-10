package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.ws.rs.core.Response;
import org.folio.rest.jaxrs.model.Loccamp;
import org.folio.rest.jaxrs.model.Loccamps;
import org.folio.rest.jaxrs.model.Locinst;
import org.folio.rest.jaxrs.model.Locinsts;
import org.folio.rest.jaxrs.model.Loclib;
import org.folio.rest.jaxrs.model.Loclibs;
import org.folio.rest.jaxrs.resource.LocationUnitsResource;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;

public class LocationUnitAPI implements LocationUnitsResource {
  private final Messages messages = Messages.getInstance();
  public static final String URL_PREFIX = "/location-units";
  public static final String ID_FIELD_NAME = "'id'"; // same for all of them
  public static final String INSTITUTION_TABLE = "locinstitution";
  public static final String URL_PREFIX_INST = URL_PREFIX + "/institutions";
  public static final String INST_SCHEMA_PATH = "apidocs/raml/locinst.json";
  public static final String CAMPUS_TABLE = "loccampus";
  public static final String URL_PREFIX_CAMP = URL_PREFIX + "/campuses";
  public static final String CAMP_SCHEMA_PATH = "apidocs/raml/loccamp.json";
  public static final String LIBRARY_TABLE = "loclibrary";
  public static final String URL_PREFIX_LIB = URL_PREFIX + "/libraries";
  public static final String LIB_SCHEMA_PATH = "apidocs/raml/loclib.json";
  private static final String MOD_NAME = "mod_inventory_storage";

  @Override
  public void deleteLocationUnitsInstitutions(String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {
    String tenantId = TenantTool.tenantId(okapiHeaders);
    PostgresClient.getInstance(vertxContext.owner(),
      TenantTool.calculateTenantId(tenantId))
      .mutate(String.format("DELETE FROM %s_%s.%s",
        tenantId, MOD_NAME, INSTITUTION_TABLE),
        reply -> {
          if (reply.succeeded()) {
            asyncResultHandler.handle(Future.succeededFuture(
              LocationUnitsResource.DeleteLocationUnitsInstitutionsResponse.noContent()
                .build()));
          } else {
            asyncResultHandler.handle(Future.succeededFuture(
              LocationUnitsResource.DeleteLocationUnitsInstitutionsResponse
                .withPlainInternalServerError(reply.cause().getMessage())));
          }
        });
  }

  @Override
  public void getLocationUnitsInstitutions(
    String query, int offset, int limit,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {
    String tenantId = StorageHelper.getTenant(okapiHeaders);
    CQLWrapper cql;
    try {
      cql = StorageHelper.getCQL(query, limit, offset, INSTITUTION_TABLE);
    } catch (Exception e) {
      String message = StorageHelper.logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
        LocationUnitsResource.GetLocationUnitsInstitutionsResponse
          .withPlainInternalServerError(message)));
      return;
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .get(INSTITUTION_TABLE, Locinst.class, new String[]{"*"},
        cql, true, true, reply -> {
          if (reply.failed()) {
            String message = StorageHelper.logAndSaveError(reply.cause());
            asyncResultHandler.handle(Future.succeededFuture(
              LocationUnitsResource.GetLocationUnitsInstitutionsResponse
                .withPlainBadRequest(message)));
          } else {
            Locinsts insts = new Locinsts();
            List<Locinst> items = (List<Locinst>) reply.result().getResults();
            insts.setLocinsts(items);
            insts.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
            asyncResultHandler.handle(Future.succeededFuture(
              LocationUnitsResource.GetLocationUnitsInstitutionsResponse.withJsonOK(insts)));
          }
        });
  }

  @Override
  public void postLocationUnitsInstitutions(String lang,
    Locinst entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    String tenantId = StorageHelper.getTenant(okapiHeaders);
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
              LocationUnitsResource.PostLocationUnitsInstitutionsResponse
                .withJsonUnprocessableEntity(
                  ValidationHelper.createValidationErrorMessage(
                    "locinst", entity.getId(),
                    "Institution already exists"))));
          } else {
            asyncResultHandler.handle(Future.succeededFuture(
              LocationUnitsResource.PostLocationUnitsInstitutionsResponse
                .withPlainInternalServerError(message)));
          }
        } else {
          Object responseObject = reply.result();
          entity.setId((String) responseObject);
          OutStream stream = new OutStream();
          stream.setData(entity);
          asyncResultHandler.handle(Future.succeededFuture(
            LocationUnitsResource.PostLocationUnitsInstitutionsResponse
              .withJsonCreated(URL_PREFIX + responseObject, stream)));
        }
      });
  }

  @Override
  public void getLocationUnitsInstitutionsById(String id,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    String tenantId = StorageHelper.getTenant(okapiHeaders);
    Criterion criterion = StorageHelper.idCriterion(id, INST_SCHEMA_PATH, asyncResultHandler);
    if (criterion == null) {
      return; // error already handled
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .get(INSTITUTION_TABLE, Locinst.class, criterion, true, false, getReply -> {
          if (getReply.failed()) {
            String message = StorageHelper.logAndSaveError(getReply.cause());
            asyncResultHandler.handle(Future.succeededFuture(
              LocationUnitsResource.GetLocationUnitsInstitutionsByIdResponse
                .withPlainInternalServerError(message)));
          } else {
            List<Locinst> instlist = (List<Locinst>) getReply.result().getResults();
            if (instlist.isEmpty()) {
              asyncResultHandler.handle(Future.succeededFuture(
                LocationUnitsResource.GetLocationUnitsInstitutionsByIdResponse
                  .withPlainNotFound(
                    messages.getMessage(lang, MessageConsts.ObjectDoesNotExist))));
              // We can safely ignore the case that we have more than one with
              // the same id, RMB has a primary key on ID, will not allow it
            } else {
              asyncResultHandler.handle(Future.succeededFuture(
                LocationUnitsResource.GetLocationUnitsInstitutionsByIdResponse
                  .withJsonOK(instlist.get(0))));
            }
          }
        });
  }

  @Override
  public void deleteLocationUnitsInstitutionsById(String id,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {
    String tenantId = StorageHelper.getTenant(okapiHeaders);
    Criterion criterion = StorageHelper.idCriterion(id, INST_SCHEMA_PATH, asyncResultHandler);
    if (criterion == null) {
      return; // error already handled
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .delete(INSTITUTION_TABLE, criterion, deleteReply -> {
        if (deleteReply.failed()) {
          StorageHelper.logAndSaveError(deleteReply.cause());
          if (StorageHelper.isInUse(deleteReply.cause().getMessage())) {
            asyncResultHandler.handle(Future.succeededFuture(
              LocationUnitsResource.DeleteLocationUnitsInstitutionsByIdResponse
                .withPlainBadRequest("Institution is in use, can not be deleted")));
          } else {
          asyncResultHandler.handle(Future.succeededFuture(
            LocationUnitsResource.DeleteLocationUnitsInstitutionsByIdResponse
                .withPlainNotFound("Institution not found")));
          }
        } else {
          asyncResultHandler.handle(Future.succeededFuture(
            LocationUnitsResource.DeleteLocationUnitsInstitutionsByIdResponse
              .withNoContent()));
        }
      });
  }

  @Override
  public void putLocationUnitsInstitutionsById(
    String id,
    String lang, Locinst entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    if (!id.equals(entity.getId())) {
      String message = "Illegal operation: Institution id cannot be changed";
      asyncResultHandler.handle(Future.succeededFuture(
        LocationUnitsResource.PutLocationUnitsInstitutionsByIdResponse
          .withPlainBadRequest(message)));
      return;
    }
    String tenantId = StorageHelper.getTenant(okapiHeaders);
    Criterion criterion = StorageHelper.idCriterion(id, INST_SCHEMA_PATH, asyncResultHandler);
    if (criterion == null) {
      return; // error already handled
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .update(INSTITUTION_TABLE, entity, criterion,
        false, updateReply -> {
          if (updateReply.failed()) {
            String message = StorageHelper.logAndSaveError(updateReply.cause());
            asyncResultHandler.handle(Future.succeededFuture(
              LocationUnitsResource.PutLocationUnitsInstitutionsByIdResponse
                .withPlainInternalServerError(message)));
          } else {
            if (updateReply.result().getUpdated() == 0) {
              asyncResultHandler.handle(Future.succeededFuture(
                LocationUnitsResource.PutLocationUnitsInstitutionsByIdResponse
                  .withPlainNotFound("Institution not found")));
            } else {
              asyncResultHandler.handle(Future.succeededFuture(
                LocationUnitsResource.PutLocationUnitsInstitutionsByIdResponse
                  .withNoContent()));
            }
          }
        });
  }

  ////////////////////////////////////////////
  @Override
  public void deleteLocationUnitsCampuses(String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {
    String tenantId = TenantTool.tenantId(okapiHeaders);
    PostgresClient.getInstance(vertxContext.owner(),
      TenantTool.calculateTenantId(tenantId))
      .mutate(String.format("DELETE FROM %s_%s.%s",
        tenantId, MOD_NAME, CAMPUS_TABLE),
        reply -> {
          if (reply.succeeded()) {
            asyncResultHandler.handle(Future.succeededFuture(
              LocationUnitsResource.DeleteLocationUnitsCampusesResponse.noContent()
                .build()));
          } else {
            asyncResultHandler.handle(Future.succeededFuture(
              LocationUnitsResource.DeleteLocationUnitsCampusesResponse
                .withPlainInternalServerError(reply.cause().getMessage())));
          }
        });
  }

  @Override
  public void getLocationUnitsCampuses(
    String query, int offset, int limit,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    String tenantId = StorageHelper.getTenant(okapiHeaders);
    CQLWrapper cql;
    try {
      cql = StorageHelper.getCQL(query, limit, offset, CAMPUS_TABLE);
    } catch (Exception e) {
      String message = StorageHelper.logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
        LocationUnitsResource.GetLocationUnitsCampusesResponse
          .withPlainInternalServerError(message)));
      return;
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .get(CAMPUS_TABLE, Loccamp.class, new String[]{"*"},
        cql, true, true, reply -> {
            if (reply.failed()) {
              String message = StorageHelper.logAndSaveError(reply.cause());
              asyncResultHandler.handle(Future.succeededFuture(
                LocationUnitsResource.GetLocationUnitsCampusesResponse
                  .withPlainBadRequest(message)));
            } else {
              Loccamps camps = new Loccamps();
              List<Loccamp> items = (List<Loccamp>) reply.result().getResults();
              camps.setLoccamps(items);
              camps.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
              asyncResultHandler.handle(Future.succeededFuture(
                LocationUnitsResource.GetLocationUnitsCampusesResponse.withJsonOK(camps)));
            }
          });
  }

  @Override
  public void postLocationUnitsCampuses(String lang,
    Loccamp entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    String tenantId = StorageHelper.getTenant(okapiHeaders);
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
              LocationUnitsResource.PostLocationUnitsCampusesResponse
                .withJsonUnprocessableEntity(
                  ValidationHelper.createValidationErrorMessage(
                    "loccamp", entity.getId(),
                    "Campus already exists"))));
          } else {
            asyncResultHandler.handle(Future.succeededFuture(
              LocationUnitsResource.PostLocationUnitsCampusesResponse
                .withPlainInternalServerError(message)));
          }
        } else {
          Object responseObject = reply.result();
          entity.setId((String) responseObject);
          OutStream stream = new OutStream();
          stream.setData(entity);
          asyncResultHandler.handle(Future.succeededFuture(
            LocationUnitsResource.PostLocationUnitsCampusesResponse
              .withJsonCreated(URL_PREFIX + responseObject, stream)));
        }
      });
  }

  @Override
  public void getLocationUnitsCampusesById(String id,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    String tenantId = StorageHelper.getTenant(okapiHeaders);
    Criterion criterion = StorageHelper.idCriterion(id, CAMP_SCHEMA_PATH, asyncResultHandler);
    if (criterion == null) {
      return; // error already handled
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .get(CAMPUS_TABLE, Loccamp.class, criterion,
        true, false, getReply -> {
          if (getReply.failed()) {
            String message = StorageHelper.logAndSaveError(getReply.cause());
            asyncResultHandler.handle(Future.succeededFuture(
              LocationUnitsResource.GetLocationUnitsCampusesByIdResponse
                .withPlainInternalServerError(message)));
          } else {
            List<Loccamp> items = (List<Loccamp>) getReply.result().getResults();
            if (items.isEmpty()) {
              asyncResultHandler.handle(Future.succeededFuture(
                LocationUnitsResource.GetLocationUnitsCampusesByIdResponse
                  .withPlainNotFound(
                    messages.getMessage(lang, MessageConsts.ObjectDoesNotExist))));
            } else {
              asyncResultHandler.handle(Future.succeededFuture(
                LocationUnitsResource.GetLocationUnitsCampusesByIdResponse
                  .withJsonOK(items.get(0))));
            }
          }
        });
  }

  @Override
  public void deleteLocationUnitsCampusesById(String id,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    String tenantId = StorageHelper.getTenant(okapiHeaders);
    Criterion criterion = StorageHelper.idCriterion(id, CAMP_SCHEMA_PATH, asyncResultHandler);
    if (criterion == null) {
      return; // error already handled
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .delete(CAMPUS_TABLE, criterion, deleteReply -> {
        if (deleteReply.failed()) {
          StorageHelper.logAndSaveError(deleteReply.cause());
          if (StorageHelper.isInUse(deleteReply.cause().getMessage())) {
            asyncResultHandler.handle(Future.succeededFuture(
              LocationUnitsResource.DeleteLocationUnitsInstitutionsByIdResponse
                .withPlainBadRequest("Campus is in use, can not be deleted")));
          } else {
            asyncResultHandler.handle(Future.succeededFuture(
              LocationUnitsResource.DeleteLocationUnitsCampusesByIdResponse
                .withPlainNotFound("Campus not found")));
          }
        } else {
          asyncResultHandler.handle(Future.succeededFuture(
            LocationUnitsResource.DeleteLocationUnitsCampusesByIdResponse
              .withNoContent()));
        }
      });
  }

  @Override
  public void putLocationUnitsCampusesById(
    String id,
    String lang, Loccamp entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {
    if (!id.equals(entity.getId())) {
      String message = "Illegal operation:Campus  id cannot be changed";
        asyncResultHandler.handle(Future.succeededFuture(
          LocationUnitsResource.PutLocationUnitsCampusesByIdResponse
            .withPlainBadRequest(message)));
        return;
      }
    String tenantId = StorageHelper.getTenant(okapiHeaders);
    Criterion criterion = StorageHelper.idCriterion(id, CAMP_SCHEMA_PATH, asyncResultHandler);
    if (criterion == null) {
      return; // error already handled
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .update(CAMPUS_TABLE, entity, criterion,
        false, updateReply -> {
          if (updateReply.failed()) {
            String message = StorageHelper.logAndSaveError(updateReply.cause());
            asyncResultHandler.handle(Future.succeededFuture(
              LocationUnitsResource.PutLocationUnitsCampusesByIdResponse
                .withPlainInternalServerError(message)));
          } else {
            if (updateReply.result().getUpdated() == 0) {
              asyncResultHandler.handle(Future.succeededFuture(
                LocationUnitsResource.PutLocationUnitsCampusesByIdResponse
                  .withPlainNotFound("Campus not found")));
            } else {
              asyncResultHandler.handle(Future.succeededFuture(
                LocationUnitsResource.PutLocationUnitsCampusesByIdResponse
                  .withNoContent()));
            }
          }
        });
  }

  ////////////////////////////////////////////
  @Override
  public void deleteLocationUnitsLibraries(String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {
    String tenantId = TenantTool.tenantId(okapiHeaders);
    PostgresClient postgresClient = PostgresClient.getInstance(
      vertxContext.owner(), TenantTool.calculateTenantId(tenantId));
    postgresClient.mutate(String.format("DELETE FROM %s_%s.%s",
      tenantId, MOD_NAME, LIBRARY_TABLE),
      reply -> {
        if (reply.succeeded()) {
          asyncResultHandler.handle(Future.succeededFuture(
            LocationUnitsResource.DeleteLocationUnitsLibrariesResponse.noContent()
              .build()));
        } else {
          asyncResultHandler.handle(Future.succeededFuture(
            LocationUnitsResource.DeleteLocationUnitsLibrariesResponse
              .withPlainInternalServerError(reply.cause().getMessage())));
        }
      });
  }

  @Override
  public void getLocationUnitsLibraries(
    String query, int offset, int limit,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    String tenantId = StorageHelper.getTenant(okapiHeaders);
    CQLWrapper cql;
    try {
      cql = StorageHelper.getCQL(query, limit, offset, LIBRARY_TABLE);
    } catch (Exception e) {
      String message = StorageHelper.logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
        LocationUnitsResource.GetLocationUnitsLibrariesResponse
          .withPlainInternalServerError(message)));
      return;
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .get(LIBRARY_TABLE, Loclib.class, new String[]{"*"},
        cql, true, true, reply -> {
          if (reply.failed()) {
            String message = StorageHelper.logAndSaveError(reply.cause());
            asyncResultHandler.handle(Future.succeededFuture(
              LocationUnitsResource.GetLocationUnitsLibrariesResponse
                .withPlainBadRequest(message)));
          } else {
            Loclibs lib = new Loclibs();
            List<Loclib> items = (List<Loclib>) reply.result().getResults();
            lib.setLoclibs(items);
            lib.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
            asyncResultHandler.handle(Future.succeededFuture(
              LocationUnitsResource.GetLocationUnitsLibrariesResponse.withJsonOK(lib)));
          }
        });
  }

  @Override
  public void postLocationUnitsLibraries(String lang,
    Loclib entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    String tenantId = StorageHelper.getTenant(okapiHeaders);
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
              LocationUnitsResource.PostLocationUnitsLibrariesResponse
                .withJsonUnprocessableEntity(
                  ValidationHelper.createValidationErrorMessage(
                    "loclib", entity.getId(),
                    "Library already exists"))));
          } else {
            asyncResultHandler.handle(Future.succeededFuture(
              LocationUnitsResource.PostLocationUnitsLibrariesResponse
                .withPlainInternalServerError(message)));
          }
        } else {
          Object responseObject = reply.result();
          entity.setId((String) responseObject);
          OutStream stream = new OutStream();
          stream.setData(entity);
          asyncResultHandler.handle(Future.succeededFuture(
            LocationUnitsResource.PostLocationUnitsLibrariesResponse
              .withJsonCreated(URL_PREFIX + responseObject, stream)));
        }
      });
  }

  @Override
  public void getLocationUnitsLibrariesById(String id,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    String tenantId = StorageHelper.getTenant(okapiHeaders);
    Criterion criterion = StorageHelper.idCriterion(id, LIB_SCHEMA_PATH, asyncResultHandler);
    if (criterion == null) {
      return; // error already handled
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .get(LIBRARY_TABLE, Loclib.class, criterion,
        true, false, getReply -> {
          if (getReply.failed()) {
            String message = StorageHelper.logAndSaveError(getReply.cause());
            asyncResultHandler.handle(Future.succeededFuture(
              LocationUnitsResource.GetLocationUnitsLibrariesByIdResponse
                .withPlainInternalServerError(message)));
          } else {
            List<Loclib> items = (List<Loclib>) getReply.result().getResults();
            if (items.isEmpty()) {
              asyncResultHandler.handle(Future.succeededFuture(
                LocationUnitsResource.GetLocationUnitsLibrariesByIdResponse
                  .withPlainNotFound(
                    messages.getMessage(lang, MessageConsts.ObjectDoesNotExist))));
            } else {
              asyncResultHandler.handle(Future.succeededFuture(
                LocationUnitsResource.GetLocationUnitsLibrariesByIdResponse
                  .withJsonOK(items.get(0))));
            }
          }
        });
  }

  @Override
  public void deleteLocationUnitsLibrariesById(String id,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    String tenantId = StorageHelper.getTenant(okapiHeaders);
    Criterion criterion = StorageHelper.idCriterion(id, LIB_SCHEMA_PATH, asyncResultHandler);
    if (criterion == null) {
      return; // error already handled
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .delete(LIBRARY_TABLE, criterion, deleteReply -> {
        if (deleteReply.failed()) {
          StorageHelper.logAndSaveError(deleteReply.cause());
          asyncResultHandler.handle(Future.succeededFuture(
            LocationUnitsResource.DeleteLocationUnitsLibrariesByIdResponse
              .withPlainNotFound("Library not found")));
        } else {
          asyncResultHandler.handle(Future.succeededFuture(
            LocationUnitsResource.DeleteLocationUnitsLibrariesByIdResponse
              .withNoContent()));
        }
      });
  }

  @Override
  public void putLocationUnitsLibrariesById(
    String id,
    String lang, Loclib entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {
    if (!id.equals(entity.getId())) {
      String message = "Illegal operation: Library id cannot be changed";
      asyncResultHandler.handle(Future.succeededFuture(
        LocationUnitsResource.PutLocationUnitsLibrariesByIdResponse
          .withPlainBadRequest(message)));
      return;
    }
    String tenantId = StorageHelper.getTenant(okapiHeaders);
    Criterion criterion = StorageHelper.idCriterion(id, LIB_SCHEMA_PATH, asyncResultHandler);
    if (criterion == null) {
      return; // error already handled
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .update(LIBRARY_TABLE, entity, criterion,
        false, updateReply -> {
          if (updateReply.failed()) {
            String message = StorageHelper.logAndSaveError(updateReply.cause());
            asyncResultHandler.handle(Future.succeededFuture(
              LocationUnitsResource.PutLocationUnitsLibrariesByIdResponse
                .withPlainInternalServerError(message)));
          } else {
            if (updateReply.result().getUpdated() == 0) {
              asyncResultHandler.handle(Future.succeededFuture(
                LocationUnitsResource.PutLocationUnitsLibrariesByIdResponse
                  .withPlainNotFound("Library not found")));
            } else {
              asyncResultHandler.handle(Future.succeededFuture(
                LocationUnitsResource.PutLocationUnitsLibrariesByIdResponse
                  .withNoContent()));
            }
          }
        });
  }

}
