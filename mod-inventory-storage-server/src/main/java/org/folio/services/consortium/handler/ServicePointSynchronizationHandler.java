package org.folio.services.consortium.handler;

import static io.vertx.core.Future.succeededFuture;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.Json;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import java.util.Optional;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.kafka.AsyncRecordHandler;
import org.folio.kafka.KafkaHeaderUtils;
import org.folio.rest.jaxrs.model.ServicePoint;
import org.folio.services.caches.ConsortiumData;
import org.folio.services.caches.ConsortiumDataCache;
import org.folio.services.consortium.SynchronizationContext;
import org.folio.services.consortium.processor.ServicePointSynchronizationEventProcessor;
import org.folio.services.domainevent.DomainEvent;

public abstract class ServicePointSynchronizationHandler
  implements AsyncRecordHandler<String, String> {

  private static final Logger log = LogManager.getLogger(
    ServicePointSynchronizationHandler.class);

  private final ConsortiumDataCache consortiumDataCache;
  private final HttpClient httpClient;
  private final Vertx vertx;

  protected ServicePointSynchronizationHandler(ConsortiumDataCache consortiumDataCache,
    HttpClient httpClient, Vertx vertx) {

    this.consortiumDataCache = consortiumDataCache;
    this.httpClient = httpClient;
    this.vertx = vertx;
  }

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> kafkaConsumerRecord) {
    log.info("handle:: Processing event {}", kafkaConsumerRecord::topic);
    var headers = new CaseInsensitiveMap<>(KafkaHeaderUtils.kafkaHeadersToMap(
      kafkaConsumerRecord.headers()));
    return consortiumDataCache.getConsortiumData(headers)
      .compose(consortiumData -> processConsortiumData(kafkaConsumerRecord, consortiumData,
        headers));
  }

  private Future<String> processConsortiumData(
    KafkaConsumerRecord<String, String> kafkaConsumerRecord,
    Optional<ConsortiumData> consortiumData, CaseInsensitiveMap<String, String> headers) {

    log.info("processConsortiumData:: {}", consortiumData);
    return consortiumData.map(data -> processConsortiumDataByEvent(data, kafkaConsumerRecord,
        headers)).orElseGet(() -> succeededFuture(kafkaConsumerRecord.key()));
  }

  private Future<String> processConsortiumDataByEvent(ConsortiumData data,
    KafkaConsumerRecord<String, String> kafkaConsumerRecord,
    CaseInsensitiveMap<String, String> headers) {

    var event = Json.decodeValue(kafkaConsumerRecord.value(), DomainEvent.class);
    var servicePointSynchronizationProcessor = getServicePointSynchronizationProcessor(event);
    return servicePointSynchronizationProcessor.process(kafkaConsumerRecord.key(),
      new SynchronizationContext(data, headers, vertx, httpClient));
  }

  protected abstract ServicePointSynchronizationEventProcessor getServicePointSynchronizationProcessor(
    DomainEvent<ServicePoint> domainEvent);
}
