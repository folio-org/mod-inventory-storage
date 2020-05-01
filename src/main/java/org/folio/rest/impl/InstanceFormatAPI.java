package org.folio.rest.impl;

import io.vertx.core.*;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.InstanceFormat;
import org.folio.rest.jaxrs.model.InstanceFormats;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.TenantTool;
import org.z3950.zing.cql.CQLParseException;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implements the instance format persistency using postgres jsonb.
 */
public class InstanceFormatAPI implements org.folio.rest.jaxrs.resource.InstanceFormats {

  public static final String INSTANCE_FORMAT_TABLE   = "instance_format";

  private static final String LOCATION_PREFIX       = "/instance-formats/";
  private static final Logger log                 = LoggerFactory.getLogger(InstanceFormatAPI.class);
  private final Messages messages                 = Messages.getInstance();

  private CQLWrapper getCQL(String query, int limit, int offset) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(INSTANCE_FORMAT_TABLE+".jsonb");
    return new CQLWrapper(cql2pgJson, query).setLimit(new Limit(limit)).setOffset(new Offset(offset));
  }

  @Validate
  @Override
  public void getInstanceFormats(String query, int offset, int limit, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {
    /**
     * http://host:port/instance-formats
     */
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.tenantId(okapiHeaders);
        CQLWrapper cql = getCQL(query, limit, offset);
        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(INSTANCE_FORMAT_TABLE, InstanceFormat.class,
            new String[]{"*"}, cql, true, true,
            reply -> {
              try {
                if (reply.succeeded()) {
                  InstanceFormats instanceFormats = new InstanceFormats();
                  List<InstanceFormat> instanceFormat = reply.result().getResults();
                  instanceFormats.setInstanceFormats(instanceFormat);
                  instanceFormats.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetInstanceFormatsResponse.respond200WithApplicationJson(
                      instanceFormats)));
                }
                else{
                  log.error(reply.cause().getMessage(), reply.cause());
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetInstanceFormatsResponse
                      .respond400WithTextPlain(reply.cause().getMessage())));
                }
              } catch (Exception e) {
                log.error(e.getMessage(), e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetInstanceFormatsResponse
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
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetInstanceFormatsResponse
            .respond500WithTextPlain(message)));
      }
    });
  }

  private void internalServerErrorDuringPost(Throwable e, String lang, Handler<AsyncResult<Response>> handler) {
    log.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(PostInstanceFormatsResponse
        .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
  }

  @Validate
  @Override
  public void postInstanceFormats(String lang, InstanceFormat entity, Map<String, String> okapiHeaders,
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
            INSTANCE_FORMAT_TABLE, id, entity,
            reply -> {
              try {
                if (reply.succeeded()) {
                  String ret = reply.result();
                  entity.setId(ret);
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    PostInstanceFormatsResponse.respond201WithApplicationJson(entity,
                    PostInstanceFormatsResponse.headersFor201().withLocation(LOCATION_PREFIX + ret))));
                } else {
                  String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                  if (msg == null) {
                    internalServerErrorDuringPost(reply.cause(), lang, asyncResultHandler);
                    return;
                  }
                  log.info(msg);
                  asyncResultHandler.handle(Future.succeededFuture(PostInstanceFormatsResponse
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
  public void getInstanceFormatsByInstanceFormatId(String instanceFormatId, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {
    PgUtil.getById(INSTANCE_FORMAT_TABLE, InstanceFormat.class, instanceFormatId,
        okapiHeaders, vertxContext, GetInstanceFormatsByInstanceFormatIdResponse.class, asyncResultHandler);
  }

  private void internalServerErrorDuringDelete(Throwable e, String lang, Handler<AsyncResult<Response>> handler) {
    log.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(DeleteInstanceFormatsByInstanceFormatIdResponse
        .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
  }

  @Validate
  @Override
  public void deleteInstanceFormatsByInstanceFormatId(String instanceFormatId, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.tenantId(okapiHeaders);
        PostgresClient postgres = PostgresClient.getInstance(vertxContext.owner(), tenantId);
        postgres.delete(INSTANCE_FORMAT_TABLE, instanceFormatId,
            reply -> {
              try {
                if (reply.failed()) {
                  String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                  if (msg == null) {
                    internalServerErrorDuringDelete(reply.cause(), lang, asyncResultHandler);
                    return;
                  }
                  log.info(msg);
                  asyncResultHandler.handle(Future.succeededFuture(DeleteInstanceFormatsByInstanceFormatIdResponse
                      .respond400WithTextPlain(msg)));
                  return;
                }
                int updated = reply.result().rowCount();
                if (updated != 1) {
                  String msg = messages.getMessage(lang, MessageConsts.DeletedCountError, 1, updated);
                  log.error(msg);
                  asyncResultHandler.handle(Future.succeededFuture(DeleteInstanceFormatsByInstanceFormatIdResponse
                      .respond404WithTextPlain(msg)));
                  return;
                }
                asyncResultHandler.handle(Future.succeededFuture(DeleteInstanceFormatsByInstanceFormatIdResponse
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
    handler.handle(Future.succeededFuture(PutInstanceFormatsByInstanceFormatIdResponse
        .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
  }

  @Validate
  @Override
  public void putInstanceFormatsByInstanceFormatId(String instanceFormatId, String lang, InstanceFormat entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    vertxContext.runOnContext(v -> {
      String tenantId = TenantTool.tenantId(okapiHeaders);
      try {
        if (entity.getId() == null) {
          entity.setId(instanceFormatId);
        }
        PostgresClient.getInstance(vertxContext.owner(), tenantId).update(
            INSTANCE_FORMAT_TABLE, entity, instanceFormatId,
            reply -> {
              try {
                if (reply.succeeded()) {
                  if (reply.result().rowCount() == 0) {
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutInstanceFormatsByInstanceFormatIdResponse
                        .respond404WithTextPlain(messages.getMessage(lang, MessageConsts.NoRecordsUpdated))));
                  } else{
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutInstanceFormatsByInstanceFormatIdResponse
                        .respond204()));
                  }
                } else {
                  String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                  if (msg == null) {
                    internalServerErrorDuringPut(reply.cause(), lang, asyncResultHandler);
                    return;
                  }
                  log.info(msg);
                  asyncResultHandler.handle(Future.succeededFuture(PutInstanceFormatsByInstanceFormatIdResponse
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
