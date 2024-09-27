package org.folio.services.consortium.processor;

import static io.vertx.core.Future.succeededFuture;
import static java.lang.Boolean.FALSE;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.utils.ConsortiumUtils.isCentralTenant;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Servicepoint;
import org.folio.services.consortium.SynchronizationContext;
import org.folio.services.domainevent.DomainEvent;
import org.folio.services.domainevent.ServicePointEventType;
import org.folio.services.servicepoint.ServicePointService;
import org.folio.utils.Environment;

import io.vertx.core.Future;

public abstract class ServicePointSynchronizationEventProcessor {

  private static final Logger log = LogManager.getLogger(
    ServicePointSynchronizationEventProcessor.class);
  private static final String ECS_TLR_FEATURE_ENABLED = "ECS_TLR_FEATURE_ENABLED";
  private final ServicePointEventType servicePointEventType;
  protected final DomainEvent<Servicepoint> domainEvent;

  protected ServicePointSynchronizationEventProcessor(ServicePointEventType eventType,
    DomainEvent<Servicepoint> domainEvent) {
    this.servicePointEventType = eventType;
    this.domainEvent = domainEvent;
  }

  public Future<String> process(String eventKey, SynchronizationContext context) {
    var future = succeededFuture(eventKey);
    if (!isCentralTenant(domainEvent.getTenant(), context.consortiaData())
      || !isEcsTlrFeatureEnabled()
      || servicePointEventType.getPayloadType() != domainEvent.getType()) {
      return future;
    }
    if (!validateEventEntity()) {
      log.warn("process:: validation event entity failed");
      return future;
    }
    var vertxContext = context.vertx().getOrCreateContext();
    var headers = context.headers();
    for (String memberTenant : context.consortiaData().memberTenants()) {
      log.info("process:: tenant {} servicePointId {}", memberTenant, eventKey);
      future = future.eventually(() -> prepareHeaders(headers, memberTenant)
        .compose(lendingTenantHeader -> {
          var servicePointService = new ServicePointService(vertxContext, lendingTenantHeader);
          return processEvent(servicePointService, eventKey)
            .map(eventKey);
        })
        .onFailure(e ->
          log.warn("process:: tenant {} servicePointId {} failed", memberTenant, eventKey, e)));
    }
    return future;
  }

  protected abstract Future<?> processEvent(ServicePointService servicePointService,
    String servicePointId);

  protected abstract boolean validateEventEntity();

  private boolean isEcsTlrFeatureEnabled() {
    return Boolean.parseBoolean(Environment.getEnvVar(ECS_TLR_FEATURE_ENABLED, FALSE.toString()));
  }

  private Future<Map<String, String>> prepareHeaders(Map<String, String> headers,
    String memberTenant) {

    var map = new HashMap<>(headers);
    map.put(TENANT, memberTenant);
    return succeededFuture(map);
  }

}
