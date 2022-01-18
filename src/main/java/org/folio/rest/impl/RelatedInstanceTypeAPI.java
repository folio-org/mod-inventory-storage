package org.folio.rest.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.validation.constraints.Pattern;
import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.RestVerticle;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.RelatedInstanceType;
import org.folio.rest.jaxrs.model.RelatedInstanceTypes;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.TenantTool;
import org.z3950.zing.cql.CQLParseException;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;

/**
 * Implements the related instancetype persistency using postgres jsonb.
 */
public class RelatedInstanceTypeAPI implements org.folio.rest.jaxrs.resource.RelatedInstanceTypes {

  public static final String RELATED_INSTANCE_TYPE_TABLE = "related_instance_type";

  private static final String LOCATION_PREFIX = "/related-instance-types/";
  private static final Logger log = LogManager.getLogger();
  private final Messages messages = Messages.getInstance();

  private CQLWrapper getCQL(String query, int limit, int offset) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(RELATED_INSTANCE_TYPE_TABLE+".jsonb");
    return new CQLWrapper(cql2pgJson, query).setLimit(new Limit(limit)).setOffset(new Offset(offset));
  }

  @Validate
  @Override
  public void getRelatedInstanceTypes(String query, int offset, int limit, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    /**
     * http://host:port/related-instance-types
     */
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.tenantId(okapiHeaders);
        CQLWrapper cql = getCQL(query, limit, offset);
        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(RELATED_INSTANCE_TYPE_TABLE, RelatedInstanceType.class,
            new String[]{"*"}, cql, true, true,
            reply -> {
              try {
                if (reply.succeeded()) {
                  RelatedInstanceTypes instanceTypes = new RelatedInstanceTypes();
                  List<RelatedInstanceType> instanceType = reply.result().getResults();
                  instanceTypes.setRelatedInstanceTypes(instanceType);
                  instanceTypes.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetRelatedInstanceTypesResponse.respond200WithApplicationJson(
                      instanceTypes)));
                }
                else {
                  log.error(reply.cause().getMessage(), reply.cause());
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetRelatedInstanceTypesResponse
                      .respond400WithTextPlain(reply.cause().getMessage())));
                }
              } catch (Exception e) {
                log.error(e.getMessage(), e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetRelatedInstanceTypesResponse
                    .respond500WithTextPlain(messages.getMessage(
                        lang, MessageConsts.InternalServerError))));
              }
            });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        String message = messages.getMessage(lang, MessageConsts.InternalServerError);
        if (e.getCause() instanceof CQLParseException) {
          message = " CQL parse error " + e.getLocalizedMessage();
        }
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetRelatedInstanceTypesResponse
            .respond500WithTextPlain(message)));
      }
    });
  }

  @Validate
  @Override
  public void postRelatedInstanceTypes(String lang, RelatedInstanceType entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    vertxContext.runOnContext(v -> {
      try {
        String id = UUID.randomUUID().toString();
        if (entity.getId() == null) {
          entity.setId(id);
        }
        else {
          id = entity.getId();
        }

        String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
        PostgresClient.getInstance(vertxContext.owner(), tenantId).save(
          RELATED_INSTANCE_TYPE_TABLE, id, entity,
          reply -> {
            try {
              if (reply.succeeded()) {
                String ret = reply.result();
                entity.setId(ret);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostRelatedInstanceTypesResponse
                  .respond201WithApplicationJson(entity,
                    PostRelatedInstanceTypesResponse.headersFor201().withLocation(LOCATION_PREFIX + ret))));
              }
              else {
                log.error(reply.cause().getMessage(), reply.cause());
                if (isDuplicate(reply.cause().getMessage())) {
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostRelatedInstanceTypesResponse
                    .respond422WithApplicationJson(
                      org.folio.rest.tools.utils.ValidationHelper.createValidationErrorMessage(
                        "name", entity.getName(), "Related Instance Type exists"))));
                }
                else {
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostRelatedInstanceTypesResponse
                    .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
                }
              }
            } catch (Exception e) {
              log.error(e.getMessage(), e);
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostRelatedInstanceTypesResponse
                .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
            }
          });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostRelatedInstanceTypesResponse
          .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  @Validate
  @Override
  public void getRelatedInstanceTypesByRelatedInstanceTypeId(String instanceTypeId, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    PgUtil.getById(RELATED_INSTANCE_TYPE_TABLE, RelatedInstanceType.class, instanceTypeId,
        okapiHeaders, vertxContext, GetRelatedInstanceTypesByRelatedInstanceTypeIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteRelatedInstanceTypesByRelatedInstanceTypeId(String instanceTypeId, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    PgUtil.deleteById(RELATED_INSTANCE_TYPE_TABLE, instanceTypeId, okapiHeaders, vertxContext,
      DeleteRelatedInstanceTypesByRelatedInstanceTypeIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void putRelatedInstanceTypesByRelatedInstanceTypeId(String instanceTypeId, String lang, RelatedInstanceType entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {
    vertxContext.runOnContext(v -> {
      String tenantId = TenantTool.calculateTenantId( okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT) );
      try {
        if (entity.getId() == null) {
          entity.setId(instanceTypeId);
        }
        PostgresClient.getInstance(vertxContext.owner(), tenantId).update(
          RELATED_INSTANCE_TYPE_TABLE, entity, instanceTypeId,
          reply -> {
            try {
              if (reply.succeeded()) {
                if (reply.result().rowCount() == 0) {
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutRelatedInstanceTypesByRelatedInstanceTypeIdResponse
                    .respond404WithTextPlain(messages.getMessage(lang, MessageConsts.NoRecordsUpdated))));
                }
                else {
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutRelatedInstanceTypesByRelatedInstanceTypeIdResponse
                    .respond204()));
                }
              }
              else {
                log.error(reply.cause().getMessage());
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutRelatedInstanceTypesByRelatedInstanceTypeIdResponse
                  .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
              }
            } catch (Exception e) {
              log.error(e.getMessage(), e);
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutRelatedInstanceTypesByRelatedInstanceTypeIdResponse
                .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
            }
          });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutRelatedInstanceTypesByRelatedInstanceTypeIdResponse
          .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  @Override
  public void deleteRelatedInstanceTypes(@Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    String tenantId = TenantTool.tenantId(okapiHeaders);

    try {
      vertxContext.runOnContext(v -> {
        PostgresClient postgresClient = PostgresClient.getInstance(
          vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

        postgresClient.execute(String.format("DELETE FROM %s_%s.%s",
          tenantId, "mod_inventory_storage", RELATED_INSTANCE_TYPE_TABLE),
          reply -> {
            if (reply.succeeded()) {
              asyncResultHandler.handle(Future.succeededFuture(
                DeleteRelatedInstanceTypesResponse.noContent().build()));
            } else {
              asyncResultHandler.handle(Future.succeededFuture(
                DeleteRelatedInstanceTypesResponse.respond500WithTextPlain(reply.cause().getMessage())));
            }
          });
      });
    }
    catch(Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(
        DeleteRelatedInstanceTypesResponse.respond500WithTextPlain(e.getMessage())));
    }
  }

  private boolean isDuplicate(String errorMessage) {
    if (errorMessage != null && errorMessage.contains("duplicate key value violates unique constraint")) {
      return true;
    }
    return false;
  }
}
