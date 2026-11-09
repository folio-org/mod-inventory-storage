package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.ResponseHandlerUtil.firstErrorMessage;
import static org.folio.rest.support.ResponseHandlerUtil.handleFailure;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.MaterialType;
import org.folio.rest.jaxrs.resource.MaterialTypes;
import org.folio.services.materialtype.MaterialTypeService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("java:S6813")
public class MaterialTypeApi implements MaterialTypes {

  public static final String MATERIAL_TYPE_TABLE = "material_type";

  @Autowired
  private MaterialTypeService service;

  public MaterialTypeApi() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Validate
  @Override
  public void getMaterialTypes(String query, String totalRecords, int offset, int limit,
                               Map<String, String> headers, Handler<AsyncResult<Response>> asyncResultHandler,
                               Context vertxContext) {
    service.getByQuery(query, offset, limit, totalRecords, headers)
      .map(GetMaterialTypesResponse::respond200WithApplicationJson)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void postMaterialTypes(MaterialType entity, Map<String, String> headers,
                                Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    service.create(entity, headers)
      .map(created -> PostMaterialTypesResponse.respond201WithApplicationJson(created,
        PostMaterialTypesResponse.headersFor201().withLocation(created.getId())))
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void deleteMaterialTypes(Map<String, String> headers,
                                  Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    service.deleteAll(headers)
      .map(notUsed -> DeleteMaterialTypesResponse.respond204())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void getMaterialTypesByMaterialtypeId(String id,
                                               Map<String, String> headers,
                                               Handler<AsyncResult<Response>> asyncResultHandler,
                                               Context vertxContext) {
    service.getById(id, headers)
      .map(GetMaterialTypesByMaterialtypeIdResponse::respond200WithApplicationJson)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler, errors ->
        GetMaterialTypesByMaterialtypeIdResponse.respond404WithTextPlain(firstErrorMessage(errors))));
  }

  @Validate
  @Override
  public void deleteMaterialTypesByMaterialtypeId(String id,
                                                  Map<String, String> headers,
                                                  Handler<AsyncResult<Response>> asyncResultHandler,
                                                  Context vertxContext) {
    service.delete(id, headers)
      .map(notUsed -> DeleteMaterialTypesByMaterialtypeIdResponse.respond204())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler, errors ->
        DeleteMaterialTypesByMaterialtypeIdResponse.respond400WithTextPlain(firstErrorMessage(errors))));
  }

  @Validate
  @Override
  public void putMaterialTypesByMaterialtypeId(String id, MaterialType entity,
                                               Map<String, String> headers,
                                               Handler<AsyncResult<Response>> asyncResultHandler,
                                               Context vertxContext) {
    service.update(id, entity, headers)
      .map(notUsed -> PutMaterialTypesByMaterialtypeIdResponse.respond204())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler, errors ->
        PutMaterialTypesByMaterialtypeIdResponse.respond400WithTextPlain(firstErrorMessage(errors))));
  }
}
