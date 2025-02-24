package org.folio.rest.impl;

import static org.folio.rest.tools.messages.Messages.DEFAULT_LANGUAGE;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.RestVerticle;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Mtype;
import org.folio.rest.jaxrs.model.Mtypes;
import org.folio.rest.jaxrs.resource.MaterialTypes;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.TenantTool;

public class MaterialTypeApi implements MaterialTypes {

  public static final String MATERIAL_TYPE_TABLE = "material_type";

  private static final String LOCATION_PREFIX = "/material-types/";
  private static final Logger log = LogManager.getLogger();
  private final Messages messages = Messages.getInstance();

  @Validate
  @Override
  public void getMaterialTypes(String query, String totalRecords, int offset, int limit,
                               Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
                               Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
        CQLWrapper cql = getCql(query, limit, offset);
        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(MATERIAL_TYPE_TABLE, Mtype.class,
          new String[] {"*"}, cql, true, true,
          reply -> {
            try {
              if (reply.succeeded()) {
                Mtypes mtypes = new Mtypes();
                List<Mtype> mtype = reply.result().getResults();
                mtypes.setMtypes(mtype);
                mtypes.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
                asyncResultHandler.handle(
                  io.vertx.core.Future.succeededFuture(GetMaterialTypesResponse.respond200WithApplicationJson(
                    mtypes)));
              } else {
                log.error(reply.cause().getMessage(), reply.cause());
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetMaterialTypesResponse
                  .respond400WithTextPlain(reply.cause().getMessage())));
              }
            } catch (Exception e) {
              log.error(e.getMessage(), e);
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetMaterialTypesResponse
                .respond500WithTextPlain(messages.getMessage(
                  DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
            }
          });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        String message = messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError);
        if (e.getCause() != null && e.getCause().getClass().getSimpleName().endsWith("CQLParseException")) {
          message = " CQL parse error " + e.getLocalizedMessage();
        }
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetMaterialTypesResponse
          .respond500WithTextPlain(message)));
      }
    });
  }

  @Validate
  @Override
  public void postMaterialTypes(Mtype entity, Map<String, String> okapiHeaders,
                                Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    vertxContext.runOnContext(v -> {
      try {
        String id = UUID.randomUUID().toString();
        if (entity.getId() == null) {
          entity.setId(id);
        } else {
          id = entity.getId();
        }

        String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
        PostgresClient.getInstance(vertxContext.owner(), tenantId).save(
          MATERIAL_TYPE_TABLE, id, entity,
          reply -> {
            try {
              if (reply.succeeded()) {
                String ret = reply.result();
                entity.setId(ret);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostMaterialTypesResponse
                  .respond201WithApplicationJson(entity,
                    PostMaterialTypesResponse.headersFor201().withLocation(LOCATION_PREFIX + ret))));
              } else {
                log.error(reply.cause().getMessage(), reply.cause());
                if (isDuplicate(reply.cause().getMessage())) {
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostMaterialTypesResponse
                    .respond422WithApplicationJson(
                      org.folio.rest.tools.utils.ValidationHelper.createValidationErrorMessage(
                        "name", entity.getName(), "Material Type exists"))));
                } else {
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostMaterialTypesResponse
                    .respond500WithTextPlain(
                      messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
                }
              }
            } catch (Exception e) {
              log.error(e.getMessage(), e);
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostMaterialTypesResponse
                .respond500WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
            }
          });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostMaterialTypesResponse
          .respond500WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
      }
    });
  }

  @Validate
  @Override
  public void deleteMaterialTypes(Map<String, String> okapiHeaders,
                                  Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    String tenantId = TenantTool.tenantId(okapiHeaders);

    try {
      vertxContext.runOnContext(v -> {
        PostgresClient postgresClient = PostgresClient.getInstance(
          vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

        postgresClient.execute(String.format("DELETE FROM %s_%s.%s",
            tenantId, "mod_inventory_storage", MATERIAL_TYPE_TABLE),
          reply -> {
            if (reply.succeeded()) {
              asyncResultHandler.handle(Future.succeededFuture(DeleteMaterialTypesResponse.respond204()));
            } else {
              asyncResultHandler.handle(Future.succeededFuture(
                DeleteMaterialTypesResponse.respond500WithTextPlain(reply.cause().getMessage())));
            }
          });
      });
    } catch (Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(
        DeleteMaterialTypesResponse.respond500WithTextPlain(e.getMessage())));
    }
  }

  @Validate
  @Override
  public void getMaterialTypesByMaterialtypeId(String materialtypeId,
                                               Map<String, String> okapiHeaders,
                                               Handler<AsyncResult<Response>> asyncResultHandler,
                                               Context vertxContext) {

    PgUtil.getById(MATERIAL_TYPE_TABLE, Mtype.class, materialtypeId, okapiHeaders, vertxContext,
      GetMaterialTypesByMaterialtypeIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteMaterialTypesByMaterialtypeId(String materialtypeId,
                                                  Map<String, String> okapiHeaders,
                                                  Handler<AsyncResult<Response>> asyncResultHandler,
                                                  Context vertxContext) {
    PgUtil.deleteById(MATERIAL_TYPE_TABLE, materialtypeId, okapiHeaders, vertxContext,
      DeleteMaterialTypesByMaterialtypeIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void putMaterialTypesByMaterialtypeId(String materialtypeId, Mtype entity,
                                               Map<String, String> okapiHeaders,
                                               Handler<AsyncResult<Response>> asyncResultHandler,
                                               Context vertxContext) {

    vertxContext.runOnContext(v -> {
      String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
      try {
        if (entity.getId() == null) {
          entity.setId(materialtypeId);
        }
        PostgresClient.getInstance(vertxContext.owner(), tenantId).update(
          MATERIAL_TYPE_TABLE, entity, materialtypeId,
          reply -> {
            try {
              if (reply.succeeded()) {
                if (reply.result().rowCount() == 0) {
                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(PutMaterialTypesByMaterialtypeIdResponse
                      .respond404WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.NoRecordsUpdated))));
                } else {
                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(PutMaterialTypesByMaterialtypeIdResponse
                      .respond204()));
                }
              } else {
                log.error(reply.cause().getMessage());
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutMaterialTypesByMaterialtypeIdResponse
                  .respond500WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
              }
            } catch (Exception e) {
              log.error(e.getMessage(), e);
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutMaterialTypesByMaterialtypeIdResponse
                .respond500WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
            }
          });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutMaterialTypesByMaterialtypeIdResponse
          .respond500WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
      }
    });
  }

  private CQLWrapper getCql(String query, int limit, int offset) throws FieldException {
    return StorageHelper.getCql(query, limit, offset, MATERIAL_TYPE_TABLE);
  }

  private boolean isDuplicate(String errorMessage) {
    return errorMessage != null && errorMessage.contains("duplicate key value violates unique constraint");
  }
}
