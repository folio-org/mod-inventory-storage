package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import javax.ws.rs.core.Response;

import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.resource.LocationUnits;
import org.folio.rest.jaxrs.resource.support.ResponseDelegate;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.TenantTool;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;

/**
 * Small helpers for mod-inventory-storage.
 */
public final class StorageHelper {

  private static Logger logger = LoggerFactory.getLogger(StorageHelper.class);

  private static final String RESPOND_500_WITH_TEXT_PLAIN = "respond500WithTextPlain";

  private StorageHelper() {
    throw new UnsupportedOperationException("Cannot instantiate utility class");
  }

  protected static String logAndSaveError(Throwable err) {
    String message = err.getLocalizedMessage();
    logger.error(message, err);
    return message;
  }

  protected static boolean isDuplicate(String message) {
    return message != null && message.contains("duplicate key value violates unique constraint");
  }

  protected static boolean isInUse(String message) {
    return message != null && message.contains("is still referenced");
  }

  /**
   * Create a Response using okMethod(entity, headersMethod().withLocationMethod(location)).
   * On exception create a Response using failResponseMethod.
   *
   * <p>All exceptions are caught and reported via the returned Future.
   */
  static <T> Future<Response> response(T entity, String location,
      Method headersMethod, Method withLocationMethod,
      Method okResponseMethod, Method failResponseMethod) {
    try {
      OutStream stream = new OutStream();
      stream.setData(entity);
      Object headers = headersMethod.invoke(null);
      withLocationMethod.invoke(headers, location);
      Response response = (Response) okResponseMethod.invoke(null, entity, headers);
      return Future.succeededFuture(response);
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      try {
        Response response = (Response) failResponseMethod.invoke(null, e.getMessage());
        return Future.succeededFuture(response);
      } catch (Exception innerException) {
        logger.error(innerException.getMessage(), innerException);
        return Future.failedFuture(innerException);
      }
    }
  }

  /**
   * Create a Response using valueMethod(T).
   * On exception create a Response using failResponseMethod(String exceptionMessage).
   * If that also throws an exception create a failed future.
   *
   * <p>All exceptions are caught and reported via the returned Future.
   */
  static <T> Future<Response> response(T value, Method valueMethod, Method failResponseMethod) {
    try {
      // the null check is redundant but avoids several sonarlint warnings
      if (valueMethod == null) {
        throw new NullPointerException("messageMethod must not be null (" + value + ")");
      }
      if (failResponseMethod == null) {
        throw new NullPointerException("failResponseMethod must not be null (" + value + ")");
      }
      Response response = (Response) valueMethod.invoke(null, value);
      return Future.succeededFuture(response);
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      try {
        if (failResponseMethod == null) {
          return Future.failedFuture(e);
        }
        Response response = (Response) failResponseMethod.invoke(null, e.getMessage());
        return Future.succeededFuture(response);
      } catch (Exception innerException) {
        logger.error(innerException.getMessage(), innerException);
        return Future.failedFuture(innerException);
      }
    }
  }

  /**
   * Return a Response using responseMethod() wrapped in a succeeded future.
   * On exception create a Response using failResponseMethod(String exceptionMessage)
   * wrapped in a succeeded future.
   * If that also throws an exception create a failed future.
   *
   * <p>All exceptions are caught and reported via the returned Future.
   */
  static Future<Response> response(Method responseMethod, Method failResponseMethod) {
    try {
      // the null check is redundant but avoids several sonarlint warnings
      if (responseMethod == null) {
        throw new NullPointerException("responseMethod must not be null");
      }
      if (failResponseMethod == null) {
        throw new NullPointerException("failResponseMethod must not be null");
      }
      Response response = (Response) responseMethod.invoke(null);
      return Future.succeededFuture(response);
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      try {
        if (failResponseMethod == null) {
          return Future.failedFuture(e);
        }
        Response response = (Response) failResponseMethod.invoke(null, e.getMessage());
        return Future.succeededFuture(response);
      } catch (Exception innerException) {
        logger.error(innerException.getMessage(), innerException);
        return Future.failedFuture(innerException);
      }
    }
  }

  /**
   * Delete a record from a table.
   *
   * <p>All exceptions are caught and reported via the asyncResultHandler.
   *
   * @deprecated use {@link PgUtil#deleteById(String, String, Map, Context, Class, Handler)} instead.
   * @param table  where to delete
   * @param id  the primary key of the record to delete
   * @param okapiHeaders  http headers provided by okapi
   * @param vertxContext  the current context
   * @param clazz  the ResponseDelegate class created from the RAML file with these methods:
   *               respond204(), respond500WithTextPlain(Object).
   * @param asyncResultHandler  where to return the result created using clazz
   * @param deleted
   * @param internalError
   */
  @Deprecated
  protected static void deleteById(String table, String id,
      Map<String, String> okapiHeaders, Context vertxContext,
      Class<? extends ResponseDelegate> clazz,
      Handler<AsyncResult<Response>> asyncResultHandler) {

    final Method respond500;
    try {
      respond500 = clazz.getMethod(RESPOND_500_WITH_TEXT_PLAIN, Object.class);
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      asyncResultHandler.handle(response(e.getMessage(), null, null));
      return;
    }

    try {
      Method respond204 = clazz.getMethod("respond204");
      PostgresClient postgresClient = StorageHelper.postgresClient(vertxContext, okapiHeaders);
      postgresClient.delete(table, id, reply -> {
        if (reply.succeeded()) {
          asyncResultHandler.handle(response(respond204, respond500));
          return;
        }
        String message = PgExceptionUtil.badRequestMessage(reply.cause());
        if (message == null) {
          message = reply.cause().getMessage();
        }
        asyncResultHandler.handle(response(message, respond500, respond500));
      });
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      asyncResultHandler.handle(response(e.getMessage(), respond500, respond500));
    }
  }

  /**
   * Get a record by id.
   *
   * <p>All exceptions are caught and reported via the asyncResultHandler.
   *
   * @deprecated use {@link PgUtil#getById(String, Class, String, Map, Context, Class, Handler)} instead.
   * @param table  the table that contains the record
   * @param id  the primary key of the record to get
   * @param okapiHeaders  http headers provided by okapi
   * @param vertxContext  the current context
   * @param clazz  the ResponseDelegate class created from the RAML file with these methods:
   *               respond200(T), respond500WithTextPlain(Object).
   * @param asyncResultHandler  where to return the result created using clazz
   */
  @Deprecated
  protected static <T> void getById(String table, Class<T> clazz, String id,
      Map<String, String> okapiHeaders, Context vertxContext,
      Class<? extends ResponseDelegate> responseDelegateClass,
      Handler<AsyncResult<Response>> asyncResultHandler) {

    final Method respond500;
    try {
      respond500 = responseDelegateClass.getMethod(RESPOND_500_WITH_TEXT_PLAIN, Object.class);
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      asyncResultHandler.handle(response(e.getMessage(), null, null));
      return;
    }
    try {
      Method respond200 = responseDelegateClass.getMethod("respond200WithApplicationJson", clazz);
      Method respond404 = responseDelegateClass.getMethod("respond404WithTextPlain", Object.class);
      PostgresClient postgresClient = postgresClient(vertxContext, okapiHeaders);
      postgresClient.getById(table, id, clazz, reply -> {
        if (reply.failed()) {
          asyncResultHandler.handle(response(reply.cause().getMessage(), respond500, respond500));
          return;
        }
        if (reply.result() == null) {
          asyncResultHandler.handle(response("Not found", respond404, respond500));
          return;
        }
        asyncResultHandler.handle(response(reply.result(), respond200, respond500));
      });
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      asyncResultHandler.handle(response(e.getMessage(), respond500, respond500));
    }
  }

  /**
   * Return entity's id.
   *
   * <p>Use reflection, the POJOs don't have a interface/superclass in common.
   */
  private static Object getId(Object entity) throws ReflectiveOperationException {
    return entity.getClass().getDeclaredMethod("getId").invoke(entity);
  }

  /**
   * Set entity's id.
   *
   * <p>Use reflection, the POJOs don't have a interface/superclass in common.
   * @param entity  where to set the id field
   * @param id  the new id value
   */
  private static void setId(Object entity, String id) throws ReflectiveOperationException {
    entity.getClass().getDeclaredMethod("setId", String.class).invoke(entity, id);
  }

  /**
   * If entity's id field is null then initialize it with a random UUID.
   * @param entity  entity with id field
   * @return the value of the id field at the end
   * @throws NoSuchFieldException  if entity has no if field
   * @throws IllegalAccessException  if id field is not accessible
   * @throws SecurityException
   * @throws NoSuchMethodException
   * @throws IllegalArgumentException  if id field is not of type String
   */
  private static String initId(Object entity) throws ReflectiveOperationException {
    Object id = getId(entity);
    if (id != null) {
      return id.toString();
    }
    String idString = UUID.randomUUID().toString();
    setId(entity, idString);
    return idString;
  }

  /**
   * Post entity to table.
   *
   * <p>All exceptions are caught and reported via the asyncResultHandler.
   *
   * @deprecated use {@link PgUtil#post(String, Object, Map, Context, Class, Handler)} instead.
   * @param table  table name
   * @param entity  the entity to post. If the id field is missing or null it is set to a random UUID.
   * @param clazz  the ResponseDelegate class created from the RAML file with these methods: headersFor201(),
   *               respond201WithApplicationJson(Object, HeadersFor201), respond400WithTextPlain(Object),
   *               respond500WithTextPlain(Object).
   * @param asyncResultHandler  where to return the result created using clazz
   */
  @Deprecated
  protected static <T> void post(String table, T entity,
      Map<String, String> okapiHeaders, Context vertxContext,
      Class<? extends ResponseDelegate> clazz,
      Handler<AsyncResult<Response>> asyncResultHandler) {

    final Method respond500;

    try {
      respond500 = clazz.getMethod(RESPOND_500_WITH_TEXT_PLAIN, Object.class);
    } catch (Exception e) {
      asyncResultHandler.handle(response(e.getMessage(), null, null));
      return;
    }

    try {
      Method headersFor201Method = clazz.getMethod("headersFor201");
      String headersFor201ClassName = clazz.getName() + "$HeadersFor201";
      Class<?> headersFor201Class = null;
      for (Class<?> declaredClass : clazz.getDeclaredClasses()) {
        if (declaredClass.getName().equals(headersFor201ClassName)) {
          headersFor201Class = declaredClass;
          break;
        }
      }
      if (headersFor201Class == null) {
        throw new ClassNotFoundException(headersFor201ClassName + " not found in " + clazz.getCanonicalName());
      }
      Method withLocation = headersFor201Class.getMethod("withLocation", String.class);
      Method respond201 = clazz.getMethod("respond201WithApplicationJson", Object.class, headersFor201Class);
      Method respond400 = clazz.getMethod("respond400WithTextPlain", Object.class);

      String id = initId(entity);
      PostgresClient postgresClient = postgresClient(vertxContext, okapiHeaders);
      postgresClient.save(table, id, entity, reply -> {
        if (reply.succeeded()) {
          asyncResultHandler.handle(response(entity, reply.result(), headersFor201Method, withLocation,
              respond201, respond500));
          return;
        }
        String message = PgExceptionUtil.badRequestMessage(reply.cause());
        if (message != null) {
          asyncResultHandler.handle(response(message,                    respond400, respond500));
        } else {
          asyncResultHandler.handle(response(reply.cause().getMessage(), respond500, respond500));
        }
      });
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      asyncResultHandler.handle(response(e.getMessage(), respond500, respond500));
    }
  }

  /**
   * Put entity to table.
   *
   * <p>All exceptions are caught and reported via the asyncResultHandler.
   *
   * @deprecated use {@link PgUtil#put(String, Object, String, Map, Context, Class, Handler)} instead.
   * @param table  table name
   * @param entity  the new entity to store. The id field is set to the id value.
   * @param id  the id value to use for entity
   * @param clazz  the ResponseDelegate class created from the RAML file with these methods:
   *               respond204(), respond400WithTextPlain(Object), respond500WithTextPlain(Object).
   * @param asyncResultHandler  where to return the result created using clazz
   */
  @Deprecated
  protected static <T> void put(String table, T entity, String id,
      Map<String, String> okapiHeaders, Context vertxContext,
      Class<? extends ResponseDelegate> clazz,
      Handler<AsyncResult<Response>> asyncResultHandler) {

    final Method respond500;

    try {
      respond500 = clazz.getMethod(RESPOND_500_WITH_TEXT_PLAIN, Object.class);
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      asyncResultHandler.handle(response(e.getMessage(), null, null));
      return;
    }

    try {
      Method respond204 = clazz.getMethod("respond204");
      Method respond400 = clazz.getMethod("respond400WithTextPlain", Object.class);
      setId(entity, id);
      PostgresClient postgresClient = postgresClient(vertxContext, okapiHeaders);
      postgresClient.upsert(table, id, entity, reply -> {
        if (reply.succeeded()) {
          asyncResultHandler.handle(response(respond204, respond500));
          return;
        }
        String message = PgExceptionUtil.badRequestMessage(reply.cause());
        if (message != null) {
          asyncResultHandler.handle(response(message,                    respond400, respond500));
        } else {
          asyncResultHandler.handle(response(reply.cause().getMessage(), respond500, respond500));
        }
      });
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      asyncResultHandler.handle(response(e.getMessage(), respond500, respond500));
    }
  }

  protected static CQLWrapper getCQL(String query,
    int limit, int offset, String tableName) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(tableName + ".jsonb");
    return new CQLWrapper(cql2pgJson, query)
      .setLimit(new Limit(limit))
      .setOffset(new Offset(offset));
  }

  protected static String getTenant(Map<String, String> headers) {
    return TenantTool.calculateTenantId(headers.get(RestVerticle.OKAPI_HEADER_TENANT));
  }

  protected static Criterion idCriterion(String id, String schemaPath, Handler<AsyncResult<Response>> asyncResultHandler) {
    try {
      Criteria criteria = new Criteria();
      criteria.addField(LocationUnitAPI.ID_FIELD_NAME);
      criteria.setOperation("=");
      criteria.setValue(id);
      return new Criterion(criteria);
    } catch (Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(
        Future.succeededFuture(
          LocationUnits.GetLocationUnitsInstitutionsByIdResponse
            .respond500WithTextPlain(message)));
      // This is a bit dirty, but all those wrappers return the same kind of
      // response for InternalServerError, so we can use this from anywhere
      return null;
    }
  }

  /**
   * Return a PostgresClient.
   * @param vertxContext  Where to get a Vertx from.
   * @param okapiHeaders  Where to get the tenantId from.
   * @return the PostgresClient for the vertx and the tenantId
   */
  protected static PostgresClient postgresClient(Context vertxContext, Map<String, String> okapiHeaders) {
    return PostgresClient.getInstance(vertxContext.owner(), TenantTool.tenantId(okapiHeaders));
  }

}
