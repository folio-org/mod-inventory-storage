package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Mtype;
import org.folio.rest.jaxrs.model.Mtypes;

public class MaterialTypeApi extends BaseApi<Mtype, Mtypes>
  implements org.folio.rest.jaxrs.resource.MaterialTypes {

  public static final String MATERIAL_TYPE_TABLE = "material_type";

  @Validate
  @Override
  public void getMaterialTypes(String query, String totalRecords, int offset, int limit,
                               Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
                               Context vertxContext) {
    getEntities(query, totalRecords, offset, limit, okapiHeaders, asyncResultHandler, vertxContext,
      GetMaterialTypesResponse.class);
  }

  @Validate
  @Override
  public void postMaterialTypes(Mtype entity, Map<String, String> okapiHeaders,
                                Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    postEntity(entity, okapiHeaders, asyncResultHandler, vertxContext, PostMaterialTypesResponse.class);
  }

  @Validate
  @Override
  public void deleteMaterialTypes(Map<String, String> okapiHeaders,
                                  Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    deleteEntities(okapiHeaders, asyncResultHandler, vertxContext);
  }

  @Validate
  @Override
  public void getMaterialTypesByMaterialtypeId(String id,
                                               Map<String, String> okapiHeaders,
                                               Handler<AsyncResult<Response>> asyncResultHandler,
                                               Context vertxContext) {
    getEntityById(id, okapiHeaders, asyncResultHandler, vertxContext, GetMaterialTypesByMaterialtypeIdResponse.class);
  }

  @Validate
  @Override
  public void deleteMaterialTypesByMaterialtypeId(String id,
                                                  Map<String, String> okapiHeaders,
                                                  Handler<AsyncResult<Response>> asyncResultHandler,
                                                  Context vertxContext) {
    deleteEntityById(id, okapiHeaders, asyncResultHandler, vertxContext,
      DeleteMaterialTypesByMaterialtypeIdResponse.class);
  }

  @Validate
  @Override
  public void putMaterialTypesByMaterialtypeId(String id, Mtype entity,
                                               Map<String, String> okapiHeaders,
                                               Handler<AsyncResult<Response>> asyncResultHandler,
                                               Context vertxContext) {
    putEntityById(id, entity, okapiHeaders, asyncResultHandler, vertxContext,
      PutMaterialTypesByMaterialtypeIdResponse.class);
  }

  @Override
  protected String getReferenceTable() {
    return MATERIAL_TYPE_TABLE;
  }

  @Override
  protected Class<Mtype> getEntityClass() {
    return Mtype.class;
  }

  @Override
  protected Class<Mtypes> getEntityCollectionClass() {
    return Mtypes.class;
  }
}
