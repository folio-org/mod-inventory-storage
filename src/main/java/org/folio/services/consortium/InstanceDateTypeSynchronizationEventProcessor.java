package org.folio.services.consortium;

import static org.folio.okapi.common.XOkapiHeaders.TENANT;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.InstanceDateType;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.domainevent.DomainEvent;
import org.folio.services.domainevent.DomainEventType;
import org.folio.services.instance.InstanceDateTypeService;
import org.folio.utils.ConsortiumUtils;

public class InstanceDateTypeSynchronizationEventProcessor implements SynchronizationEventProcessor<InstanceDateType> {

  private static final Logger LOG = LogManager.getLogger(InstanceDateTypeSynchronizationEventProcessor.class);

  @Override
  public Future<String> process(DomainEvent<?> event, String typeId, SynchronizationContext context) {
    LOG.debug("process:: Processing event, tenantId: '{}'", event.getTenant());
    if (!ConsortiumUtils.isCentralTenant(event.getTenant(), context.consortiaData())
        || event.getType() != DomainEventType.UPDATE) {
      return Future.succeededFuture(typeId);
    }
    try {
      var dateType = PostgresClient.pojo2JsonObject(event.getNewEntity()).mapTo(InstanceDateType.class);
      var vertxContext = context.vertx().getOrCreateContext();
      var headers = context.headers();
      var future = Future.succeededFuture(typeId);
      for (String memberTenant : context.consortiaData().memberTenants()) {
        LOG.info("process:: propagate instance date type id={} to tenant='{}'", typeId, memberTenant);
        vertxContext.putLocal("folio_tenantid", memberTenant);
        headers.put(TENANT, memberTenant);
        future = future.eventually(() -> new InstanceDateTypeService(vertxContext, headers)
          .putInstanceDateType(dateType.getId(), dateType)
        );
      }
      return future;
    } catch (JsonProcessingException e) {
      return Future.failedFuture(e);
    }
  }
}
