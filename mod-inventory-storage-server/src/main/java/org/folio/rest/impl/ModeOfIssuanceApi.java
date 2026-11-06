package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.IssuanceMode;
import org.folio.rest.jaxrs.model.IssuanceModes;
import org.folio.rest.jaxrs.resource.ModesOfIssuance;

public class ModeOfIssuanceApi extends BaseApi<IssuanceMode, IssuanceModes> implements ModesOfIssuance {

  public static final String MODE_OF_ISSUANCE_TABLE = "mode_of_issuance";

  @Validate
  @Override
  public void getModesOfIssuance(String query, String totalRecords, int offset, int limit,
                                 Map<String, String> headers, Handler<AsyncResult<Response>> asyncResultHandler,
                                 Context vertxContext) {
    getEntities(query, totalRecords, offset, limit, headers, asyncResultHandler);
  }

  @Validate
  @Override
  public void postModesOfIssuance(IssuanceMode entity, Map<String, String> headers,
                                  Handler<AsyncResult<Response>> asyncResultHandler,
                                  Context vertxContext) {
    postEntity(entity, headers, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteModesOfIssuance(Map<String, String> headers,
                                    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    deleteEntities(headers, asyncResultHandler);
  }

  @Validate
  @Override
  public void getModesOfIssuanceByModeOfIssuanceId(String modeOfIssuanceId,
                                                   Map<String, String> headers,
                                                   Handler<AsyncResult<Response>> asyncResultHandler,
                                                   Context vertxContext) {
    getEntityById(modeOfIssuanceId, headers, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteModesOfIssuanceByModeOfIssuanceId(String modeOfIssuanceId,
                                                      Map<String, String> headers,
                                                      Handler<AsyncResult<Response>> asyncResultHandler,
                                                      Context vertxContext) {
    deleteEntityById(modeOfIssuanceId, headers, asyncResultHandler);
  }

  @Validate
  @Override
  public void putModesOfIssuanceByModeOfIssuanceId(String modeOfIssuanceId, IssuanceMode entity,
                                                   Map<String, String> headers,
                                                   Handler<AsyncResult<Response>> asyncResultHandler,
                                                   Context vertxContext) {
    putEntityById(modeOfIssuanceId, entity, headers, asyncResultHandler);
  }

  @Override
  protected String getReferenceTable() {
    return MODE_OF_ISSUANCE_TABLE;
  }

  @Override
  protected Class<IssuanceMode> getEntityClass() {
    return IssuanceMode.class;
  }

  @Override
  protected Class<IssuanceModes> getEntityCollectionClass() {
    return IssuanceModes.class;
  }
}
