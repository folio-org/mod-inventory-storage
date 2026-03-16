package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.EndpointFailureHandler.handleFailure;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.MaterialType;
import org.folio.services.materialtype.MaterialTypeService;

public class MaterialTypeApi implements org.folio.rest.jaxrs.resource.MaterialTypes {

  public static final String MATERIAL_TYPE_TABLE = "material_type";

  @Validate
  @Override
  public void getMaterialTypes(String query, String totalRecords, int offset, int limit,
                               Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
                               Context vertxContext) {
    new MaterialTypeService(vertxContext, okapiHeaders)
      .getByQuery(query, offset, limit)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void postMaterialTypes(MaterialType entity, Map<String, String> okapiHeaders,
                                Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    new MaterialTypeService(vertxContext, okapiHeaders)
      .create(entity)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void deleteMaterialTypes(Map<String, String> okapiHeaders,
                                  Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    new MaterialTypeService(vertxContext, okapiHeaders)
      .deleteAll()
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void getMaterialTypesByMaterialtypeId(String id,
                                               Map<String, String> okapiHeaders,
                                               Handler<AsyncResult<Response>> asyncResultHandler,
                                               Context vertxContext) {
    new MaterialTypeService(vertxContext, okapiHeaders)
      .getById(id)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void deleteMaterialTypesByMaterialtypeId(String id,
                                                  Map<String, String> okapiHeaders,
                                                  Handler<AsyncResult<Response>> asyncResultHandler,
                                                  Context vertxContext) {
    new MaterialTypeService(vertxContext, okapiHeaders)
      .delete(id)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void putMaterialTypesByMaterialtypeId(String id, MaterialType entity,
                                               Map<String, String> okapiHeaders,
                                               Handler<AsyncResult<Response>> asyncResultHandler,
                                               Context vertxContext) {
    new MaterialTypeService(vertxContext, okapiHeaders)
      .update(id, entity)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }
}
