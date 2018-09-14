package org.folio.rest.impl;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.InstanceRelationshipType;
import org.folio.rest.jaxrs.model.InstanceRelationshipTypes;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.jaxrs.resource.InstanceRelationshipTypesResource;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.TenantTool;
import org.z3950.zing.cql.CQLParseException;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;

/**
 *
 * @author ne
 */
public class InstanceRelationshipTypeAPI implements InstanceRelationshipTypesResource {

  public static final String INSTANCE_RELATIONSHIP_TYPE_TABLE   = "instance_relationship_type";

  private static final String LOCATION_PREFIX       = "/instance-relationship-types/";
  private static final Logger log                 = LoggerFactory.getLogger(InstanceRelationshipTypeAPI.class);
  private final Messages messages                 = Messages.getInstance();
  private final String idFieldName                      = "_id";


  public InstanceRelationshipTypeAPI(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  private CQLWrapper getCQL(String query, int limit, int offset) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(INSTANCE_RELATIONSHIP_TYPE_TABLE+".jsonb");
    return new CQLWrapper(cql2pgJson, query).setLimit(new Limit(limit)).setOffset(new Offset(offset));
  }

  @Validate
  @Override
  public void getInstanceRelationshipTypes(String query, int offset, int limit, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) throws Exception {
    /**
     * http://host:port/instance-relationship-types
     */
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.tenantId(okapiHeaders);
        CQLWrapper cql = getCQL(query, limit, offset);
        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(INSTANCE_RELATIONSHIP_TYPE_TABLE, InstanceRelationshipType.class,
            new String[]{"*"}, cql, true, true,
            reply -> {
              try {
                if (reply.succeeded()) {
                  InstanceRelationshipTypes instanceRelationshipTypes = new InstanceRelationshipTypes();
                  @SuppressWarnings("unchecked")
                  List<InstanceRelationshipType> instanceRelationshipType = (List<InstanceRelationshipType>) reply.result().getResults();
                  instanceRelationshipTypes.setInstanceRelationshipTypes(instanceRelationshipType);
                  instanceRelationshipTypes.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetInstanceRelationshipTypesResponse.withJsonOK(
                      instanceRelationshipTypes)));
                }
                else{
                  log.error(reply.cause().getMessage(), reply.cause());
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetInstanceRelationshipTypesResponse
                      .withPlainBadRequest(reply.cause().getMessage())));
                }
              } catch (Exception e) {
                log.error(e.getMessage(), e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetInstanceRelationshipTypesResponse
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
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetInstanceRelationshipTypesResponse
            .withPlainInternalServerError(message)));
      }
    });
  }

  private void internalServerErrorDuringPost(Throwable e, String lang, Handler<AsyncResult<Response>> handler) {
    log.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(PostInstanceRelationshipTypesResponse
        .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
  }

  @Validate
  @Override
  public void postInstanceRelationshipTypes(String lang, InstanceRelationshipType entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

    vertxContext.runOnContext(v -> {
      try {
        String id = entity.getId();
        if (id == null) {
          id = UUID.randomUUID().toString();
          entity.setId(id);
        }

        String tenantId = TenantTool.tenantId(okapiHeaders);
        PostgresClient.getInstance(vertxContext.owner(), tenantId).save(INSTANCE_RELATIONSHIP_TYPE_TABLE, id, entity,
            reply -> {
              try {
                if (reply.succeeded()) {
                  Object ret = reply.result();
                  entity.setId((String) ret);
                  OutStream stream = new OutStream();
                  stream.setData(entity);
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostInstanceRelationshipTypesResponse.withJsonCreated(
                      LOCATION_PREFIX + ret, stream)));
                } else {
                  String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                  if (msg == null) {
                    internalServerErrorDuringPost(reply.cause(), lang, asyncResultHandler);
                    return;
                  }
                  log.info(msg);
                  asyncResultHandler.handle(Future.succeededFuture(PostInstanceRelationshipTypesResponse
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
    handler.handle(Future.succeededFuture(GetInstanceRelationshipTypesByRelationshipTypeIdResponse
        .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
  }

  @Validate
  @Override
  public void getInstanceRelationshipTypesByRelationshipTypeId(String relationshipTypeId, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) throws Exception {

    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.tenantId(okapiHeaders);

        Criterion c = new Criterion(
            new Criteria().addField(idFieldName).setJSONB(false).setOperation("=").setValue("'"+relationshipTypeId+"'"));

        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(INSTANCE_RELATIONSHIP_TYPE_TABLE, InstanceRelationshipType.class, c, true,
            reply -> {
              try {
                if (reply.failed()) {
                  String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                  if (msg == null) {
                    internalServerErrorDuringGetById(reply.cause(), lang, asyncResultHandler);
                    return;
                  }
                  log.info(msg);
                  asyncResultHandler.handle(Future.succeededFuture(GetInstanceRelationshipTypesByRelationshipTypeIdResponse.
                      withPlainNotFound(msg)));
                  return;
                }
                @SuppressWarnings("unchecked")
                List<InstanceRelationshipType> instanceRelationshipType = (List<InstanceRelationshipType>) reply.result().getResults();
                if (instanceRelationshipType.isEmpty()) {
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetInstanceRelationshipTypesByRelationshipTypeIdResponse
                      .withPlainNotFound(relationshipTypeId)));
                }
                else{
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetInstanceRelationshipTypesByRelationshipTypeIdResponse
                      .withJsonOK(instanceRelationshipType.get(0))));
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
    handler.handle(Future.succeededFuture(DeleteInstanceRelationshipTypesByRelationshipTypeIdResponse
        .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
  }

  @Validate
  @Override
  public void deleteInstanceRelationshipTypesByRelationshipTypeId(String relationshipTypeId, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) throws Exception {

    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.tenantId(okapiHeaders);
        PostgresClient postgres = PostgresClient.getInstance(vertxContext.owner(), tenantId);
        postgres.delete(INSTANCE_RELATIONSHIP_TYPE_TABLE, relationshipTypeId,
            reply -> {
              try {
                if (reply.failed()) {
                  String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                  if (msg == null) {
                    internalServerErrorDuringDelete(reply.cause(), lang, asyncResultHandler);
                    return;
                  }
                  log.info(msg);
                  asyncResultHandler.handle(Future.succeededFuture(DeleteInstanceRelationshipTypesByRelationshipTypeIdResponse
                      .withPlainBadRequest(msg)));
                  return;
                }
                int updated = reply.result().getUpdated();
                if (updated != 1) {
                  String msg = messages.getMessage(lang, MessageConsts.DeletedCountError, 1, updated);
                  log.error(msg);
                  asyncResultHandler.handle(Future.succeededFuture(DeleteInstanceRelationshipTypesByRelationshipTypeIdResponse
                      .withPlainNotFound(msg)));
                  return;
                }
                asyncResultHandler.handle(Future.succeededFuture(DeleteInstanceRelationshipTypesByRelationshipTypeIdResponse
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
    handler.handle(Future.succeededFuture(PutInstanceRelationshipTypesByRelationshipTypeIdResponse
        .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
  }

  @Validate
  @Override
  public void putInstanceRelationshipTypesByRelationshipTypeId(String relationshipTypeId, String lang, InstanceRelationshipType entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) throws Exception {

    vertxContext.runOnContext(v -> {
      String tenantId = TenantTool.tenantId(okapiHeaders);
      try {
        if (entity.getId() == null) {
          entity.setId(relationshipTypeId);
        }
        PostgresClient.getInstance(vertxContext.owner(), tenantId).update(INSTANCE_RELATIONSHIP_TYPE_TABLE, entity, relationshipTypeId,
            reply -> {
              try {
                if (reply.succeeded()) {
                  if (reply.result().getUpdated() == 0) {
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutInstanceRelationshipTypesByRelationshipTypeIdResponse
                        .withPlainNotFound(messages.getMessage(lang, MessageConsts.NoRecordsUpdated))));
                  } else{
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutInstanceRelationshipTypesByRelationshipTypeIdResponse
                        .withNoContent()));
                  }
                } else {
                  String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                  if (msg == null) {
                    internalServerErrorDuringPut(reply.cause(), lang, asyncResultHandler);
                    return;
                  }
                  log.info(msg);
                  asyncResultHandler.handle(Future.succeededFuture(PutInstanceRelationshipTypesByRelationshipTypeIdResponse
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
