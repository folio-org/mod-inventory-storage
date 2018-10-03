package org.folio.rest.impl;

import io.vertx.core.*;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.ContributorNameType;
import org.folio.rest.jaxrs.model.ContributorNameTypes;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.TenantTool;
import org.z3950.zing.cql.CQLParseException;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implements the instance contributor name type persistency using postgres jsonb.
 */
public class ContributorNameTypeAPI implements org.folio.rest.jaxrs.resource.ContributorNameTypes {

  public static final String CONTRIBUTOR_NAME_TYPE_TABLE   = "contributor_name_type";

  private static final String LOCATION_PREFIX       = "/contributor-name-types/";
  private static final Logger log                 = LoggerFactory.getLogger(ContributorNameTypeAPI.class);
  private final Messages messages                 = Messages.getInstance();
  private String idFieldName                      = "_id";


  public ContributorNameTypeAPI(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  private CQLWrapper getCQL(String query, int limit, int offset) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(CONTRIBUTOR_NAME_TYPE_TABLE+".jsonb");
    return new CQLWrapper(cql2pgJson, query).setLimit(new Limit(limit)).setOffset(new Offset(offset));
  }

  @Validate
  @Override
  public void getContributorNameTypes(String query, int offset, int limit, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {
    /**
     * http://host:port/contributor-name-types
     */
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.tenantId(okapiHeaders);
        CQLWrapper cql = getCQL(query, limit, offset);
        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(CONTRIBUTOR_NAME_TYPE_TABLE, ContributorNameType.class,
            new String[]{"*"}, cql, true, true,
            reply -> {
              try {
                if (reply.succeeded()) {
                  ContributorNameTypes ContributorNameTypes = new ContributorNameTypes();
                  @SuppressWarnings("unchecked")
                  List<ContributorNameType> ContributorNameType = (List<ContributorNameType>) reply.result().getResults();
                  ContributorNameTypes.setContributorNameTypes(ContributorNameType);
                  ContributorNameTypes.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetContributorNameTypesResponse.respond200WithApplicationJson(
                      ContributorNameTypes)));
                }
                else{
                  log.error(reply.cause().getMessage(), reply.cause());
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetContributorNameTypesResponse
                      .respond400WithTextPlain(reply.cause().getMessage())));
                }
              } catch (Exception e) {
                log.error(e.getMessage(), e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetContributorNameTypesResponse
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
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetContributorNameTypesResponse
            .respond500WithTextPlain(message)));
      }
    });
  }

  private void internalServerErrorDuringPost(Throwable e, String lang, Handler<AsyncResult<Response>> handler) {
    log.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(PostContributorNameTypesResponse
        .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
  }

  @Validate
  @Override
  public void postContributorNameTypes(String lang, ContributorNameType entity, Map<String, String> okapiHeaders,
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
            CONTRIBUTOR_NAME_TYPE_TABLE, id, entity,
            reply -> {
              try {
                if (reply.succeeded()) {
                  String ret = reply.result();
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostContributorNameTypesResponse
                    .respond201WithApplicationJson(entity, PostContributorNameTypesResponse.headersFor201().withLocation(LOCATION_PREFIX + ret))));
                } else {
                  String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                  if (msg == null) {
                    internalServerErrorDuringPost(reply.cause(), lang, asyncResultHandler);
                    return;
                  }
                  log.info(msg);
                  asyncResultHandler.handle(Future.succeededFuture(PostContributorNameTypesResponse
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

  private void internalServerErrorDuringGetById(Throwable e, String lang, Handler<AsyncResult<Response>> handler) {
    log.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(GetContributorNameTypesByContributorNameTypeIdResponse
        .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
  }

  @Validate
  @Override
  public void getContributorNameTypesByContributorNameTypeId(String ContributorNameTypeId, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.tenantId(okapiHeaders);

        Criterion c = new Criterion(
            new Criteria().addField(idFieldName).setJSONB(false).setOperation("=").setValue("'"+ContributorNameTypeId+"'"));

        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(CONTRIBUTOR_NAME_TYPE_TABLE, ContributorNameType.class, c, true,
            reply -> {
              try {
                if (reply.failed()) {
                  String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                  if (msg == null) {
                    internalServerErrorDuringGetById(reply.cause(), lang, asyncResultHandler);
                    return;
                  }
                  log.info(msg);
                  asyncResultHandler.handle(Future.succeededFuture(GetContributorNameTypesByContributorNameTypeIdResponse.
                      respond404WithTextPlain(msg)));
                  return;
                }
                @SuppressWarnings("unchecked")
                List<ContributorNameType> ContributorNameType = (List<ContributorNameType>) reply.result().getResults();
                if (ContributorNameType.isEmpty()) {
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetContributorNameTypesByContributorNameTypeIdResponse
                      .respond404WithTextPlain(ContributorNameTypeId)));
                }
                else{
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetContributorNameTypesByContributorNameTypeIdResponse
                      .respond200WithApplicationJson(ContributorNameType.get(0))));
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
    handler.handle(Future.succeededFuture(DeleteContributorNameTypesByContributorNameTypeIdResponse
        .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
  }

  @Validate
  @Override
  public void deleteContributorNameTypesByContributorNameTypeId(String ContributorNameTypeId, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.tenantId(okapiHeaders);
        PostgresClient postgres = PostgresClient.getInstance(vertxContext.owner(), tenantId);
        postgres.delete(CONTRIBUTOR_NAME_TYPE_TABLE, ContributorNameTypeId,
            reply -> {
              try {
                if (reply.failed()) {
                  String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                  if (msg == null) {
                    internalServerErrorDuringDelete(reply.cause(), lang, asyncResultHandler);
                    return;
                  }
                  log.info(msg);
                  asyncResultHandler.handle(Future.succeededFuture(DeleteContributorNameTypesByContributorNameTypeIdResponse
                      .respond400WithTextPlain(msg)));
                  return;
                }
                int updated = reply.result().getUpdated();
                if (updated != 1) {
                  String msg = messages.getMessage(lang, MessageConsts.DeletedCountError, 1, updated);
                  log.error(msg);
                  asyncResultHandler.handle(Future.succeededFuture(DeleteContributorNameTypesByContributorNameTypeIdResponse
                      .respond404WithTextPlain(msg)));
                  return;
                }
                asyncResultHandler.handle(Future.succeededFuture(DeleteContributorNameTypesByContributorNameTypeIdResponse
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
    handler.handle(Future.succeededFuture(PutContributorNameTypesByContributorNameTypeIdResponse
        .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
  }

  @Validate
  @Override
  public void putContributorNameTypesByContributorNameTypeId(String ContributorNameTypeId, String lang, ContributorNameType entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    vertxContext.runOnContext(v -> {
      String tenantId = TenantTool.tenantId(okapiHeaders);
      try {
        if (entity.getId() == null) {
          entity.setId(ContributorNameTypeId);
        }
        PostgresClient.getInstance(vertxContext.owner(), tenantId).update(
            CONTRIBUTOR_NAME_TYPE_TABLE, entity, ContributorNameTypeId,
            reply -> {
              try {
                if (reply.succeeded()) {
                  if (reply.result().getUpdated() == 0) {
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutContributorNameTypesByContributorNameTypeIdResponse
                        .respond404WithTextPlain(messages.getMessage(lang, MessageConsts.NoRecordsUpdated))));
                  } else{
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutContributorNameTypesByContributorNameTypeIdResponse
                        .respond204()));
                  }
                } else {
                  String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                  if (msg == null) {
                    internalServerErrorDuringPut(reply.cause(), lang, asyncResultHandler);
                    return;
                  }
                  log.info(msg);
                  asyncResultHandler.handle(Future.succeededFuture(PutContributorNameTypesByContributorNameTypeIdResponse
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
