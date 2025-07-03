package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.IdentifierType;
import org.folio.rest.jaxrs.model.IdentifierTypes;

public class IdentifierTypeApi extends BaseApi<IdentifierType, IdentifierTypes>
  implements org.folio.rest.jaxrs.resource.IdentifierTypes {

  public static final String IDENTIFIER_TYPE_TABLE = "identifier_type";

  @Validate
  @Override
  public void getIdentifierTypes(String query, String totalRecords, int offset, int limit,
                                 Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
                                 Context vertxContext) {
    getEntities(query, totalRecords, offset, limit, okapiHeaders, asyncResultHandler, vertxContext,
      GetIdentifierTypesResponse.class);
  }

  @Validate
  @Override
  public void postIdentifierTypes(IdentifierType entity, Map<String, String> okapiHeaders,
                                  Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    postEntity(entity, okapiHeaders, asyncResultHandler, vertxContext, PostIdentifierTypesResponse.class);
  }

  @Validate
  @Override
  public void getIdentifierTypesByIdentifierTypeId(String identifierTypeId,
                                                   Map<String, String> okapiHeaders,
                                                   Handler<AsyncResult<Response>> asyncResultHandler,
                                                   Context vertxContext) {
    getEntityById(identifierTypeId, okapiHeaders, asyncResultHandler, vertxContext,
      GetIdentifierTypesByIdentifierTypeIdResponse.class);
  }

  @Validate
  @Override
  public void deleteIdentifierTypesByIdentifierTypeId(String identifierTypeId,
                                                      Map<String, String> okapiHeaders,
                                                      Handler<AsyncResult<Response>> asyncResultHandler,
                                                      Context vertxContext) {
    deleteEntityById(identifierTypeId, okapiHeaders, asyncResultHandler, vertxContext,
      DeleteIdentifierTypesByIdentifierTypeIdResponse.class);
  }

  @Validate
  @Override
  public void putIdentifierTypesByIdentifierTypeId(String identifierTypeId, IdentifierType entity,
                                                   Map<String, String> okapiHeaders,
                                                   Handler<AsyncResult<Response>> asyncResultHandler,
                                                   Context vertxContext) {
    putEntityById(identifierTypeId, entity, okapiHeaders, asyncResultHandler, vertxContext,
      PutIdentifierTypesByIdentifierTypeIdResponse.class);
  }

  @Override
  protected String getReferenceTable() {
    return IDENTIFIER_TYPE_TABLE;
  }

  @Override
  protected Class<IdentifierType> getEntityClass() {
    return IdentifierType.class;
  }

  @Override
  protected Class<IdentifierTypes> getEntityCollectionClass() {
    return IdentifierTypes.class;
  }
}
