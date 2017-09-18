package org.folio.rest.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.InstanceType;
import org.folio.rest.jaxrs.model.InstanceTypes;
import org.folio.rest.jaxrs.resource.InstanceTypesResource;
import org.folio.rest.persist.DatabaseExceptionUtils;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.TenantTool;
import org.z3950.zing.cql.CQLParseException;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * Implements the instance instance type persistency using postgres jsonb.
 */
public class InstanceTypeAPI implements InstanceTypesResource {

  public static final String INSTANCE_TYPE_TABLE   = "instance_type";

  private static final String LOCATION_PREFIX       = "/instance-types/";
  private static final Logger log                 = LoggerFactory.getLogger(InstanceTypeAPI.class);
  private final Messages messages                 = Messages.getInstance();
  private String idFieldName                      = "_id";


  public InstanceTypeAPI(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  private CQLWrapper getCQL(String query, int limit, int offset) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(INSTANCE_TYPE_TABLE+".jsonb");
    return new CQLWrapper(cql2pgJson, query).setLimit(new Limit(limit)).setOffset(new Offset(offset));
  }

  @Validate
  @Override
  public void getInstanceTypes(String query, int offset, int limit, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) throws Exception {
    /**
     * http://host:port/instances/instance-types
     */
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.tenantId(okapiHeaders);
        CQLWrapper cql = getCQL(query, limit, offset);
        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(INSTANCE_TYPE_TABLE, InstanceType.class,
            new String[]{"*"}, cql, true, true,
            reply -> {
              try {
                if (reply.succeeded()) {
                  InstanceTypes instanceTypes = new InstanceTypes();
                  @SuppressWarnings("unchecked")
                  List<InstanceType> instanceType = (List<InstanceType>) reply.result()[0];
                  instanceTypes.setInstanceTypes(instanceType);
                  instanceTypes.setTotalRecords((Integer)reply.result()[1]);
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetInstanceTypesResponse.withJsonOK(
                      instanceTypes)));
                }
                else{
                  log.error(reply.cause().getMessage(), reply.cause());
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetInstanceTypesResponse
                      .withPlainBadRequest(reply.cause().getMessage())));
                }
              } catch (Exception e) {
                log.error(e.getMessage(), e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetInstanceTypesResponse
                    .withPlainInternalServerError(messages.getMessage(
                        lang, MessageConsts.InternalServerError))));
              }
            });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        String message = messages.getMessage(lang, MessageConsts.InternalServerError);
        if (e.getCause() instanceof CQLParseException) {
          message = " CQL parse error " + e.getLocalizedMessage();
        }
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetInstanceTypesResponse
            .withPlainInternalServerError(message)));
      }
    });
  }

  private void internalServerErrorDuringPost(Throwable e, String lang, Handler<AsyncResult<Response>> handler) {
    log.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(PostInstanceTypesResponse
        .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
  }

  @Validate
  @Override
  public void postInstanceTypes(String lang, InstanceType entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

    vertxContext.runOnContext(v -> {
      try {
        String id = entity.getId();
        if (id == null) {
          id = UUID.randomUUID().toString();
          entity.setId(id);
        }

        String tenantId = TenantTool.tenantId(okapiHeaders);
        PostgresClient.getInstance(vertxContext.owner(), tenantId).save(
            INSTANCE_TYPE_TABLE, id, entity,
            reply -> {
              try {
                if (reply.succeeded()) {
                  Object ret = reply.result();
                  entity.setId((String) ret);
                  OutStream stream = new OutStream();
                  stream.setData(entity);
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostInstanceTypesResponse.withJsonCreated(
                      LOCATION_PREFIX + ret, stream)));
                } else {
                  String msg = DatabaseExceptionUtils.badRequestMessage(reply.cause());
                  if (msg == null) {
                    internalServerErrorDuringPost(reply.cause(), lang, asyncResultHandler);
                    return;
                  }
                  log.info(msg);
                  asyncResultHandler.handle(Future.succeededFuture(PostInstanceTypesResponse
                      .withPlainBadRequest(msg)));
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

  private void internalServerErrorDuringGetById(Throwable e, String lang, Handler<AsyncResult<Response>> handler) {
    log.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(GetInstanceTypesByInstanceTypeIdResponse
        .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
  }

  @Validate
  @Override
  public void getInstanceTypesByInstanceTypeId(String instanceTypeId, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) throws Exception {

    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.tenantId(okapiHeaders);

        Criterion c = new Criterion(
            new Criteria().addField(idFieldName).setJSONB(false).setOperation("=").setValue("'"+instanceTypeId+"'"));

        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(INSTANCE_TYPE_TABLE, InstanceType.class, c, true,
            reply -> {
              try {
                if (reply.failed()) {
                  String msg = DatabaseExceptionUtils.badRequestMessage(reply.cause());
                  if (msg == null) {
                    internalServerErrorDuringGetById(reply.cause(), lang, asyncResultHandler);
                    return;
                  }
                  log.info(msg);
                  asyncResultHandler.handle(Future.succeededFuture(GetInstanceTypesByInstanceTypeIdResponse.
                      withPlainNotFound(msg)));
                  return;
                }
                @SuppressWarnings("unchecked")
                List<InstanceType> instanceType = (List<InstanceType>) reply.result()[0];
                if (instanceType.isEmpty()) {
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetInstanceTypesByInstanceTypeIdResponse
                      .withPlainNotFound(instanceTypeId)));
                }
                else{
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetInstanceTypesByInstanceTypeIdResponse
                      .withJsonOK(instanceType.get(0))));
                }
              } catch (Exception e) {
                internalServerErrorDuringGetById(e, lang, asyncResultHandler);
              }
            });
      } catch (Exception e) {
        internalServerErrorDuringGetById(e, lang, asyncResultHandler);
      }
    });
  }

  private void internalServerErrorDuringDelete(Throwable e, String lang, Handler<AsyncResult<Response>> handler) {
    log.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(DeleteInstanceTypesByInstanceTypeIdResponse
        .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
  }

  @Validate
  @Override
  public void deleteInstanceTypesByInstanceTypeId(String instanceTypeId, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) throws Exception {

    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.tenantId(okapiHeaders);
        PostgresClient postgres = PostgresClient.getInstance(vertxContext.owner(), tenantId);
        postgres.delete(INSTANCE_TYPE_TABLE, instanceTypeId,
            reply -> {
              try {
                if (reply.failed()) {
                  String msg = DatabaseExceptionUtils.badRequestMessage(reply.cause());
                  if (msg == null) {
                    internalServerErrorDuringDelete(reply.cause(), lang, asyncResultHandler);
                    return;
                  }
                  log.info(msg);
                  asyncResultHandler.handle(Future.succeededFuture(DeleteInstanceTypesByInstanceTypeIdResponse
                      .withPlainBadRequest(msg)));
                  return;
                }
                int updated = reply.result().getUpdated();
                if (updated != 1) {
                  String msg = messages.getMessage(lang, MessageConsts.DeletedCountError, 1, updated);
                  log.error(msg);
                  asyncResultHandler.handle(Future.succeededFuture(DeleteInstanceTypesByInstanceTypeIdResponse
                      .withPlainNotFound(msg)));
                  return;
                }
                asyncResultHandler.handle(Future.succeededFuture(DeleteInstanceTypesByInstanceTypeIdResponse
                        .withNoContent()));
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
    handler.handle(Future.succeededFuture(PutInstanceTypesByInstanceTypeIdResponse
        .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
  }

  @Validate
  @Override
  public void putInstanceTypesByInstanceTypeId(String instanceTypeId, String lang, InstanceType entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) throws Exception {

    vertxContext.runOnContext(v -> {
      String tenantId = TenantTool.tenantId(okapiHeaders);
      try {
        if (entity.getId() == null) {
          entity.setId(instanceTypeId);
        }
        PostgresClient.getInstance(vertxContext.owner(), tenantId).update(
            INSTANCE_TYPE_TABLE, entity, instanceTypeId,
            reply -> {
              try {
                if (reply.succeeded()) {
                  if (reply.result().getUpdated() == 0) {
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutInstanceTypesByInstanceTypeIdResponse
                        .withPlainNotFound(messages.getMessage(lang, MessageConsts.NoRecordsUpdated))));
                  } else{
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutInstanceTypesByInstanceTypeIdResponse
                        .withNoContent()));
                  }
                } else {
                  String msg = DatabaseExceptionUtils.badRequestMessage(reply.cause());
                  if (msg == null) {
                    internalServerErrorDuringPut(reply.cause(), lang, asyncResultHandler);
                    return;
                  }
                  log.info(msg);
                  asyncResultHandler.handle(Future.succeededFuture(PutInstanceTypesByInstanceTypeIdResponse
                      .withPlainBadRequest(msg)));
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
