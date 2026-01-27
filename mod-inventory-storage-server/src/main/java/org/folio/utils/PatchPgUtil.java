package org.folio.utils;

import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.rest.persist.PgUtil.response;
import static org.folio.rest.persist.PgUtil.responseInvalidUuid;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import java.lang.reflect.Method;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.resource.support.ResponseDelegate;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.UpdateSection;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.util.UuidUtil;

public class PatchPgUtil {
  private static final String RESPOND_204                       = "respond204";
  private static final String RESPOND_400_WITH_TEXT_PLAIN       = "respond400WithTextPlain";
  private static final String RESPOND_404_WITH_TEXT_PLAIN       = "respond404WithTextPlain";
  private static final String RESPOND_409_WITH_TEXT_PLAIN       = "respond409WithTextPlain";
  private static final String RESPOND_500_WITH_TEXT_PLAIN       = "respond500WithTextPlain";
  private static final String NOT_FOUND = "Not found";

  private static final Logger logger = LogManager.getLogger();

  /**
   * Patch entity in the table.
   *
   * <p>To enforce optimistic locking the version of entity is set to null if it is -1.
   *
   * <p>All exceptions are caught and reported via the asyncResultHandler.
   *
   * @param table  table name
   * @param updateSection  patch data
   * @param id  the id value to use for entity
   * @param okapiHeaders  http headers provided by okapi
   * @param vertxContext  the current context
   * @param clazz  the ResponseDelegate class created from the RAML file with these methods:
   *               respond204(), respond400WithTextPlain(Object), respond404WithTextPlain(Object),
   *               respond409WithTextPlain(Object), respond500WithTextPlain(Object).
   * @param asyncResultHandler  where to return the result created by clazz
   */
  public static void patch(String table, UpdateSection updateSection, String id,
    Map<String, String> okapiHeaders, Context vertxContext,
    Class<? extends ResponseDelegate> clazz,
    Handler<AsyncResult<Response>> asyncResultHandler) {
    patch(table, updateSection, id, okapiHeaders, vertxContext, clazz).onComplete(asyncResultHandler);
  }

  /**
   * Put entity to table.
   *
   * <p>To enforce optimistic locking the version of entity is set to null if it is -1.
   *
   * <p>All exceptions are caught and reported via the asyncResultHandler.
   *
   * @param table  table name
   * @param updateSection  the new entity to store. The id field is set to the id value.
   * @param id  the id value to use for entity
   * @param okapiHeaders  http headers provided by okapi
   * @param vertxContext  the current context
   * @param clazz  the ResponseDelegate class created from the RAML file with these methods:
   *               respond204(), respond400WithTextPlain(Object), respond404WithTextPlain(Object),
   *               respond409WithTextPlain(Object), respond500WithTextPlain(Object).
   * @return   where to return the result created by clazz
   */
  @SuppressWarnings("checkstyle:methodlength")
  public static Future<Response> patch(String table, UpdateSection updateSection, String id,
    Map<String, String> okapiHeaders, Context vertxContext,
    Class<? extends ResponseDelegate> clazz) {

    var criterion = new Criterion().addCriterion(new Criteria()
      .addField("'id'").setOperation("=").setVal(id));

    final Method respond500;

    try {
      respond500 = clazz.getMethod(RESPOND_500_WITH_TEXT_PLAIN, Object.class);
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      return response(e.getMessage(), null, null);
    }

    try {
      Method respond204 = clazz.getMethod(RESPOND_204);
      Method respond400 = clazz.getMethod(RESPOND_400_WITH_TEXT_PLAIN, Object.class);
      Method respond404 = clazz.getMethod(RESPOND_404_WITH_TEXT_PLAIN, Object.class);
      Method respond409 = getRespond409(clazz);
      if (! UuidUtil.isUuid(id)) {
        return responseInvalidUuid(table + ".id", id, clazz, respond400, respond500);
      }

      final Promise<Response> promise = Promise.promise();
      PostgresClient postgresClient = postgresClient(vertxContext, okapiHeaders);
      postgresClient.update(table, updateSection, criterion, false, reply -> {
        if (reply.failed()) {
          if (PgExceptionUtil.isVersionConflict(reply.cause())) {
            Method method = respond409 == null ? respond400 : respond409;
            response(reply.cause().getMessage(), method, respond500).onComplete(promise);
          } else {
            response(table, id, reply.cause(), clazz, respond400, respond500).onComplete(promise);
          }
          return;
        }
        int updated = reply.result().rowCount();
        if (updated == 0) {
          response(NOT_FOUND, respond404, respond500).onComplete(promise);
          return;
        }
        if (updated != 1) {
          String message = "Patched " + updated + " records in " + table + " for id: " + id;
          logger.fatal(message);
          response(message, respond500, respond500).onComplete(promise);
          return;
        }
        response(respond204, respond500).onComplete(promise);
      });
      return promise.future();
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      return response(e.getMessage(), respond500, respond500);
    }
  }

  private static Method getRespond409(Class<? extends ResponseDelegate> clazz) {
    Method respond409;
    try {
      respond409 = clazz.getMethod(RESPOND_409_WITH_TEXT_PLAIN, Object.class);
    } catch (NoSuchMethodException e) {
      logger.warn("Response 409 is not defined for class {}", clazz);
      respond409 = null;
    }
    return respond409;
  }
}
