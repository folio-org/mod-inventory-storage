package org.folio.services.instance;

import static org.folio.rest.impl.BoundWithPartApi.BOUND_WITH_TABLE;
import static org.folio.rest.persist.PgUtil.deleteById;
import static org.folio.rest.persist.PgUtil.post;
import static org.folio.rest.persist.PgUtil.put;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.persist.BoundWithRepository;
import org.folio.rest.jaxrs.model.BoundWithPart;
import org.folio.rest.jaxrs.resource.InventoryStorageBoundWithParts;
import org.folio.services.domainevent.BoundWithDomainEventPublisher;
import org.folio.validator.CommonValidators;

public class BoundWithPartService {

  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;
  private final BoundWithDomainEventPublisher domainEventPublisher;
  private final BoundWithRepository boundWithRepository;

  public BoundWithPartService(Context vertxContext, Map<String, String> okapiHeaders) {
    this.vertxContext = vertxContext;
    this.okapiHeaders = okapiHeaders;
    domainEventPublisher = new BoundWithDomainEventPublisher(vertxContext, okapiHeaders);
    boundWithRepository = new BoundWithRepository(vertxContext, okapiHeaders);
  }

  public Future<Response> create(BoundWithPart entity) {
    return post(BOUND_WITH_TABLE, entity, okapiHeaders, vertxContext,
      InventoryStorageBoundWithParts.PostInventoryStorageBoundWithPartsResponse.class)
      .onSuccess(domainEventPublisher.publishCreated());
  }

  public Future<Response> update(BoundWithPart entity, String id) {
    return put(BOUND_WITH_TABLE, entity, id, okapiHeaders, vertxContext,
      InventoryStorageBoundWithParts.PutInventoryStorageBoundWithPartsByIdResponse.class)
      .onSuccess(domainEventPublisher.publishUpdated(entity));
  }

  public Future<Response> delete(String id) {
    return boundWithRepository.getById(id)
      .compose(CommonValidators::refuseIfNotFound)
      .compose(item -> deleteById(BOUND_WITH_TABLE, id, okapiHeaders, vertxContext,
        InventoryStorageBoundWithParts.DeleteInventoryStorageBoundWithPartsByIdResponse.class)
        .onSuccess(domainEventPublisher.publishRemoved(item)));
  }
}
