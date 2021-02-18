package org.folio.services.domainevent;

import static org.folio.rest.tools.utils.TenantTool.tenantId;
import static org.folio.services.domainevent.DomainEventsHelper.getHeadersToForward;
import static org.folio.services.kafka.KafkaProducerServiceFactory.getKafkaProducerService;
import static org.folio.services.kafka.topic.KafkaTopic.SEARCH_RESOURCES;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Map;
import org.folio.services.kafka.KafkaMessage;

public class ReindexInstanceEventPublisher {
  private final Context vertxContext;
  private final Map<String, String> headersToForward;
  private final String tenantId;

  public ReindexInstanceEventPublisher(Context context, Map<String, String> okapiHeaders) {
    this.vertxContext = context;
    this.headersToForward = getHeadersToForward(okapiHeaders);
    this.tenantId = tenantId(okapiHeaders);
  }

  public Future<Void> publishReindexInstance(String instanceId) {
    return getKafkaProducerService(vertxContext.owner())
      .sendMessage(KafkaMessage.builder()
        .key(instanceId)
        .topic(SEARCH_RESOURCES)
        .payload(new ReindexInstanceEvent(instanceId, tenantId))
        .headers(headersToForward)
        .build());
  }
}
