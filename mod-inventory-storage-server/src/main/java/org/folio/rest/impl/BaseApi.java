package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.folio.rest.support.ResponseHandlerUtil.failureResponse;
import static org.folio.rest.support.ResponseHandlerUtil.firstErrorMessage;
import static org.folio.rest.tools.messages.Messages.DEFAULT_LANGUAGE;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.persist.helper.EntityAccessors;
import org.folio.rest.persist.interfaces.Results;
import org.folio.rest.support.ResponseHandlerUtil;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.services.referencedata.ReferenceDataService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Base for the endpoints of simple reference data. It builds the same responses as the RMB generated
 * response classes do: POST reports validation failures as 422, the other methods as 400, except
 * GET by id which reports them as 404.
 */
@SuppressWarnings("java:S6813")
public abstract class BaseApi<E, C> {

  protected static final Logger log = LogManager.getLogger();
  protected static final Messages MESSAGES = Messages.getInstance();

  @Autowired
  private ReferenceDataService referenceDataService;

  protected BaseApi() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  public void getEntities(String query, String totalRecords, int offset, int limit, Map<String, String> headers,
                          Handler<AsyncResult<Response>> responseHandler) {
    respond(responseHandler, ResponseHandlerUtil::failureResponse,
      () -> referenceDataService.query(getReferenceTable(), getEntityClass(),
          query, offset, limit, totalRecords, headers)
        .map(results -> jsonResponse(200, collection(results))));
  }

  public void getEntityById(String id, Map<String, String> headers,
                            Handler<AsyncResult<Response>> responseHandler) {
    respond(responseHandler,
      failure -> failureResponse(failure, errors -> response404WithTextPlain(firstErrorMessage(errors))),
      () -> referenceDataService.getById(getReferenceTable(), getEntityClass(), id, headers)
        .map(entity -> jsonResponse(200, entity)));
  }

  public void postEntity(E entity, Map<String, String> headers,
                         Handler<AsyncResult<Response>> responseHandler) {
    postEntity(entity, headers, responseHandler, UnaryOperator.identity());
  }

  public void postEntity(E entity, Map<String, String> headers,
                         Handler<AsyncResult<Response>> responseHandler,
                         UnaryOperator<Response> responseMapper) {
    respond(responseHandler,
      failure -> responseMapper.apply(failureResponse(failure, errors -> jsonResponse(422, errors))),
      () -> referenceDataService.create(getReferenceTable(), getEntityClass(), entity, headers)
        .map(created -> responseMapper.apply(createdResponse(created))));
  }

  public void deleteEntities(Map<String, String> headers,
                             Handler<AsyncResult<Response>> responseHandler) {
    respond(responseHandler, this::internalServerError,
      () -> referenceDataService.deleteAll(getReferenceTable(), getEntityClass(), headers)
        .map(notUsed -> Response.status(Response.Status.NO_CONTENT).build()));
  }

  public void deleteEntityById(String id, Map<String, String> headers,
                               Handler<AsyncResult<Response>> responseHandler) {
    respond(responseHandler,
      failure -> failureResponse(failure, errors -> response400WithTextPlain(firstErrorMessage(errors))),
      () -> {
        var conditions = deleteValidationPredicates();
        var deleted = conditions == null || conditions.isEmpty()
                      ? referenceDataService.delete(getReferenceTable(), getEntityClass(), id, headers)
                      : referenceDataService.delete(getReferenceTable(), getEntityClass(), id, conditions, headers);
        return deleted.map(notUsed -> Response.status(Response.Status.NO_CONTENT).build());
      });
  }

  public void putEntityById(String id, E entity, Map<String, String> headers,
                            Handler<AsyncResult<Response>> responseHandler) {
    putEntityById(id, entity, headers, responseHandler, UnaryOperator.identity());
  }

  public void putEntityById(String id, E entity, Map<String, String> headers,
                            Handler<AsyncResult<Response>> responseHandler,
                            UnaryOperator<Response> responseMapper) {
    respond(responseHandler, failure -> responseMapper.apply(failureResponse(failure,
        errors -> response400WithTextPlain(firstErrorMessage(errors)))),
      () -> referenceDataService.update(getReferenceTable(), getEntityClass(), id, entity, headers)
        .map(notUsed -> responseMapper.apply(Response.status(Response.Status.NO_CONTENT).build())));
  }

  protected abstract String getReferenceTable();

  protected abstract Class<E> getEntityClass();

  protected abstract Class<C> getEntityCollectionClass();

  protected Map<String, Predicate<E>> deleteValidationPredicates() {
    return Map.of();
  }

  protected Response response400WithTextPlain(String message) {
    return Response.status(Response.Status.BAD_REQUEST)
      .header(CONTENT_TYPE, TEXT_PLAIN)
      .entity(message)
      .build();
  }

  protected Response response404WithTextPlain(String message) {
    return Response.status(Response.Status.NOT_FOUND)
      .header(CONTENT_TYPE, TEXT_PLAIN)
      .entity(message)
      .build();
  }

  protected Response response500WithTextPlain() {
    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
      .header(CONTENT_TYPE, TEXT_PLAIN)
      .entity(MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))
      .build();
  }

  private void respond(Handler<AsyncResult<Response>> responseHandler, Function<Throwable, Response> failureMapper,
                       java.util.function.Supplier<Future<Response>> action) {
    Future<Response> result;
    try {
      result = action.get();
    } catch (Exception e) {
      responseHandler.handle(succeededFuture(failureMapper.apply(e)));
      return;
    }
    result
      .onSuccess(response -> responseHandler.handle(succeededFuture(response)))
      .onFailure(failure -> responseHandler.handle(succeededFuture(failureMapper.apply(failure))));
  }

  private Response internalServerError(Throwable failure) {
    log.error(failure.getMessage(), failure);
    return response500WithTextPlain();
  }

  private Response createdResponse(E created) {
    return Response.status(201)
      .header(CONTENT_TYPE, APPLICATION_JSON)
      .header("Location", EntityAccessors.of(getEntityClass()).id(created))
      .entity(created)
      .build();
  }

  private static Response jsonResponse(int status, Object entity) {
    return Response.status(status).header(CONTENT_TYPE, APPLICATION_JSON).entity(entity).build();
  }

  private C collection(Results<E> results) {
    try {
      Method listSetter = null;
      for (Method method : getEntityCollectionClass().getMethods()) {
        if (method.getName().startsWith("set") && method.getParameterCount() == 1
            && List.class.equals(method.getParameterTypes()[0])) {
          listSetter = method;
          break;
        }
      }
      if (listSetter == null) {
        throw new NoSuchMethodException(getEntityCollectionClass().getName() + " has no set...(List) method");
      }
      var collection = getEntityCollectionClass().getDeclaredConstructor().newInstance();
      listSetter.invoke(collection, results.getResults());
      getEntityCollectionClass().getMethod("setTotalRecords", Integer.class)
        .invoke(collection, results.getResultInfo().getTotalRecords());
      return collection;
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
