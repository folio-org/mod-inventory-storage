package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.AlternativeTitleType;
import org.folio.rest.jaxrs.model.AlternativeTitleTypes;

public class AlternativeTitleTypeApi extends BaseApi<AlternativeTitleType, AlternativeTitleTypes>
  implements org.folio.rest.jaxrs.resource.AlternativeTitleTypes {

  public static final String REFERENCE_TABLE = "alternative_title_type";

  @Validate
  @Override
  public void getAlternativeTitleTypes(String query, String totalRecords, int offset, int limit,
                                       Map<String, String> okapiHeaders,
                                       Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    getEntities(query, totalRecords, offset, limit, okapiHeaders, asyncResultHandler, vertxContext,
      GetAlternativeTitleTypesResponse.class);
  }

  @Validate
  @Override
  public void postAlternativeTitleTypes(AlternativeTitleType entity, Map<String, String> okapiHeaders,
                                        Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    postEntity(entity, okapiHeaders, asyncResultHandler, vertxContext, PostAlternativeTitleTypesResponse.class);
  }

  @Validate
  @Override
  public void getAlternativeTitleTypesById(String id, Map<String, String> okapiHeaders,
                                           Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    getEntityById(id, okapiHeaders, asyncResultHandler, vertxContext, GetAlternativeTitleTypesByIdResponse.class);
  }

  @Validate
  @Override
  public void deleteAlternativeTitleTypesById(String id, Map<String, String> okapiHeaders,
                                              Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    deleteEntityById(id, okapiHeaders, asyncResultHandler, vertxContext, DeleteAlternativeTitleTypesByIdResponse.class);
  }

  @Validate
  @Override
  public void putAlternativeTitleTypesById(String id, AlternativeTitleType entity,
                                           Map<String, String> okapiHeaders,
                                           Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    putEntityById(id, entity, okapiHeaders, asyncResultHandler, vertxContext,
      PutAlternativeTitleTypesByIdResponse.class);
  }

  @Override
  protected String getReferenceTable() {
    return REFERENCE_TABLE;
  }

  @Override
  protected Class<AlternativeTitleType> getEntityClass() {
    return AlternativeTitleType.class;
  }

  @Override
  protected Class<AlternativeTitleTypes> getEntityCollectionClass() {
    return AlternativeTitleTypes.class;
  }
}
