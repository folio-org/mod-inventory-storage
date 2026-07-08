package org.folio.services.setting;

import static org.folio.okapi.common.XOkapiHeaders.TENANT;

import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.kafka.AsyncRecordHandler;
import org.folio.kafka.KafkaHeaderUtils;
import org.folio.services.caches.SettingCache;
import org.folio.services.domainevent.SettingEvent;

public class SettingUpdateKafkaHandler implements AsyncRecordHandler<String, String> {

  private static final Logger logger = LogManager.getLogger(SettingUpdateKafkaHandler.class);

  private final SettingCache cache;

  public SettingUpdateKafkaHandler(SettingCache cache) {
    this.cache = cache;
  }

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> kafkaRecord) {
    var topic = kafkaRecord.topic();
    logger.debug("handle:: Processing event for topic={}", topic);
    try {
      var headers = new CaseInsensitiveMap<>(KafkaHeaderUtils.kafkaHeadersToMap(kafkaRecord.headers()));
      var event = Json.decodeValue(kafkaRecord.value(), SettingEvent.class);
      var tenant = headers.get(TENANT);
      logger.debug("handle:: Processing setting event for tenantId={}, key={}, value={}", tenant, event.key(),
        event.value());

      cache.put(tenant + ":" + event.key(), CompletableFuture.completedFuture(event.value().toString()));
      return Future.succeededFuture(kafkaRecord.key());
    } catch (Exception e) {
      logger.error("Failed to process setting event for topic={}: {}", topic, e.getMessage(), e);
      return Future.failedFuture(e);
    }
  }
}
