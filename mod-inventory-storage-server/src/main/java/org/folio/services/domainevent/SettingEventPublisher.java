package org.folio.services.domainevent;

import static org.folio.InventoryKafkaTopic.SETTING;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.kafka.client.producer.KafkaProducer;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.KafkaProducerManager;
import org.folio.kafka.SimpleKafkaProducerManager;
import org.folio.kafka.services.KafkaEnvironmentProperties;
import org.folio.kafka.services.KafkaProducerRecordBuilder;
import org.folio.rest.tools.utils.TenantTool;

public class SettingEventPublisher {

  private static final Logger logger = LogManager.getLogger(SettingEventPublisher.class);

  private final KafkaProducerManager producerManager;

  public SettingEventPublisher(Context vertxContext) {
    this(createProducerManager(vertxContext));
  }

  SettingEventPublisher(KafkaProducerManager producerManager) {
    this.producerManager = producerManager;
  }

  public Future<Void> publish(SettingEvent event, String eventKey, Map<String, String> okapiHeaders) {
    var topic = SETTING.fullTopicName(event.tenantId());
    var producerRecord = new KafkaProducerRecordBuilder<String, Object>(TenantTool.tenantId(okapiHeaders))
      .key(eventKey)
      .value(event)
      .topic(topic)
      .propagateOkapiHeaders(okapiHeaders)
      .build();

    KafkaProducer<String, String> producer = producerManager.createShared(topic);

    return producer.send(producerRecord)
      .<Void>mapEmpty()
      .eventually(producer::flush)
      .eventually(producer::close)
      .onSuccess(v -> logger.info("publish:: sent setting event topic={}", topic))
      .onFailure(e -> logger.error("publish:: failed to send setting event topic={}", topic, e));
  }

  private static KafkaProducerManager createProducerManager(Context vertxContext) {
    var kafkaConfig = KafkaConfig.builder()
      .kafkaHost(KafkaEnvironmentProperties.host())
      .kafkaPort(KafkaEnvironmentProperties.port())
      .build();
    return new SimpleKafkaProducerManager(vertxContext.owner(), kafkaConfig);
  }
}
