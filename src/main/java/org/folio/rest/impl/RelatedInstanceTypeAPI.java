package org.folio.rest.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.RelatedInstanceType;
import org.folio.rest.jaxrs.model.RelatedInstanceTypes;
import org.folio.rest.persist.PgExceptionUtil;
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
                else{
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

  private void internalServerErrorDuringPost(Throwable e, String lang, Handler<AsyncResult<Response>> handler) {
    log.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(PostRelatedInstanceTypesResponse
        .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
  }

  @Validate
  @Override
  public void postRelatedInstanceTypes(String lang, RelatedInstanceType entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    vertxContext.runOnContext(v -> {
      try {
        String id = entity.getId();
        if (id == null) {
          id = UUID.randomUUID().toString();
          entity.setId(id);
        }

        String tenantId = TenantTool.tenantId(okapiHeaders);
        PostgresClient.getInstance(vertxContext.owner(), tenantId).save(
            RELATED_INSTANCE_TYPE_TABLE, id, entity,
            reply -> {
              try {
                if (reply.succeeded()) {
                  String ret = reply.result();
                  entity.setId(ret);
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostRelatedInstanceTypesResponse
                    .respond201WithApplicationJson(entity, PostRelatedInstanceTypesResponse.headersFor201().withLocation(LOCATION_PREFIX + ret))));
                } else {
                  String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                  if (msg == null) {
                    internalServerErrorDuringPost(reply.cause(), lang, asyncResultHandler);
                    return;
                  }
                  log.info(msg);
                  asyncResultHandler.handle(Future.succeededFuture(PostRelatedInstanceTypesResponse
                      .respond400WithTextPlain(msg)));
                }
              } catch (Exception e) {
                internalServerErrorDuringPost(e, lang, asyncResultHandler);
              }
            });
      } catch (Exception e) {
        internalServerErrorDuringPost(e, lang, asyncResultHandler);
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

  private void internalServerErrorDuringDelete(Throwable e, String lang, Handler<AsyncResult<Response>> handler) {
    log.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(DeleteRelatedInstanceTypesByRelatedInstanceTypeIdResponse
        .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
  }

  @Validate
  @Override
  public void deleteRelatedInstanceTypesByRelatedInstanceTypeId(String instanceTypeId, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.tenantId(okapiHeaders);
        PostgresClient postgres = PostgresClient.getInstance(vertxContext.owner(), tenantId);
        postgres.delete(RELATED_INSTANCE_TYPE_TABLE, instanceTypeId,
            reply -> {
              try {
                if (reply.failed()) {
                  String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                  if (msg == null) {
                    internalServerErrorDuringDelete(reply.cause(), lang, asyncResultHandler);
                    return;
                  }
                  log.info(msg);
                  asyncResultHandler.handle(Future.succeededFuture(DeleteRelatedInstanceTypesByRelatedInstanceTypeIdResponse
                      .respond400WithTextPlain(msg)));
                  return;
                }
                int updated = reply.result().rowCount();
                if (updated != 1) {
                  String msg = messages.getMessage(lang, MessageConsts.DeletedCountError, 1, updated);
                  log.error(msg);
                  asyncResultHandler.handle(Future.succeededFuture(DeleteRelatedInstanceTypesByRelatedInstanceTypeIdResponse
                      .respond404WithTextPlain(msg)));
                  return;
                }
                asyncResultHandler.handle(Future.succeededFuture(DeleteRelatedInstanceTypesByRelatedInstanceTypeIdResponse
                        .respond204()));
              } catch (Exception e) {
                internalServerErrorDuringDelete(e, lang, asyncResultHandler);
              }
            });
      } catch (Exception e) {
        internalServerErrorDuringDelete(e, lang, asyncResultHandler);
      }
    });
  }

  private void internalServerErrorDuringPut(Throwable e, String lang, Handler<AsyncResult<Response>> handler) {
    log.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(PutRelatedInstanceTypesByRelatedInstanceTypeIdResponse
        .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
  }

  @Validate
  @Override
  public void putRelatedInstanceTypesByRelatedInstanceTypeId(String instanceTypeId, String lang, RelatedInstanceType entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    vertxContext.runOnContext(v -> {
      String tenantId = TenantTool.tenantId(okapiHeaders);
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
                  } else{
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutRelatedInstanceTypesByRelatedInstanceTypeIdResponse
                        .respond204()));
                  }
                } else {
                  String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                  if (msg == null) {
                    internalServerErrorDuringPut(reply.cause(), lang, asyncResultHandler);
                    return;
                  }
                  log.info(msg);
                  asyncResultHandler.handle(Future.succeededFuture(PutRelatedInstanceTypesByRelatedInstanceTypeIdResponse
                      .respond400WithTextPlain(msg)));
                }
              } catch (Exception e) {
                internalServerErrorDuringPut(e, lang, asyncResultHandler);
              }
            });
      } catch (Exception e) {
        internalServerErrorDuringPut(e, lang, asyncResultHandler);      }
    });
  }
}
