package org.folio.rest.impl;

import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.rest.tools.utils.ValidationHelper.createValidationErrorMessage;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.ObjectUtils;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.PrecedingSucceedingTitle;
import org.folio.rest.jaxrs.model.PrecedingSucceedingTitles;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.tools.utils.MetadataUtil;
import org.folio.rest.tools.utils.ValidationHelper;

public class PrecedingSucceedingTitleApi implements org.folio.rest.jaxrs.resource.PrecedingSucceedingTitles {

  private static final String PRECEDING_SUCCEEDING_TITLE_TABLE = "preceding_succeeding_title";

  @Validate
  @Override
  public void getPrecedingSucceedingTitles(int offset, int limit, String query, String lang,
                                           Map<String, String> okapiHeaders,
                                           Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(PRECEDING_SUCCEEDING_TITLE_TABLE, PrecedingSucceedingTitle.class, PrecedingSucceedingTitles.class, query,
      offset, limit,
      okapiHeaders, vertxContext, GetPrecedingSucceedingTitlesResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void postPrecedingSucceedingTitles(String lang, PrecedingSucceedingTitle entity,
                                            Map<String, String> okapiHeaders,
                                            Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    if (precedingAndSucceedingInstanceEmpty(entity)) {
      handlePrecedingAndSucceedingEmptyError(asyncResultHandler);
      return;
    }
    PgUtil.post(PRECEDING_SUCCEEDING_TITLE_TABLE, entity, okapiHeaders, vertxContext,
      PostPrecedingSucceedingTitlesResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void getPrecedingSucceedingTitlesByPrecedingSucceedingTitleId(String precedingSucceedingTitleId, String lang,
                                                                       Map<String, String> okapiHeaders,
                                                                       Handler<AsyncResult<Response>> resultHandler,
                                                                       Context vertxContext) {
    PgUtil.getById(PRECEDING_SUCCEEDING_TITLE_TABLE, PrecedingSucceedingTitle.class, precedingSucceedingTitleId,
      okapiHeaders, vertxContext, GetPrecedingSucceedingTitlesByPrecedingSucceedingTitleIdResponse.class,
      resultHandler);
  }

  @Validate
  @Override
  public void putPrecedingSucceedingTitlesByPrecedingSucceedingTitleId(String precedingSucceedingTitleId, String lang,
                                                                       PrecedingSucceedingTitle entity,
                                                                       Map<String, String> okapiHeaders,
                                                                       Handler<AsyncResult<Response>> resultHandler,
                                                                       Context vertxContext) {

    if (precedingAndSucceedingInstanceEmpty(entity)) {
      handlePrecedingAndSucceedingEmptyError(resultHandler);
      return;
    }
    PgUtil.put(PRECEDING_SUCCEEDING_TITLE_TABLE, entity, precedingSucceedingTitleId, okapiHeaders, vertxContext,
      PutPrecedingSucceedingTitlesByPrecedingSucceedingTitleIdResponse.class, resultHandler);
  }

  @Validate
  @Override
  public void deletePrecedingSucceedingTitlesByPrecedingSucceedingTitleId(String precedingSucceedingTitleId,
                                                                          String lang, Map<String, String> okapiHeaders,
                                                                          Handler<AsyncResult<Response>> resultHandler,
                                                                          Context vertxContext) {
    PgUtil.deleteById(PRECEDING_SUCCEEDING_TITLE_TABLE, precedingSucceedingTitleId, okapiHeaders, vertxContext,
      DeletePrecedingSucceedingTitlesByPrecedingSucceedingTitleIdResponse.class, resultHandler);
  }

  @Validate
  @Override
  public void putPrecedingSucceedingTitlesInstancesByInstanceId(String instanceId, PrecedingSucceedingTitles entity,
                                                                Map<String, String> okapiHeaders,
                                                                Handler<AsyncResult<Response>> asyncResultHandler,
                                                                Context vertxContext) {
    var titles = entity.getPrecedingSucceedingTitles();
    boolean areValidTitles = validatePrecedingSucceedingTitles(titles, instanceId, asyncResultHandler);
    if (areValidTitles) {
      var cqlQuery = String.format("succeedingInstanceId==(%s) or precedingInstanceId==(%s)", instanceId, instanceId);
      PgUtil.delete(PRECEDING_SUCCEEDING_TITLE_TABLE, cqlQuery, okapiHeaders, vertxContext,
          PutPrecedingSucceedingTitlesInstancesByInstanceIdResponse.class)
        .compose(response -> saveCollection(titles, okapiHeaders, vertxContext))
        .onComplete(asyncResultHandler);
    }
  }

  private boolean validatePrecedingSucceedingTitles(List<PrecedingSucceedingTitle> precedingSucceedingTitles,
                                                    String instanceId,
                                                    Handler<AsyncResult<Response>> asyncResultHandler) {
    for (PrecedingSucceedingTitle precedingSucceedingTitle : precedingSucceedingTitles) {
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

  private boolean titleIsLinkedToInstanceId(PrecedingSucceedingTitle precedingSucceedingTitle, String instanceId) {
    return instanceId.equals(precedingSucceedingTitle.getPrecedingInstanceId())
      || instanceId.equals(precedingSucceedingTitle.getSucceedingInstanceId());
  }

  private Future<Response> saveCollection(List<PrecedingSucceedingTitle> entities, Map<String, String> okapiHeaders,
                                          Context vertxContext) {
    try {
      MetadataUtil.populateMetadata(entities, okapiHeaders);
      return postgresClient(vertxContext, okapiHeaders)
        .saveBatch(PRECEDING_SUCCEEDING_TITLE_TABLE, entities)
        .<Response>map(x -> PutPrecedingSucceedingTitlesInstancesByInstanceIdResponse.respond204())
        .otherwise(cause -> {
          var errorMessage = cause.getMessage();
          if (ValidationHelper.isFKViolation(errorMessage)) {
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

  private boolean precedingAndSucceedingInstanceEmpty(PrecedingSucceedingTitle entity) {
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
