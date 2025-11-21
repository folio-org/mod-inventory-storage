package org.folio.services.instance;

import static io.vertx.core.Promise.promise;
import static org.folio.rest.persist.PgUtil.put;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.persist.InstanceDateTypeRepository;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.jaxrs.model.InstanceDateType;
import org.folio.rest.jaxrs.model.InstanceDateTypePatch;
import org.folio.rest.jaxrs.model.InstanceDateTypes;
import org.folio.rest.jaxrs.resource.InstanceDateTypes.GetInstanceDateTypesResponse;
import org.folio.rest.persist.PgUtil;
import org.folio.services.caches.ConsortiumDataCache;
import org.folio.services.domainevent.InstanceDateTypeDomainEventPublisher;
import org.folio.utils.ConsortiumUtils;

public class InstanceDateTypeService {

  public static final String INSTANCE_DATE_TYPE_TABLE = "instance_date_type";

  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;
  private final InstanceDateTypeRepository repository;
  private final InstanceDateTypeDomainEventPublisher eventPublisher;
  private final ConsortiumDataCache consortiumDataCache;

  public InstanceDateTypeService(Context vertxContext, Map<String, String> okapiHeaders) {
    this.vertxContext = vertxContext;
    this.okapiHeaders = okapiHeaders;

    this.repository = new InstanceDateTypeRepository(vertxContext, okapiHeaders);
    this.eventPublisher = new InstanceDateTypeDomainEventPublisher(vertxContext, okapiHeaders);
    this.consortiumDataCache = vertxContext.get(ConsortiumDataCache.class.getName());
  }

  public Future<Response> getInstanceDateTypes(String query, int offset, int limit) {
    return PgUtil.get(INSTANCE_DATE_TYPE_TABLE, InstanceDateType.class, InstanceDateTypes.class, query,
      "exact", offset, limit, okapiHeaders, vertxContext, GetInstanceDateTypesResponse.class);
  }

  public Future<Response> patchInstanceDateTypes(String id, InstanceDateTypePatch entity) {
    return consortiumDataCache.getConsortiumData(okapiHeaders)
      .compose(consortiumData -> {
        if (consortiumData.isEmpty() || ConsortiumUtils.isCentralTenant(okapiHeaders, consortiumData.get())) {
          return doUpdate(id, entity);
        } else {
          throw new BadRequestException("Action ‘UPDATE' is not supported for consortium member tenant.");
        }
      });
  }

  public Future<Response> putInstanceDateType(String id, InstanceDateType entity) {
    return repository.getById(id)
      .compose(oldDateType -> {
        final Promise<Response> putResult = promise();

        put(INSTANCE_DATE_TYPE_TABLE, entity, id, okapiHeaders, vertxContext,
          org.folio.rest.jaxrs.resource.InstanceDateTypes.PatchInstanceDateTypesByIdResponse.class, putResult::handle);

        return putResult.future()
          .onSuccess(eventPublisher.publishUpdated(oldDateType));
      });
  }

  private Future<Response> doUpdate(String id, InstanceDateTypePatch entity) {
    return repository.getById(id)
      .compose(oldDateType -> {
        final Promise<Response> putResult = promise();

        put(INSTANCE_DATE_TYPE_TABLE, oldDateType.withName(entity.getName()), id, okapiHeaders, vertxContext,
          org.folio.rest.jaxrs.resource.InstanceDateTypes.PatchInstanceDateTypesByIdResponse.class, putResult::handle);

        return putResult.future()
          .onSuccess(eventPublisher.publishUpdated(oldDateType));
      });
  }
}
