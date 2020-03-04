package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.apache.commons.lang3.ObjectUtils;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.PrecedingSucceedingTitle;
import org.folio.rest.jaxrs.model.PrecedingSucceedingTitles;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.tools.utils.ValidationHelper;

import javax.ws.rs.core.Response;
import java.util.Map;

public class PrecedingSucceedingTitleAPI implements org.folio.rest.jaxrs.resource.PrecedingSucceedingTitles {

  private static final String PRECEDING_SUCCEEDING_TITLE_TABLE = "preceding_succeeding_title";

  @Validate
  @Override
  public void getPrecedingSucceedingTitles(int offset, int limit, String query, String lang,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(PRECEDING_SUCCEEDING_TITLE_TABLE, PrecedingSucceedingTitle.class, PrecedingSucceedingTitles.class, query, offset, limit,
      okapiHeaders, vertxContext, GetPrecedingSucceedingTitlesResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void postPrecedingSucceedingTitles(String lang, PrecedingSucceedingTitle entity,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

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
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(PRECEDING_SUCCEEDING_TITLE_TABLE, PrecedingSucceedingTitle.class, precedingSucceedingTitleId,
      okapiHeaders, vertxContext, GetPrecedingSucceedingTitlesByPrecedingSucceedingTitleIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void deletePrecedingSucceedingTitlesByPrecedingSucceedingTitleId(String precedingSucceedingTitleId,
    String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(PRECEDING_SUCCEEDING_TITLE_TABLE, precedingSucceedingTitleId, okapiHeaders, vertxContext,
      DeletePrecedingSucceedingTitlesByPrecedingSucceedingTitleIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void putPrecedingSucceedingTitlesByPrecedingSucceedingTitleId(String precedingSucceedingTitleId, String lang,
    PrecedingSucceedingTitle entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    if (precedingAndSucceedingInstanceEmpty(entity)) {
      handlePrecedingAndSucceedingEmptyError(asyncResultHandler);
      return;
    }
    PgUtil.put(PRECEDING_SUCCEEDING_TITLE_TABLE, entity, precedingSucceedingTitleId, okapiHeaders, vertxContext,
      PutPrecedingSucceedingTitlesByPrecedingSucceedingTitleIdResponse.class, asyncResultHandler);
  }

  private boolean precedingAndSucceedingInstanceEmpty(PrecedingSucceedingTitle entity) {
    return ObjectUtils.isEmpty(entity.getPrecedingInstanceId()) && ObjectUtils.isEmpty(entity.getSucceedingInstanceId());
  }

  private void handlePrecedingAndSucceedingEmptyError(Handler<AsyncResult<Response>> asyncResultHandler) {
    asyncResultHandler.handle(Future.succeededFuture(
      PostPrecedingSucceedingTitlesResponse.respond422WithApplicationJson(
        ValidationHelper.createValidationErrorMessage("", "", "The precedingInstanceId and succeedingInstanceId " +
          "can't be empty at the same time"))));
  }
}
