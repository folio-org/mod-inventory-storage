package org.folio.services.consortium;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.Json;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.folio.InventoryKafkaTopic;
import org.folio.kafka.AsyncRecordHandler;
import org.folio.kafka.KafkaHeaderUtils;
import org.folio.services.caches.ConsortiumDataCache;
import org.folio.services.domainevent.DomainEvent;

public class SynchronizationAsyncRecordHandler implements AsyncRecordHandler<String, String> {

  private static final Map<InventoryKafkaTopic, Supplier<SynchronizationEventProcessor<?>>> MAP = Map.of(
    InventoryKafkaTopic.INSTANCE, InstanceSynchronizationEventProcessor::new,
    InventoryKafkaTopic.INSTANCE_DATE_TYPE, InstanceDateTypeSynchronizationEventProcessor::new
  );

  private final ConsortiumDataCache consortiaDataCache;
  private final HttpClient httpClient;
  private final Vertx vertx;

  public SynchronizationAsyncRecordHandler(ConsortiumDataCache consortiaDataCache,
                                           HttpClient httpClient, Vertx vertx) {
    this.consortiaDataCache = consortiaDataCache;
    this.httpClient = httpClient;
    this.vertx = vertx;
  }

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> kafkaRecord) {
    var kafkaTopic = getKafkaTopic(kafkaRecord);

    var processor = Optional.ofNullable(MAP.get(kafkaTopic))
      .map(Supplier::get)
      .orElseThrow(() -> new IllegalStateException("Synchronization is unsupported. Topic:" + kafkaTopic.topicName()));

    var domainEvent = Json.decodeValue(kafkaRecord.value(), DomainEvent.class);
    var headers = new CaseInsensitiveMap<>(KafkaHeaderUtils.kafkaHeadersToMap(kafkaRecord.headers()));
    var synchronizationContext = new SynchronizationContext(consortiaDataCache, headers, vertx, httpClient);
    return processor.process(domainEvent, kafkaRecord.key(), synchronizationContext);
  }

  private InventoryKafkaTopic getKafkaTopic(KafkaConsumerRecord<String, String> kafkaConsumerRecord) {
    var fullTopic = kafkaConsumerRecord.topic();
    var topic = fullTopic.substring(fullTopic.lastIndexOf('.') + 1);
    return InventoryKafkaTopic.byTopic(topic);
  }
}
