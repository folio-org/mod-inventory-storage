package org.folio.support;

import io.vertx.core.Context;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.SimpleKafkaProducerManager;
import org.folio.kafka.services.KafkaEnvironmentProperties;
import org.folio.persist.NotificationSendingErrorRepository;
import org.folio.services.domainevent.DomainEventPublisherFactory;
import org.folio.services.domainevent.LogToDbFailureHandler;

/**
 * Builds a real {@link DomainEventPublisherFactory} for tests that publish to the test Kafka container.
 */
public final class DomainEventPublisherFactories {

  private DomainEventPublisherFactories() { }

  public static DomainEventPublisherFactory create(Context context) {
    var kafkaConfig = KafkaConfig.builder()
      .kafkaHost(KafkaEnvironmentProperties.host())
      .kafkaPort(KafkaEnvironmentProperties.port())
      .maxRequestSize(0)
      .build();
    var producerManager = new SimpleKafkaProducerManager(context.owner(), kafkaConfig);
    var failureHandler = new LogToDbFailureHandler(new NotificationSendingErrorRepository(context));

    return new DomainEventPublisherFactory(producerManager, producerManager, failureHandler);
  }
}
