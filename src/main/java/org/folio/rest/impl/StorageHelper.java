package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.resource.LocationUnitsResource;
import org.folio.rest.jaxrs.resource.support.ResponseWrapper;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PgExceptionUtil;
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

  protected static void deleteById(String table, String id,
      Map<String, String> okapiHeaders, Context vertxContext, Handler<AsyncResult<Response>> asyncResultHandler,
      Supplier<ResponseWrapper> deleted,
      Function<String,ResponseWrapper> internalError) {
    PostgresClient postgresClient = StorageHelper.postgresClient(vertxContext, okapiHeaders);
    postgresClient.delete(table, id, reply -> {
      if (reply.succeeded()) {
        asyncResultHandler.handle(Future.succeededFuture(deleted.get()));
      } else {
        asyncResultHandler.handle(Future.succeededFuture(internalError.apply(
            "Error while deleting " + id + " from " + table)));
      }
    });
  }

  protected static <T> void getById(String table, Class<T> clazz, String id,
      Map<String, String> okapiHeaders, Context vertxContext,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Function<T,ResponseWrapper> found,
      Function<String,ResponseWrapper> notFound,
      Function<String,ResponseWrapper> error) {
    PostgresClient postgresClient = postgresClient(vertxContext, okapiHeaders);
    try {
      UUID.fromString(id);   // syntax check to prevent sql injection
    } catch (Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(error.apply(e.getMessage())));
      return;
    }
    String where = "WHERE _id='" + id + "'";
    // TODO: switch to RMB 21.0.0 and use postgresClient.getById and drop above uuid check
    // because getById prevents sql injection by using a prepared/parameterized statement
    postgresClient.get(table, clazz, where, false, false, reply -> {
      if (reply.failed()) {
        asyncResultHandler.handle(Future.succeededFuture(error.apply(reply.cause().getMessage())));
        return;
      }
      List<T> results = (List<T>) reply.result().getResults();
      if (results.isEmpty()) {
        asyncResultHandler.handle(Future.succeededFuture(notFound.apply("Not found")));
        return;
      }
      asyncResultHandler.handle(Future.succeededFuture(found.apply(results.get(0))));
    });
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
   * @param table  table name
   * @param entity  the entity to post. If the id field is missing or null it is set to a random UUID.
   * @param created  how to create a ResponseWrapper from a location URI and a StreamingOutput
   * @param error  how to create a ResponseWrapper from an error message
   * @param internalError  how to create a ResponseWrapper from an internal error message
   */
  protected static <T> void post(String table, T entity,
      Map<String, String> okapiHeaders, Context vertxContext,
      Handler<AsyncResult<Response>> asyncResultHandler,
      BiFunction<String,StreamingOutput,ResponseWrapper> created,
      Function<String,ResponseWrapper> error,
      Function<String,ResponseWrapper> internalError) {
    try {
      String id = initId(entity);
      PostgresClient postgresClient = postgresClient(vertxContext, okapiHeaders);
      postgresClient.save(table, id, entity, reply -> {
        if (reply.succeeded()) {
          OutStream stream = new OutStream();
          stream.setData(entity);
          asyncResultHandler.handle(Future.succeededFuture(created.apply(reply.result(), stream)));
          return;
        }
        String message = PgExceptionUtil.badRequestMessage(reply.cause());
        if (message != null) {
          asyncResultHandler.handle(Future.succeededFuture(error.apply(message)));
        } else {
          asyncResultHandler.handle(Future.succeededFuture(internalError.apply(reply.cause().getMessage())));
        }
      });
    } catch (Exception e) {
      logger.error(e.getMessage(), e.getCause());
      asyncResultHandler.handle(Future.succeededFuture(internalError.apply(
          e.getClass().getName() + ": " + e.getMessage())));
    }
  }

  /**
   * Put entity to table.
   * @param table  table name
   * @param entity  the new entity to store. The id field is set to the id value.
   * @param id  the id value to use for entity
   * @param updated  how to create a ResponseWrapper about a successful update
   * @param error  how to create a ResponseWrapper from an error message
   * @param internalError  how to create a ResponseWrapper from an internal error message
   */
  protected static <T> void put(String table, T entity, String id,
      Map<String, String> okapiHeaders, Context vertxContext,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Supplier<ResponseWrapper> updated,
      Function<String,ResponseWrapper> error,
      Function<String,ResponseWrapper> internalError) {
    try {
      setId(entity, id);
      PostgresClient postgresClient = postgresClient(vertxContext, okapiHeaders);
      postgresClient.upsert(table, id, entity, reply -> {
        if (reply.succeeded()) {
          asyncResultHandler.handle(Future.succeededFuture(updated.get()));
          return;
        }
        String message = PgExceptionUtil.badRequestMessage(reply.cause());
        if (message != null) {
          asyncResultHandler.handle(Future.succeededFuture(error.apply(message)));
        } else {
          asyncResultHandler.handle(Future.succeededFuture(internalError.apply(reply.cause().getMessage())));
        }
      });
    } catch (Exception e) {
      logger.error(e.getMessage(), e.getCause());
      asyncResultHandler.handle(Future.succeededFuture(internalError.apply(
          e.getClass().getName() + ": " + e.getMessage())));
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
      Criteria criteria = new Criteria(schemaPath);
      criteria.addField(LocationUnitAPI.ID_FIELD_NAME);
      criteria.setOperation("=");
      criteria.setValue(id);
      return new Criterion(criteria);
    } catch (Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(
        Future.succeededFuture(
          LocationUnitsResource.GetLocationUnitsInstitutionsByIdResponse
            .withPlainInternalServerError(message)));
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
