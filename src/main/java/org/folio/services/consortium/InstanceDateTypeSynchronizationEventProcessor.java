package org.folio.services.consortium;

import static org.folio.okapi.common.XOkapiHeaders.TENANT;

import io.vertx.core.Future;
import java.util.HashMap;
import java.util.Map;
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
        future = future.eventually(() -> prepareHeaders(headers, memberTenant)
          .compose(newHeaders -> new InstanceDateTypeService(vertxContext, newHeaders)
            .putInstanceDateType(dateType.getId(), dateType))
          .onFailure(e -> LOG.warn("process:: propagate instance date type id={} to tenant='{}' failed",
            typeId, memberTenant, e))
        );
      }
      return future;
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
  }

  private Future<Map<String, String>> prepareHeaders(Map<String, String> headers, String memberTenant) {
    var map = new HashMap<>(headers);
    map.put(TENANT, memberTenant);
    return Future.succeededFuture(map);
  }
}
