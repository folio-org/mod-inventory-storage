package org.folio.services.domainevent;

import static org.folio.InventoryKafkaTopic.SETTING;

import io.vertx.core.Future;
import io.vertx.kafka.client.producer.KafkaProducer;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.kafka.KafkaProducerManager;
import org.folio.kafka.services.KafkaProducerRecordBuilder;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.services.domainevent.model.SettingEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SettingEventPublisher {

  private static final Logger logger = LogManager.getLogger(SettingEventPublisher.class);

  private final KafkaProducerManager producerManager;

  @Autowired
  public SettingEventPublisher(KafkaProducerManager producerManager) {
    this.producerManager = producerManager;
  }

  public Future<Void> publish(SettingEvent event, String eventKey, Map<String, String> headers) {
    var topic = SETTING.fullTopicName(event.tenantId());
    var producerRecord = new KafkaProducerRecordBuilder<String, Object>(TenantTool.tenantId(headers))
      .key(eventKey)
      .value(event)
      .topic(topic)
      .propagateOkapiHeaders(headers)
      .build();

    KafkaProducer<String, String> producer = producerManager.createShared(topic);

    return producer.send(producerRecord)
      .<Void>mapEmpty()
      .eventually(producer::flush)
      .eventually(producer::close)
      .onSuccess(v -> logger.info("publish:: sent setting event topic={}", topic))
      .onFailure(e -> logger.error("publish:: failed to send setting event topic={}", topic, e));
  }
}
