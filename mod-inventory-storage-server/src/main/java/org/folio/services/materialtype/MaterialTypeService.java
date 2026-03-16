package org.folio.services.materialtype;

import static org.folio.rest.persist.PgUtil.deleteById;
import static org.folio.rest.persist.PgUtil.get;
import static org.folio.rest.persist.PgUtil.post;
import static org.folio.rest.persist.PgUtil.put;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.persist.MaterialTypeRepository;
import org.folio.rest.exceptions.NotFoundException;
import org.folio.rest.jaxrs.model.MaterialType;
import org.folio.rest.jaxrs.model.MaterialTypes;
import org.folio.rest.jaxrs.resource.MaterialTypes.DeleteMaterialTypesByMaterialtypeIdResponse;
import org.folio.rest.jaxrs.resource.MaterialTypes.GetMaterialTypesByMaterialtypeIdResponse;
import org.folio.rest.jaxrs.resource.MaterialTypes.GetMaterialTypesResponse;
import org.folio.rest.jaxrs.resource.MaterialTypes.PutMaterialTypesByMaterialtypeIdResponse;
import org.folio.rest.jaxrs.resource.SubjectTypes.PostSubjectTypesResponse;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.support.PostgresClientFactory;
import org.folio.services.domainevent.MaterialTypeDomainEventPublisher;

public class MaterialTypeService {
  public static final String MATERIAL_TYPE_TABLE = "material_type";

  private final Context context;
  private final Map<String, String> okapiHeaders;
  private final MaterialTypeRepository repository;
  private final MaterialTypeDomainEventPublisher domainEventService;

  public MaterialTypeService(Context context, Map<String, String> okapiHeaders) {
    this.context = context;
    this.okapiHeaders = okapiHeaders;
    this.repository = new MaterialTypeRepository(context, okapiHeaders);
    this.domainEventService = new MaterialTypeDomainEventPublisher(context, okapiHeaders);
  }

  public Future<Response> getByQuery(String cql, int offset, int limit) {
    return get(MATERIAL_TYPE_TABLE, MaterialType.class, MaterialTypes.class,
      cql, offset, limit, okapiHeaders, context, GetMaterialTypesResponse.class);
  }

  public Future<Response> getById(String id) {
    return PgUtil.getById(MATERIAL_TYPE_TABLE, MaterialType.class, id, okapiHeaders, context,
      GetMaterialTypesByMaterialtypeIdResponse.class);
  }

  public Future<Response> create(MaterialType materialType) {
    return createMaterialType(materialType);
  }

  public Future<Response> update(String id, MaterialType materialType) {
    if (materialType.getId() == null) {
      materialType.setId(id);
    }

    return repository.getById(id)
      .compose(oldMaterialType -> {
        if (oldMaterialType != null) {
          return put(MATERIAL_TYPE_TABLE, materialType, id, okapiHeaders, context,
            PutMaterialTypesByMaterialtypeIdResponse.class)
            .onSuccess(domainEventService.publishUpdated(oldMaterialType));
        }
        return Future.failedFuture(new NotFoundException("MaterialType was not found"));
      });
  }

  public Future<Response> delete(String id) {
    return repository.getById(id)
      .compose(oldSubjectType -> {
        if (oldSubjectType != null) {
          return deleteById(MATERIAL_TYPE_TABLE, id, okapiHeaders, context,
            DeleteMaterialTypesByMaterialtypeIdResponse.class)
            .onSuccess(domainEventService.publishRemoved(oldSubjectType));
        }
        return Future.failedFuture(new NotFoundException("MaterialType was not found"));
      });
  }

  public Future<Response> deleteAll() {
    return  PostgresClientFactory.getInstance(context, okapiHeaders)
      .delete(MATERIAL_TYPE_TABLE, new Criterion())
      .compose(notUsed -> domainEventService.publishAllRemoved())
      .map(notUsed -> Response.noContent().build());
  }

  private Future<Response> createMaterialType(MaterialType materialType) {
    return post(MATERIAL_TYPE_TABLE, materialType, okapiHeaders, context, PostSubjectTypesResponse.class)
      .onSuccess(domainEventService.publishCreated());
  }
}
