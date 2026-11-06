package org.folio.services.domainevent;

import static org.folio.rest.tools.utils.TenantTool.tenantId;

import java.util.Map;
import java.util.function.Function;
import org.folio.InventoryKafkaTopic;
import org.folio.kafka.KafkaProducerManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Creates the stateless {@link CommonDomainEventPublisher}s that the domain event publishers delegate to. They
 * share the producer managers and the failure handler owned by the Spring context.
 */
@Component
public class DomainEventPublisherFactory {

  public static final String REINDEX_PRODUCER_MANAGER = "reindexKafkaProducerManager";

  private final KafkaProducerManager producerManager;
  private final KafkaProducerManager reindexProducerManager;
  private final FailureHandler failureHandler;

  public DomainEventPublisherFactory(KafkaProducerManager producerManager,
                                     @Qualifier(REINDEX_PRODUCER_MANAGER) KafkaProducerManager reindexProducerManager,
                                     FailureHandler failureHandler) {
    this.producerManager = producerManager;
    this.reindexProducerManager = reindexProducerManager;
    this.failureHandler = failureHandler;
  }

  public <T> CommonDomainEventPublisher<T> publisher(InventoryKafkaTopic topic) {
    return publisher(headers -> topic.fullTopicName(tenantId(headers)));
  }

  /** Publisher for a topic whose name is resolved from the request's okapi headers. */
  public <T> CommonDomainEventPublisher<T> publisher(Function<Map<String, String>, String> topicResolver) {
    return new CommonDomainEventPublisher<>(producerManager, failureHandler, topicResolver);
  }

  /** Publisher whose producers accept the larger request size needed by reindex records. */
  public <T> CommonDomainEventPublisher<T> reindexPublisher(InventoryKafkaTopic topic) {
    return new CommonDomainEventPublisher<>(reindexProducerManager, failureHandler,
      headers -> topic.fullTopicName(tenantId(headers)));
  }
}
