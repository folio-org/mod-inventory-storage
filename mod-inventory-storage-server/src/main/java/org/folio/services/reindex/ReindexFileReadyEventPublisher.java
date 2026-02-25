package org.folio.services.reindex;

import static org.folio.InventoryKafkaTopic.REINDEX_FILE_READY;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.kafka.client.producer.KafkaProducer;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.SimpleKafkaProducerManager;
import org.folio.kafka.services.KafkaEnvironmentProperties;
import org.folio.kafka.services.KafkaProducerRecordBuilder;

/**
 * Publishes a {@link ReindexFileReadyEvent} to the {@code reindex-file-ready} Kafka topic.
 * Intentionally bypasses the domain-event publisher infrastructure and sends directly
 * via a short-lived {@link KafkaProducer}.
 */
public class ReindexFileReadyEventPublisher {

  private static final Logger log = LogManager.getLogger(ReindexFileReadyEventPublisher.class);

  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;

  public ReindexFileReadyEventPublisher(Context vertxContext, Map<String, String> okapiHeaders) {
    this.vertxContext = vertxContext;
    this.okapiHeaders = okapiHeaders;
  }

  public Future<Void> publish(ReindexFileReadyEvent event) {
    var topic = REINDEX_FILE_READY.fullTopicName(event.getTenantId());
    var producerRecord = new KafkaProducerRecordBuilder<String, Object>(event.getTenantId())
      .key(event.getRangeId())
      .value(event)
      .topic(topic)
      .propagateOkapiHeaders(okapiHeaders)
      .build();

    var kafkaConfig = KafkaConfig.builder()
      .kafkaHost(KafkaEnvironmentProperties.host())
      .kafkaPort(KafkaEnvironmentProperties.port())
      .build();

    KafkaProducer<String, String> producer =
      new SimpleKafkaProducerManager(vertxContext.owner(), kafkaConfig).createShared(topic);

    return producer.send(producerRecord)
      .<Void>mapEmpty()
      .eventually(producer::flush)
      .eventually(producer::close)
      .onSuccess(v -> log.info("publish:: sent reindex-file-ready event jobId={} key={} topic={}",
        event.getJobId(), event.getObjectKey(), topic))
      .onFailure(e -> log.error("publish:: failed to send reindex-file-ready event jobId={} topic={}",
        event.getJobId(), topic, e));
  }
}
