package org.folio.rest.impl;

import static org.folio.rest.support.ResponseHandlerUtil.firstErrorMessage;
import static org.folio.rest.support.ResponseHandlerUtil.handleFailure;
import static org.folio.rest.tools.utils.ValidationHelper.createValidationErrorMessage;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.ObjectUtils;
import org.folio.rest.annotations.Validate;
import org.folio.rest.exceptions.ValidationException;
import org.folio.rest.jaxrs.model.InstancePrecedingSucceedingTitle;
import org.folio.rest.jaxrs.model.InstancePrecedingSucceedingTitles;
import org.folio.rest.tools.utils.MetadataUtil;
import org.folio.rest.tools.utils.ValidationHelper;
import org.folio.services.referencedata.ReferenceDataService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("java:S6813")
public class PrecedingSucceedingTitleApi implements org.folio.rest.jaxrs.resource.PrecedingSucceedingTitles {

  private static final String TABLE = "preceding_succeeding_title";
  private static final Class<InstancePrecedingSucceedingTitle> TITLE = InstancePrecedingSucceedingTitle.class;

  @Autowired
  private ReferenceDataService referenceDataService;

  public PrecedingSucceedingTitleApi() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Validate
  @Override
  public void getPrecedingSucceedingTitles(String totalRecords, int offset, int limit, String query,
                                           Map<String, String> headers,
                                           Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    referenceDataService.query(TABLE, TITLE, query, offset, limit, totalRecords, headers)
      .map(results -> new InstancePrecedingSucceedingTitles()
        .withPrecedingSucceedingTitles(results.getResults())
        .withTotalRecords(results.getResultInfo().getTotalRecords()))
      .map(GetPrecedingSucceedingTitlesResponse::respond200WithApplicationJson)
      .onSuccess(response -> asyncResultHandler.handle(Future.succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void postPrecedingSucceedingTitles(InstancePrecedingSucceedingTitle entity,
                                            Map<String, String> headers,
                                            Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    if (precedingAndSucceedingInstanceEmpty(entity)) {
      handlePrecedingAndSucceedingEmptyError(asyncResultHandler);
      return;
    }
    referenceDataService.create(TABLE, TITLE, entity, headers)
      .map(created -> PostPrecedingSucceedingTitlesResponse.respond201WithApplicationJson(created,
        PostPrecedingSucceedingTitlesResponse.headersFor201().withLocation(created.getId())))
      .onSuccess(response -> asyncResultHandler.handle(Future.succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void getPrecedingSucceedingTitlesByPrecedingSucceedingTitleId(String precedingSucceedingTitleId,
                                                                       Map<String, String> headers,
                                                                       Handler<AsyncResult<Response>> resultHandler,
                                                                       Context vertxContext) {
    referenceDataService.getById(TABLE, TITLE, precedingSucceedingTitleId, headers)
      .map(GetPrecedingSucceedingTitlesByPrecedingSucceedingTitleIdResponse::respond200WithApplicationJson)
      .onSuccess(response -> resultHandler.handle(Future.succeededFuture(response)))
      .onFailure(handleFailure(resultHandler));
  }

  @Validate
  @Override
  public void putPrecedingSucceedingTitlesByPrecedingSucceedingTitleId(String precedingSucceedingTitleId,
                                                                       InstancePrecedingSucceedingTitle entity,
                                                                       Map<String, String> headers,
                                                                       Handler<AsyncResult<Response>> resultHandler,
                                                                       Context vertxContext) {

    if (precedingAndSucceedingInstanceEmpty(entity)) {
      handlePrecedingAndSucceedingEmptyError(resultHandler);
      return;
    }
    referenceDataService.update(TABLE, TITLE, precedingSucceedingTitleId, entity, headers)
      .map(notUsed -> PutPrecedingSucceedingTitlesByPrecedingSucceedingTitleIdResponse.respond204())
      .onSuccess(response -> resultHandler.handle(Future.succeededFuture(response)))
      .onFailure(handleFailure(resultHandler));
  }

  @Validate
  @Override
  public void deletePrecedingSucceedingTitlesByPrecedingSucceedingTitleId(String precedingSucceedingTitleId,
                                                                          Map<String, String> headers,
                                                                          Handler<AsyncResult<Response>> resultHandler,
                                                                          Context vertxContext) {
    referenceDataService.delete(TABLE, TITLE, precedingSucceedingTitleId, headers)
      .map(notUsed -> DeletePrecedingSucceedingTitlesByPrecedingSucceedingTitleIdResponse.respond204())
      .onSuccess(response -> resultHandler.handle(Future.succeededFuture(response)))
      .onFailure(handleFailure(resultHandler, errors ->
        DeletePrecedingSucceedingTitlesByPrecedingSucceedingTitleIdResponse.respond400WithTextPlain(
          firstErrorMessage(errors))));
  }

  @Validate
  @Override
  public void putPrecedingSucceedingTitlesInstancesByInstanceId(String instanceId,
                                                                InstancePrecedingSucceedingTitles entity,
                                                                Map<String, String> headers,
                                                                Handler<AsyncResult<Response>> asyncResultHandler,
                                                                Context vertxContext) {
    var titles = entity.getPrecedingSucceedingTitles();
    boolean areValidTitles = validatePrecedingSucceedingTitles(titles, instanceId, asyncResultHandler);
    if (areValidTitles) {
      var cqlQuery = String.format("succeedingInstanceId==(%s) or precedingInstanceId==(%s)", instanceId, instanceId);
      referenceDataService.deleteByQuery(TABLE, TITLE, cqlQuery, headers)
        .compose(notUsed -> saveCollection(titles, headers))
        .onSuccess(response -> asyncResultHandler.handle(Future.succeededFuture(response)))
        .onFailure(handleFailure(asyncResultHandler));
    }
  }

  private boolean validatePrecedingSucceedingTitles(List<InstancePrecedingSucceedingTitle> precedingSucceedingTitles,
                                                    String instanceId,
                                                    Handler<AsyncResult<Response>> asyncResultHandler) {
    for (var precedingSucceedingTitle : precedingSucceedingTitles) {
      if (!titleIsLinkedToInstanceId(precedingSucceedingTitle, instanceId)) {
        var validationErrorMessage =
          createValidationErrorMessage("precedingInstanceId or succeedingInstanceId", "",
            String.format("The precedingInstanceId or succeedingInstanceId should contain instanceId [%s]",
              instanceId));
        asyncResultHandler.handle(Future.succeededFuture(PutPrecedingSucceedingTitlesInstancesByInstanceIdResponse
          .respond422WithApplicationJson(validationErrorMessage)));
        return false;
      }
    }
    return true;
  }

  private boolean titleIsLinkedToInstanceId(InstancePrecedingSucceedingTitle precedingSucceedingTitle,
                                            String instanceId) {
    return instanceId.equals(precedingSucceedingTitle.getPrecedingInstanceId())
      || instanceId.equals(precedingSucceedingTitle.getSucceedingInstanceId());
  }

  private Future<Response> saveCollection(List<InstancePrecedingSucceedingTitle> entities,
                                          Map<String, String> headers) {
    try {
      MetadataUtil.populateMetadata(entities, headers);
      return referenceDataService.saveBatch(TABLE, TITLE,
          entities, headers)
        .<Response>map(x -> PutPrecedingSucceedingTitlesInstancesByInstanceIdResponse.respond204())
        .otherwise(cause -> {
          var errorMessage = cause.getMessage();
          if (isMissingInstance(cause) || ValidationHelper.isFKViolation(errorMessage)) {
            return PutPrecedingSucceedingTitlesInstancesByInstanceIdResponse.respond404WithTextPlain(
              "Instance not found");
          } else {
            return PutPrecedingSucceedingTitlesInstancesByInstanceIdResponse.respond500WithTextPlain(errorMessage);
          }
        });
    } catch (Exception e) {
      return Future.succeededFuture(
        PutPrecedingSucceedingTitlesInstancesByInstanceIdResponse.respond500WithTextPlain(e.getMessage()));
    }
  }

  private static boolean isMissingInstance(Throwable cause) {
    return cause instanceof ValidationException validationException
      && firstErrorMessage(validationException.getErrors()).contains("does not exist in");
  }

  private boolean precedingAndSucceedingInstanceEmpty(InstancePrecedingSucceedingTitle entity) {
    return ObjectUtils.isEmpty(entity.getPrecedingInstanceId()) && ObjectUtils.isEmpty(
      entity.getSucceedingInstanceId());
  }

  private void handlePrecedingAndSucceedingEmptyError(Handler<AsyncResult<Response>> asyncResultHandler) {
    asyncResultHandler.handle(Future.succeededFuture(
      PostPrecedingSucceedingTitlesResponse.respond422WithApplicationJson(
        ValidationHelper.createValidationErrorMessage("", "",
          "The precedingInstanceId and succeedingInstanceId can't be empty at the same time"))));
  }
}
