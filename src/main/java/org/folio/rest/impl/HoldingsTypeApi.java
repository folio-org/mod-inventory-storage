package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.HoldingsType;
import org.folio.rest.jaxrs.model.HoldingsTypes;

public class HoldingsTypeApi extends BaseApi<HoldingsType, HoldingsTypes>
  implements org.folio.rest.jaxrs.resource.HoldingsTypes {

  public static final String REFERENCE_TABLE = "holdings_type";

  @Validate
  @Override
  public void getHoldingsTypes(String query, String totalRecords, int offset, int limit,
                               Map<String, String> okapiHeaders,
                               Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    getEntities(query, totalRecords, offset, limit, okapiHeaders, asyncResultHandler, vertxContext,
      GetHoldingsTypesResponse.class);
  }

  @Validate
  @Override
  public void postHoldingsTypes(HoldingsType entity, Map<String, String> okapiHeaders,
                                Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    postEntity(entity, okapiHeaders, asyncResultHandler, vertxContext, PostHoldingsTypesResponse.class);
  }

  @Validate
  @Override
  public void getHoldingsTypesById(String id, Map<String, String> okapiHeaders,
                                   Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    getEntityById(id, okapiHeaders, asyncResultHandler, vertxContext, GetHoldingsTypesByIdResponse.class);
  }

  @Validate
  @Override
  public void deleteHoldingsTypesById(String id, Map<String, String> okapiHeaders,
                                      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    deleteEntityById(id, okapiHeaders, asyncResultHandler, vertxContext, DeleteHoldingsTypesByIdResponse.class);
  }

  @Validate
  @Override
  public void putHoldingsTypesById(String id, HoldingsType entity, Map<String, String> okapiHeaders,
                                   Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    putEntityById(id, entity, okapiHeaders, asyncResultHandler, vertxContext, PutHoldingsTypesByIdResponse.class);
  }

  @Override
  protected String getReferenceTable() {
    return REFERENCE_TABLE;
  }

  @Override
  protected Class<HoldingsType> getEntityClass() {
    return HoldingsType.class;
  }

  @Override
  protected Class<HoldingsTypes> getEntityCollectionClass() {
    return HoldingsTypes.class;
  }
}
