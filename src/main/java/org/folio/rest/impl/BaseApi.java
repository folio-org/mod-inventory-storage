package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.folio.rest.tools.messages.Messages.DEFAULT_LANGUAGE;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import java.util.function.Predicate;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.resource.support.ResponseDelegate;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.support.PostgresClientFactory;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;

public abstract class BaseApi<E, C> {

  protected static final Logger log = LogManager.getLogger();
  protected static final Messages MESSAGES = Messages.getInstance();

  public void getEntities(String query, String totalRecords, int offset, int limit, Map<String, String> okapiHeaders,
                          Handler<AsyncResult<Response>> responseHandler, Context vertxContext,
                          Class<? extends ResponseDelegate> responseType) {
    runInContext(vertxContext, responseHandler,
      v -> PgUtil.get(getReferenceTable(), getEntityClass(), getEntityCollectionClass(), query, totalRecords,
        offset, limit, okapiHeaders, vertxContext, responseType, responseHandler));
  }

  public void getEntityById(String id, Map<String, String> okapiHeaders,
                            Handler<AsyncResult<Response>> responseHandler, Context vertxContext,
                            Class<? extends ResponseDelegate> responseType) {
    runInContext(vertxContext, responseHandler,
      v -> PgUtil.getById(getReferenceTable(), getEntityClass(), id, okapiHeaders, vertxContext, responseType,
        responseHandler));
  }

  public void postEntity(E entity, Map<String, String> okapiHeaders,
                         Handler<AsyncResult<Response>> responseHandler, Context vertxContext,
                         Class<? extends ResponseDelegate> responseType) {
    runInContext(vertxContext, responseHandler,
      v -> PgUtil.post(getReferenceTable(), entity, okapiHeaders, vertxContext, responseType, responseHandler));
  }

  public void deleteEntities(Map<String, String> okapiHeaders,
                             Handler<AsyncResult<Response>> responseHandler, Context vertxContext) {
    runInContext(vertxContext, responseHandler,
      v -> PostgresClientFactory.getInstance(vertxContext, okapiHeaders)
        .delete(getReferenceTable(), new Criterion(),
          reply -> {
            if (reply.succeeded()) {
              responseHandler.handle(succeededFuture(Response.status(Response.Status.NO_CONTENT).build()));
            } else {
              respond500WithTextPlain(responseHandler, reply.cause());
            }
          }));
  }

  public void deleteEntityById(String id, Map<String, String> okapiHeaders,
                               Handler<AsyncResult<Response>> responseHandler, Context vertxContext,
                               Class<? extends ResponseDelegate> responseType) {
    runInContext(vertxContext, responseHandler,
      v -> {
        if (deleteValidationPredicates() == null || deleteValidationPredicates().isEmpty()) {
          PgUtil.deleteById(getReferenceTable(), id, okapiHeaders, vertxContext, responseType, responseHandler);
        } else {
          deleteWithValidation(id, okapiHeaders, responseHandler, vertxContext, responseType);
        }
      });
  }

  public void putEntityById(String id, E entity, Map<String, String> okapiHeaders,
                            Handler<AsyncResult<Response>> responseHandler, Context vertxContext,
                            Class<? extends ResponseDelegate> responseType) {
    runInContext(vertxContext, responseHandler,
      v -> PgUtil.put(getReferenceTable(), entity, id, okapiHeaders, vertxContext, responseType, responseHandler));
  }

  protected abstract String getReferenceTable();

  protected abstract Class<E> getEntityClass();

  protected abstract Class<C> getEntityCollectionClass();

  protected Map<String, Predicate<E>> deleteValidationPredicates() {
    return Map.of();
  }

  private void deleteWithValidation(String id, Map<String, String> okapiHeaders,
                                    Handler<AsyncResult<Response>> responseHandler,
                                    Context vertxContext, Class<? extends ResponseDelegate> responseType) {
    PostgresClientFactory.getInstance(vertxContext, okapiHeaders)
      .getById(getReferenceTable(), id, getEntityClass(),
        reply -> {
          if (!reply.succeeded()) {
            log.error("Failed to fetch entity for deletion: {}", reply.cause().getMessage(), reply.cause());
            respond400WithTextPlain(responseHandler, "Failed to fetch entity: " + reply.cause().getMessage());
            return;
          }
          E entity = reply.result();
          if (entity == null) {
            var msg = "Entity with id '%s' not found for deletion".formatted(id);
            log.warn(msg);
            respond404WithTextPlain(responseHandler, msg);
            return;
          }
          for (var predicateEntry : deleteValidationPredicates().entrySet()) {
            if (!predicateEntry.getValue().test(entity)) {
              log.warn("Delete validation failed for id {}: {}", id, predicateEntry.getKey());
              respond400WithTextPlain(responseHandler, predicateEntry.getKey());
              return;
            }
          }
          PgUtil.deleteById(getReferenceTable(), id, okapiHeaders, vertxContext, responseType, responseHandler);
        });
  }

  private void respond400WithTextPlain(Handler<AsyncResult<Response>> responseHandler, String message) {
    var responseBuilder = Response.status(Response.Status.BAD_REQUEST)
      .header(CONTENT_TYPE.toString(), TEXT_PLAIN)
      .entity(message);
    responseHandler.handle(succeededFuture(responseBuilder.build()));
  }

  private void respond404WithTextPlain(Handler<AsyncResult<Response>> responseHandler, String message) {
    var responseBuilder = Response.status(Response.Status.NOT_FOUND)
      .header(CONTENT_TYPE.toString(), TEXT_PLAIN)
      .entity(message);
    responseHandler.handle(succeededFuture(responseBuilder.build()));
  }

  private void respond500WithTextPlain(Handler<AsyncResult<Response>> responseHandler, Throwable e) {
    log.error(e.getMessage(), e);
    var responseBuilder = Response.status(Response.Status.INTERNAL_SERVER_ERROR)
      .header(CONTENT_TYPE.toString(), TEXT_PLAIN)
      .entity(MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError));
    responseHandler.handle(succeededFuture(responseBuilder.build()));
  }

  private void runInContext(Context context, Handler<AsyncResult<Response>> responseHandler, Handler<Void> action) {
    context.runOnContext(v -> {
      try {
        action.handle(v);
      } catch (Exception e) {
        respond500WithTextPlain(responseHandler, e);
      }
    });
  }
}
