package org.folio.services.consortium.handler;

import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.kafka.AsyncRecordHandler;
import org.folio.kafka.KafkaHeaderUtils;
import org.folio.rest.jaxrs.model.Servicepoint;
import org.folio.services.caches.ConsortiumDataCache;
import org.folio.services.consortium.SynchronizationContext;
import org.folio.services.consortium.processor.ServicePointSynchronizationEventProcessor;
import org.folio.services.domainevent.DomainEvent;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.Json;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

public abstract class ServicePointSynchronizationHandler
  implements AsyncRecordHandler<String, String> {
  private static final Logger LOG = LogManager.getLogger(
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
    LOG.info("handle:: Processing event={}", kafkaConsumerRecord.topic());
    var event = Json.decodeValue(kafkaConsumerRecord.value(), DomainEvent.class);
    var headers = new CaseInsensitiveMap<>(
      KafkaHeaderUtils.kafkaHeadersToMap(kafkaConsumerRecord.headers()));
    String servicePointId = kafkaConsumerRecord.key();

    return consortiumDataCache.getConsortiumData(headers)
      .compose(consortiumData -> {
        LOG.info("consortiumData:: {}", consortiumData);
        if (consortiumData.isPresent()) {
          var servicePointSynchronizationProcessor = getServicePointSynchronizationProcessor(event);
          return servicePointSynchronizationProcessor.process(servicePointId,
            new SynchronizationContext(consortiumData.get(), headers, vertx, httpClient));
        }
        return Future.succeededFuture(kafkaConsumerRecord.key());
      });
  }

  protected abstract ServicePointSynchronizationEventProcessor getServicePointSynchronizationProcessor(
    DomainEvent<Servicepoint> domainEvent);

}
