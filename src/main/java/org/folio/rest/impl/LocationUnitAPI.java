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
import org.folio.rest.jaxrs.resource.LocationUnits;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;

public class LocationUnitAPI implements LocationUnits {
  private final Messages messages = Messages.getInstance();
  public static final String URL_PREFIX = "/location-units";
  public static final String ID_FIELD_NAME = "'id'"; // same for all of them
  public static final String INSTITUTION_TABLE = "locinstitution";
  public static final String URL_PREFIX_INST = URL_PREFIX + "/institutions";
  public static final String CAMPUS_TABLE = "loccampus";
  public static final String URL_PREFIX_CAMP = URL_PREFIX + "/campuses";
  public static final String LIBRARY_TABLE = "loclibrary";
  public static final String URL_PREFIX_LIB = URL_PREFIX + "/libraries";
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
              DeleteLocationUnitsInstitutionsResponse.noContent().build()));
          } else {
            asyncResultHandler.handle(Future.succeededFuture(
              DeleteLocationUnitsInstitutionsResponse
                .respond500WithTextPlain(reply.cause().getMessage())));
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
        GetLocationUnitsInstitutionsResponse
          .respond500WithTextPlain(message)));
      return;
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .get(INSTITUTION_TABLE, Locinst.class, new String[]{"*"},
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

  @Override
  public void getLocationUnitsInstitutionsById(String id,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    String tenantId = StorageHelper.getTenant(okapiHeaders);
    Criterion criterion = StorageHelper.idCriterion(id, asyncResultHandler);
    if (criterion == null) {
      return; // error already handled
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .get(INSTITUTION_TABLE, Locinst.class, criterion, true, false, getReply -> {
          if (getReply.failed()) {
            String message = StorageHelper.logAndSaveError(getReply.cause());
            asyncResultHandler.handle(Future.succeededFuture(
              GetLocationUnitsInstitutionsByIdResponse
                .respond500WithTextPlain(message)));
          } else {
            List<Locinst> instlist = getReply.result().getResults();
            if (instlist.isEmpty()) {
              asyncResultHandler.handle(Future.succeededFuture(
                GetLocationUnitsInstitutionsByIdResponse
                  .respond404WithTextPlain(
                    messages.getMessage(lang, MessageConsts.ObjectDoesNotExist))));
              // We can safely ignore the case that we have more than one with
              // the same id, RMB has a primary key on ID, will not allow it
            } else {
              asyncResultHandler.handle(Future.succeededFuture(
                GetLocationUnitsInstitutionsByIdResponse
                  .respond200WithApplicationJson(instlist.get(0))));
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
    Criterion criterion = StorageHelper.idCriterion(id, asyncResultHandler);
    if (criterion == null) {
      return; // error already handled
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .delete(INSTITUTION_TABLE, criterion, deleteReply -> {
        if (deleteReply.failed()) {
          StorageHelper.logAndSaveError(deleteReply.cause());
          if (StorageHelper.isInUse(deleteReply.cause().getMessage())) {
            asyncResultHandler.handle(Future.succeededFuture(
              DeleteLocationUnitsInstitutionsByIdResponse
                .respond400WithTextPlain("Institution is in use, can not be deleted")));
          } else {
          asyncResultHandler.handle(Future.succeededFuture(
            DeleteLocationUnitsInstitutionsByIdResponse
                .respond404WithTextPlain("Institution not found")));
          }
        } else {
          asyncResultHandler.handle(Future.succeededFuture(
            DeleteLocationUnitsInstitutionsByIdResponse.respond204()));
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
        PutLocationUnitsInstitutionsByIdResponse
          .respond400WithTextPlain(message)));
      return;
    }
    String tenantId = StorageHelper.getTenant(okapiHeaders);
    Criterion criterion = StorageHelper.idCriterion(id, asyncResultHandler);
    if (criterion == null) {
      return; // error already handled
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .update(INSTITUTION_TABLE, entity, criterion,
        false, updateReply -> {
          if (updateReply.failed()) {
            String message = StorageHelper.logAndSaveError(updateReply.cause());
            asyncResultHandler.handle(Future.succeededFuture(
              PutLocationUnitsInstitutionsByIdResponse.respond500WithTextPlain(message)));
          } else {
            if (updateReply.result().getUpdated() == 0) {
              asyncResultHandler.handle(Future.succeededFuture(
                PutLocationUnitsInstitutionsByIdResponse
                  .respond404WithTextPlain("Institution not found")));
            } else {
              asyncResultHandler.handle(Future.succeededFuture(
                PutLocationUnitsInstitutionsByIdResponse.respond204()));
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
              DeleteLocationUnitsCampusesResponse.noContent()
                .build()));
          } else {
            asyncResultHandler.handle(Future.succeededFuture(
              DeleteLocationUnitsCampusesResponse
                .respond500WithTextPlain(reply.cause().getMessage())));
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
        GetLocationUnitsCampusesResponse
          .respond500WithTextPlain(message)));
      return;
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .get(CAMPUS_TABLE, Loccamp.class, new String[]{"*"},
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
              .respond201WithApplicationJson(entity, PostLocationUnitsCampusesResponse.headersFor201().withLocation(URL_PREFIX + responseObject))));
        }
      });
  }

  @Override
  public void getLocationUnitsCampusesById(String id,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    String tenantId = StorageHelper.getTenant(okapiHeaders);
    Criterion criterion = StorageHelper.idCriterion(id, asyncResultHandler);
    if (criterion == null) {
      return; // error already handled
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .get(CAMPUS_TABLE, Loccamp.class, criterion,
        true, false, getReply -> {
          if (getReply.failed()) {
            String message = StorageHelper.logAndSaveError(getReply.cause());
            asyncResultHandler.handle(Future.succeededFuture(
              GetLocationUnitsCampusesByIdResponse
                .respond500WithTextPlain(message)));
          } else {
            List<Loccamp> items = getReply.result().getResults();
            if (items.isEmpty()) {
              asyncResultHandler.handle(Future.succeededFuture(
                GetLocationUnitsCampusesByIdResponse
                  .respond404WithTextPlain(
                    messages.getMessage(lang, MessageConsts.ObjectDoesNotExist))));
            } else {
              asyncResultHandler.handle(Future.succeededFuture(
                GetLocationUnitsCampusesByIdResponse
                  .respond200WithApplicationJson(items.get(0))));
            }
          }
        });
  }

  @Override
  public void deleteLocationUnitsCampusesById(String id,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    String tenantId = StorageHelper.getTenant(okapiHeaders);
    Criterion criterion = StorageHelper.idCriterion(id, asyncResultHandler);
    if (criterion == null) {
      return; // error already handled
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .delete(CAMPUS_TABLE, criterion, deleteReply -> {
        if (deleteReply.failed()) {
          StorageHelper.logAndSaveError(deleteReply.cause());
          if (StorageHelper.isInUse(deleteReply.cause().getMessage())) {
            asyncResultHandler.handle(Future.succeededFuture(
              DeleteLocationUnitsInstitutionsByIdResponse
                .respond400WithTextPlain("Campus is in use, can not be deleted")));
          } else {
            asyncResultHandler.handle(Future.succeededFuture(
              DeleteLocationUnitsCampusesByIdResponse
                .respond404WithTextPlain("Campus not found")));
          }
        } else {
          asyncResultHandler.handle(Future.succeededFuture(
            DeleteLocationUnitsCampusesByIdResponse.respond204()));
        }
      });
  }

  @Override
  public void putLocationUnitsCampusesById(
    String id,
    String lang, Loccamp entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {
    if (!id.equals(entity.getId())) {
      String message = "Illegal operation:Campus  id cannot be changed";
        asyncResultHandler.handle(Future.succeededFuture(
          PutLocationUnitsCampusesByIdResponse.respond400WithTextPlain(message)));
        return;
      }
    String tenantId = StorageHelper.getTenant(okapiHeaders);
    Criterion criterion = StorageHelper.idCriterion(id, asyncResultHandler);
    if (criterion == null) {
      return; // error already handled
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .update(CAMPUS_TABLE, entity, criterion,
        false, updateReply -> {
          if (updateReply.failed()) {
            String message = StorageHelper.logAndSaveError(updateReply.cause());
            asyncResultHandler.handle(Future.succeededFuture(
              PutLocationUnitsCampusesByIdResponse
                .respond500WithTextPlain(message)));
          } else {
            if (updateReply.result().getUpdated() == 0) {
              asyncResultHandler.handle(Future.succeededFuture(
                PutLocationUnitsCampusesByIdResponse
                  .respond404WithTextPlain("Campus not found")));
            } else {
              asyncResultHandler.handle(Future.succeededFuture(
                PutLocationUnitsCampusesByIdResponse.respond204()));
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
            DeleteLocationUnitsLibrariesResponse.noContent()
              .build()));
        } else {
          asyncResultHandler.handle(Future.succeededFuture(
            DeleteLocationUnitsLibrariesResponse
              .respond500WithTextPlain(reply.cause().getMessage())));
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
        GetLocationUnitsLibrariesResponse
          .respond500WithTextPlain(message)));
      return;
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .get(LIBRARY_TABLE, Loclib.class, new String[]{"*"},
        cql, true, true, reply -> {
          if (reply.failed()) {
            String message = StorageHelper.logAndSaveError(reply.cause());
            asyncResultHandler.handle(Future.succeededFuture(
              GetLocationUnitsLibrariesResponse
                .respond400WithTextPlain(message)));
          } else {
            Loclibs lib = new Loclibs();
            List<Loclib> items = (List<Loclib>) reply.result().getResults();
            lib.setLoclibs(items);
            lib.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
            asyncResultHandler.handle(Future.succeededFuture(
              GetLocationUnitsLibrariesResponse.respond200WithApplicationJson(lib)));
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

  @Override
  public void getLocationUnitsLibrariesById(String id,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    String tenantId = StorageHelper.getTenant(okapiHeaders);
    Criterion criterion = StorageHelper.idCriterion(id, asyncResultHandler);
    if (criterion == null) {
      return; // error already handled
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .get(LIBRARY_TABLE, Loclib.class, criterion,
        true, false, getReply -> {
          if (getReply.failed()) {
            String message = StorageHelper.logAndSaveError(getReply.cause());
            asyncResultHandler.handle(Future.succeededFuture(
              GetLocationUnitsLibrariesByIdResponse
                .respond500WithTextPlain(message)));
          } else {
            List<Loclib> items = (List<Loclib>) getReply.result().getResults();
            if (items.isEmpty()) {
              asyncResultHandler.handle(Future.succeededFuture(
                GetLocationUnitsLibrariesByIdResponse
                  .respond404WithTextPlain(
                    messages.getMessage(lang, MessageConsts.ObjectDoesNotExist))));
            } else {
              asyncResultHandler.handle(Future.succeededFuture(
                GetLocationUnitsLibrariesByIdResponse
                  .respond200WithApplicationJson(items.get(0))));
            }
          }
        });
  }

  @Override
  public void deleteLocationUnitsLibrariesById(String id,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    String tenantId = StorageHelper.getTenant(okapiHeaders);
    Criterion criterion = StorageHelper.idCriterion(id, asyncResultHandler);
    if (criterion == null) {
      return; // error already handled
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .delete(LIBRARY_TABLE, criterion, deleteReply -> {
        if (deleteReply.failed()) {
          StorageHelper.logAndSaveError(deleteReply.cause());
          if (StorageHelper.isInUse(deleteReply.cause().getMessage())) {
            asyncResultHandler.handle(Future.succeededFuture(
              DeleteLocationUnitsLibrariesByIdResponse
                .respond400WithTextPlain("Library is in use, can not be deleted")));
          } else {
          asyncResultHandler.handle(Future.succeededFuture(
            DeleteLocationUnitsLibrariesByIdResponse.respond404WithTextPlain("Library not found")));
          }
        } else {
          asyncResultHandler.handle(Future.succeededFuture(
            DeleteLocationUnitsLibrariesByIdResponse.respond204()));
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
        PutLocationUnitsLibrariesByIdResponse.respond400WithTextPlain(message)));
      return;
    }
    String tenantId = StorageHelper.getTenant(okapiHeaders);
    Criterion criterion = StorageHelper.idCriterion(id, asyncResultHandler);
    if (criterion == null) {
      return; // error already handled
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .update(LIBRARY_TABLE, entity, criterion,
        false, updateReply -> {
          if (updateReply.failed()) {
            String message = StorageHelper.logAndSaveError(updateReply.cause());
            asyncResultHandler.handle(Future.succeededFuture(
              PutLocationUnitsLibrariesByIdResponse
                .respond500WithTextPlain(message)));
          } else {
            if (updateReply.result().getUpdated() == 0) {
              asyncResultHandler.handle(Future.succeededFuture(
                PutLocationUnitsLibrariesByIdResponse
                  .respond404WithTextPlain("Library not found")));
            } else {
              asyncResultHandler.handle(Future.succeededFuture(
                PutLocationUnitsLibrariesByIdResponse.respond204()));
            }
          }
        });
  }

}
