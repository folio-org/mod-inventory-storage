package org.folio.services.reindex;

import static org.folio.InventoryKafkaTopic.REINDEX_FILE_READY;

import io.vertx.core.Future;
import io.vertx.kafka.client.producer.KafkaProducer;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.kafka.KafkaProducerManager;
import org.folio.kafka.services.KafkaProducerRecordBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Publishes a {@link ReindexFileReadyEvent} to the {@code reindex.file-ready} Kafka topic.
 * Intentionally bypasses the domain-event publisher infrastructure and sends directly
 * via a short-lived {@link KafkaProducer}.
 */
@Component
public class ReindexFileReadyEventPublisher {

  private static final Logger log = LogManager.getLogger(ReindexFileReadyEventPublisher.class);

  private final KafkaProducerManager producerManager;

  @Autowired
  public ReindexFileReadyEventPublisher(KafkaProducerManager producerManager) {
    this.producerManager = producerManager;
  }

  public Future<Void> publish(ReindexFileReadyEvent event, Map<String, String> headers) {
    var topic = REINDEX_FILE_READY.fullTopicName(event.getTenantId());
    var producerRecord = new KafkaProducerRecordBuilder<String, Object>(event.getTenantId())
      .key(event.getRangeId())
      .value(event)
      .topic(topic)
      .propagateOkapiHeaders(headers)
      .build();

    KafkaProducer<String, String> producer = producerManager.createShared(topic);

    return producer.send(producerRecord)
      .<Void>mapEmpty()
      .eventually(producer::flush)
      .eventually(producer::close)
      .onSuccess(v -> log.info("publish:: sent reindex.file-ready event traceId={} key={} topic={}",
        event.getTraceId(), event.getObjectKey(), topic))
      .onFailure(e -> log.error("publish:: failed to send reindex.file-ready event traceId={} topic={}",
        event.getTraceId(), topic, e));
  }
}
