package org.folio.services.instance;

import static org.folio.rest.persist.PgUtil.postgresClient;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.persist.InstanceDateTypeRepository;
import org.folio.rest.jaxrs.model.InstanceDateType;
import org.folio.rest.jaxrs.model.InstanceDateTypePatch;
import org.folio.rest.jaxrs.model.InstanceDateTypes;
import org.folio.rest.jaxrs.resource.InstanceDateTypes.GetInstanceDateTypesResponse;
import org.folio.rest.jaxrs.resource.InstanceDateTypes.PatchInstanceDateTypesByIdResponse;
import org.folio.rest.persist.PgUtil;

public class InstanceDateTypeService {

  public static final String INSTANCE_DATE_TYPE_TABLE = "instance_date_type";

  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;
  private final InstanceDateTypeRepository repository;

  public InstanceDateTypeService(Context vertxContext, Map<String, String> okapiHeaders) {

    this.vertxContext = vertxContext;
    this.okapiHeaders = okapiHeaders;

    final var postgresClient = postgresClient(vertxContext, okapiHeaders);
    this.repository = new InstanceDateTypeRepository(postgresClient, INSTANCE_DATE_TYPE_TABLE, InstanceDateType.class);
  }

  public Future<Response> getInstanceDateTypes(String query, int offset, int limit) {
    return PgUtil.get(INSTANCE_DATE_TYPE_TABLE, InstanceDateType.class, InstanceDateTypes.class, query,
      "exact", offset, limit, okapiHeaders, vertxContext, GetInstanceDateTypesResponse.class);
  }

  public Future<Response> patchInstanceDateTypes(String id, InstanceDateTypePatch entity) {
    return repository.getById(id)
      .compose(instanceDateType -> PgUtil.put(INSTANCE_DATE_TYPE_TABLE, instanceDateType.withName(entity.getName()),
        id, okapiHeaders, vertxContext, PatchInstanceDateTypesByIdResponse.class));
  }
}
