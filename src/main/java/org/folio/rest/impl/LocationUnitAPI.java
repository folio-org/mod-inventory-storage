/*
 * Copyright (c) 2015-2018, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.ws.rs.core.Response;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.Locinst;
import org.folio.rest.jaxrs.model.Locinsts;
import org.folio.rest.jaxrs.resource.LocationUnitsResource;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;


public class LocationUnitAPI implements LocationUnitsResource {
  private final Messages messages = Messages.getInstance();
  public static final String INSTITUTION_TABLE = "locinstitution";
  private final Logger logger = LoggerFactory.getLogger(LocationUnitAPI.class);
  public static final String URL_PREFIX = "/location-units";
  public static final String URL_PREFIX_INST = URL_PREFIX + "/institutions";
  public static final String INST_SCHEMA_PATH = "apidocs/raml/locinst.json";
  public static final String ID_FIELD_NAME = "'id'";

  private String getErrorResponse(String response) {
    //Check to see if we're suppressing messages or not
    return response;
  }

  private String logAndSaveError(Throwable err) {
    String message = err.getLocalizedMessage();
    logger.error(message, err);
    return message;
  }

  private boolean isDuplicate(String message) {
    if (message != null && message.contains("duplicate key value violates unique constraint")) {
      return true;
    }
    return false;
  }

  private CQLWrapper getCQL(String query, int limit, int offset, String tableName) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(tableName + ".jsonb");
    return new CQLWrapper(cql2pgJson, query).setLimit(new Limit(limit)).setOffset(new Offset(offset));
  }

  private String getTenant(Map<String, String> headers) {
    return TenantTool.calculateTenantId(headers.get(RestVerticle.OKAPI_HEADER_TENANT));
  }

  @Override
  public void deleteLocationUnitsInstitutions(String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {
    String tenantId = TenantTool.tenantId(okapiHeaders);
    try {
      vertxContext.runOnContext(v -> {
        PostgresClient postgresClient = PostgresClient.getInstance(
          vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

        postgresClient.mutate(String.format("DELETE FROM %s_%s.%s",
          tenantId, "mod_inventory_storage", INSTITUTION_TABLE),
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
      });
    } catch (Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(
        LocationUnitsResource.DeleteLocationUnitsInstitutionsResponse
          .withPlainInternalServerError(e.getMessage())));
    }
  }

  @Override
  public void getLocationUnitsInstitutions(
    String query, int offset, int limit,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = getTenant(okapiHeaders);
        CQLWrapper cql = getCQL(query, limit, offset, INSTITUTION_TABLE);
        PostgresClient.getInstance(vertxContext.owner(), tenantId)
          .get(
            INSTITUTION_TABLE, Locinst.class, new String[]{"*"},
            cql, true, true, reply -> {
              try {
                if (reply.failed()) {
                  String message = logAndSaveError(reply.cause());
                  asyncResultHandler.handle(Future.succeededFuture(
                    LocationUnitsResource.GetLocationUnitsInstitutionsResponse
                      .withPlainBadRequest(getErrorResponse(message))));
                } else {
                  Locinsts insts = new Locinsts();
                  List<Locinst> shelfLocationsList = (List<Locinst>) reply.result().getResults();
                  insts.setLocinsts(shelfLocationsList);
                  insts.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
                  asyncResultHandler.handle(Future.succeededFuture(
                    LocationUnitsResource.GetLocationUnitsInstitutionsResponse.withJsonOK(insts)));
                }
              } catch (Exception e) {
                String message = logAndSaveError(e);
                asyncResultHandler.handle(Future.succeededFuture(
                  LocationUnitsResource.GetLocationUnitsInstitutionsResponse
                    .withPlainInternalServerError(getErrorResponse(message))));
              }
            });
      } catch (Exception e) {
        String message = logAndSaveError(e);
        asyncResultHandler.handle(Future.succeededFuture(
          LocationUnitsResource.GetLocationUnitsInstitutionsResponse
            .withPlainInternalServerError(getErrorResponse(message))));
      }
    });
  }

  @Override
  public void postLocationUnitsInstitutions(String lang,
    Locinst entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = getTenant(okapiHeaders);
        String id = entity.getId();
        if (id == null) {
          id = UUID.randomUUID().toString();
          entity.setId(id);
        }
        PostgresClient.getInstance(vertxContext.owner(), tenantId).save(INSTITUTION_TABLE, id, entity, reply -> {
          try {
            if (reply.failed()) {
              String message = logAndSaveError(reply.cause());
              if (isDuplicate(message)) {
                asyncResultHandler.handle(Future.succeededFuture(
                  LocationUnitsResource.PostLocationUnitsInstitutionsResponse
                    .withJsonUnprocessableEntity(
                      ValidationHelper.createValidationErrorMessage(
                      "locinst", entity.getId(),
                      "Institution already exists"))));
              } else {
                asyncResultHandler.handle(Future.succeededFuture(
                  LocationUnitsResource.PostLocationUnitsInstitutionsResponse
                    .withPlainInternalServerError(getErrorResponse(message))));
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
          } catch (Exception e) {
            String message = logAndSaveError(e);
            asyncResultHandler.handle(Future.succeededFuture(
              LocationUnitsResource.PostLocationUnitsInstitutionsResponse
                .withPlainInternalServerError(getErrorResponse(message))));
          }
        });
      } catch (Exception e) {
        String message = logAndSaveError(e);
        asyncResultHandler.handle(Future.succeededFuture(
          LocationUnitsResource.PostLocationUnitsInstitutionsResponse
            .withPlainInternalServerError(getErrorResponse(message))));
      }
    });
  }

  @Override
  public void getLocationUnitsInstitutionsById(String id,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = getTenant(okapiHeaders);
        Criteria criteria = new Criteria(INST_SCHEMA_PATH);
        criteria.addField(ID_FIELD_NAME);
        criteria.setOperation("=");
        criteria.setValue(id);
        Criterion criterion = new Criterion(criteria);
        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(
          INSTITUTION_TABLE, Locinst.class, criterion, true,
          false, getReply -> {
            if (getReply.failed()) {
              String message = logAndSaveError(getReply.cause());
              asyncResultHandler.handle(Future.succeededFuture(
                LocationUnitsResource.GetLocationUnitsInstitutionsByIdResponse
                  .withPlainInternalServerError(getErrorResponse(message))));
            } else {
              List<Locinst> instlist = (List<Locinst>) getReply.result().getResults();
              if (instlist.size() < 1) {
                asyncResultHandler.handle(Future.succeededFuture(
                  LocationUnitsResource.GetLocationUnitsInstitutionsByIdResponse
                    .withPlainNotFound(
                      messages.getMessage(lang, MessageConsts.ObjectDoesNotExist))));
              } else if (instlist.size() > 1) {
                String message = "Multiple locations found with the same id";
                logger.error(message);
                asyncResultHandler.handle(Future.succeededFuture(
                  LocationUnitsResource.GetLocationUnitsInstitutionsByIdResponse
                    .withPlainInternalServerError(getErrorResponse(message))));
              } else {
                asyncResultHandler.handle(Future.succeededFuture(
                  LocationUnitsResource.GetLocationUnitsInstitutionsByIdResponse
                    .withJsonOK(instlist.get(0))));
              }
            }
          });
      } catch (Exception e) {
        String message = logAndSaveError(e);
        asyncResultHandler.handle(Future.succeededFuture(
          LocationUnitsResource.GetLocationUnitsInstitutionsByIdResponse
            .withPlainInternalServerError(
              getErrorResponse(message))));
      }
    });
  }

  @Override
  public void deleteLocationUnitsInstitutionsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = getTenant(okapiHeaders);
        Criteria criteria = new Criteria(INST_SCHEMA_PATH);
        criteria.addField(ID_FIELD_NAME);
        criteria.setOperation("=");
        criteria.setValue(id);
        Criterion criterion = new Criterion(criteria);
        try {
          instInUse(id, tenantId, vertxContext).setHandler(res -> {
            if (res.failed()) {
              String message = logAndSaveError(res.cause());
              LocationUnitsResource.DeleteLocationUnitsInstitutionsByIdResponse
                .withPlainInternalServerError(getErrorResponse(message));
            } else {
              if (res.result()) {
                asyncResultHandler.handle(Future.succeededFuture(
                  LocationUnitsResource.DeleteLocationUnitsInstitutionsByIdResponse
                    .withPlainBadRequest("Cannot delete location, as it is in use")));
              } else {
                try {
                  PostgresClient.getInstance(vertxContext.owner(), tenantId).delete(INSTITUTION_TABLE, criterion, deleteReply -> {
                    if (deleteReply.failed()) {
                      String message = logAndSaveError(deleteReply.cause());
                      asyncResultHandler.handle(Future.succeededFuture(
                        LocationUnitsResource.DeleteLocationUnitsInstitutionsByIdResponse
                          .withPlainNotFound("Not found")));
                    } else {
                      asyncResultHandler.handle(Future.succeededFuture(
                        LocationUnitsResource.DeleteLocationUnitsInstitutionsByIdResponse
                          .withNoContent()));
                    }
                  });
                } catch (Exception e) {
                  String message = logAndSaveError(e);
                  asyncResultHandler.handle(Future.succeededFuture(
                    LocationUnitsResource.DeleteLocationUnitsInstitutionsByIdResponse
                      .withPlainInternalServerError(getErrorResponse(message))));
                }
              }
            }
          });
        } catch (Exception e) {
          String message = logAndSaveError(e);
          asyncResultHandler.handle(Future.succeededFuture(
            LocationUnitsResource.DeleteLocationUnitsInstitutionsByIdResponse
              .withPlainInternalServerError(
                getErrorResponse(message))));
        }
      } catch (Exception e) {
        String message = logAndSaveError(e);
        asyncResultHandler.handle(Future.succeededFuture(
          LocationUnitsResource.DeleteLocationUnitsInstitutionsByIdResponse
            .withPlainInternalServerError(
              getErrorResponse(message))));
      }
    });
  }

  @Override
  public void putLocationUnitsInstitutionsById(String id, String lang, Locinst entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    vertxContext.runOnContext(v -> {
      try {
        if (!id.equals(entity.getId())) {
          String message = "Illegal operation: id cannot be changed";
          asyncResultHandler.handle(Future.succeededFuture(
            LocationUnitsResource.PutLocationUnitsInstitutionsByIdResponse
              .withPlainBadRequest(message)));
          return;
        }
        String tenantId = getTenant(okapiHeaders);
        Criteria criteria = new Criteria(INST_SCHEMA_PATH);
        criteria.addField(ID_FIELD_NAME);
        criteria.setOperation("=");
        criteria.setValue(id);
        Criterion criterion = new Criterion(criteria);
        try {
          PostgresClient.getInstance(vertxContext.owner(), tenantId).update(
            INSTITUTION_TABLE, entity, criterion, false, updateReply -> {
              if (updateReply.failed()) {
                String message = logAndSaveError(updateReply.cause());
                asyncResultHandler.handle(Future.succeededFuture(
                  LocationUnitsResource.PutLocationUnitsInstitutionsByIdResponse
                    .withPlainInternalServerError(getErrorResponse(message))));
              } else {
                if (updateReply.result().getUpdated() == 0) {
                  asyncResultHandler.handle(Future.succeededFuture(
                    LocationUnitsResource.PutLocationUnitsInstitutionsByIdResponse
                      .withPlainNotFound("Not found")));
                  //Not found
                } else {
                  asyncResultHandler.handle(Future.succeededFuture(
                    LocationUnitsResource.PutLocationUnitsInstitutionsByIdResponse
                      .withNoContent()));
                }
              }
            });
        } catch (Exception e) {
          String message = logAndSaveError(e);
          asyncResultHandler.handle(Future.succeededFuture(
            LocationUnitsResource.PutLocationUnitsInstitutionsByIdResponse
              .withPlainInternalServerError(getErrorResponse(message))));
        }
      } catch (Exception e) {
        String message = logAndSaveError(e);
        asyncResultHandler.handle(Future.succeededFuture(
          LocationUnitsResource.PutLocationUnitsInstitutionsByIdResponse
            .withPlainInternalServerError(getErrorResponse(message))));
      }
    });
  }

  // TODO - Check that the institution is not used by any locateion object
  // or by any campus item points here.
  Future<Boolean> instInUse(String locationId, String tenantId, Context vertxContext) {
    Future<Boolean> future = Future.future();
    future.complete(false);
    return future;
  }

  Future<Boolean> instInUseOLDXXXX(String locationId, String tenantId, Context vertxContext) {
    Future<Boolean> future = Future.future();
    //Get all items where the temporary future or permanent future is this location id
    String query = "permanentLocation == " + locationId + " OR temporarylocation == " + locationId;
    vertxContext.runOnContext(v -> {
      try {
        CQLWrapper cql = getCQL(query, 10, 0, ItemStorageAPI.ITEM_TABLE);
        String[] fieldList = {"*"};
        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(
          ItemStorageAPI.ITEM_TABLE, Item.class, fieldList, cql, true, false,
          getReply -> {
            if (getReply.failed()) {
              future.fail(getReply.cause());
            } else {
              List<Item> itemList = (List<Item>) getReply.result().getResults();
              if (itemList.isEmpty()) {
                future.complete(false);
              } else {
                future.complete(true);
              }
            }
          });
      } catch (Exception e) {
        future.fail(e);
      }
    });
    return future;
  }

}
