package org.folio.services.domainevent;

import static io.vertx.core.Future.all;
import static io.vertx.core.Future.succeededFuture;
import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.InventoryKafkaTopic.REINDEX_RECORDS;
import static org.folio.rest.tools.utils.TenantTool.tenantId;
import static org.folio.services.domainevent.model.DomainEvent.createEvent;
import static org.folio.services.domainevent.model.DomainEvent.deleteAllEvent;
import static org.folio.services.domainevent.model.DomainEvent.deleteEvent;
import static org.folio.services.domainevent.model.DomainEvent.updateEvent;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.streams.ReadStream;
import io.vertx.kafka.client.producer.KafkaProducer;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.LongFunction;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.Logger;
import org.folio.kafka.KafkaProducerManager;
import org.folio.kafka.services.KafkaProducerRecordBuilder;
import org.folio.rest.jaxrs.model.ReindexRecordsRequest;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.services.domainevent.model.DomainEvent;
import org.folio.services.domainevent.model.DomainEventRaw;
import org.folio.services.domainevent.model.ReindexRecordEvent;

public class CommonDomainEventPublisher<T> {
  public static final String NULL_ID = "00000000-0000-0000-0000-000000000000";
  private static final Logger log = getLogger(CommonDomainEventPublisher.class);

  private final KafkaProducerManager producerManager;
  private final FailureHandler failureHandler;
  private final Function<Map<String, String>, String> topicResolver;

  /**
   * Creates a publisher for the topic that {@code topicResolver} returns for the request's okapi headers.
   */
  public CommonDomainEventPublisher(KafkaProducerManager kafkaProducerManager, FailureHandler failureHandler,
                                    Function<Map<String, String>, String> topicResolver) {
    this.topicResolver = topicResolver;
    this.producerManager = kafkaProducerManager;
    this.failureHandler = failureHandler;
  }

  public <R> Future<Long> publishStream(ReadStream<R> readStream,
                                        Function<R, KafkaProducerRecordBuilder<String, Object>> mapper,
                                        LongFunction<Future<?>> progressHandler,
                                        Map<String, String> headers) {

    var promise = Promise.<Long>promise();
    var kafkaProducer = getOrCreateProducer(kafkaTopic(headers), "stream_");
    var recordsProcessed = new AtomicLong(0);

    readStream.exceptionHandler(error -> {
      log.error("Unable to publish stream", error);
      promise.tryFail(error);
    }).endHandler(notUsed -> promise.tryComplete(recordsProcessed.get()))
      .handler(rec -> handleStreamRecord(rec, mapper, progressHandler, kafkaProducer,
                                         readStream, promise, recordsProcessed, headers));

    return promise.future()
      .onSuccess(records -> log.info("Total records published from stream {}", records));
  }

  private <R> void handleStreamRecord(R rec, Function<R, KafkaProducerRecordBuilder<String, Object>> mapper,
                                       LongFunction<Future<?>> progressHandler,
                                       KafkaProducer<String, String> kafkaProducer,
                                       ReadStream<R> readStream, Promise<Long> promise,
                                       AtomicLong recordsProcessed, Map<String, String> headers) {
    var producerRecord = mapper.apply(rec)
      .topic(kafkaTopic(headers)).propagateOkapiHeaders(headers).build();

    kafkaProducer.send(producerRecord)
      .onFailure(error -> {
        log.error("Unable to send event [{}]", producerRecord.value(), error);
        failureHandler.handleFailure(error, producerRecord, headers);
        recordsProcessed.decrementAndGet();
      });

    progressHandler.apply(recordsProcessed.incrementAndGet())
      .onFailure(error -> {
        log.warn("Error occurred when progress tracked", error);
        readStream.pause();
        promise.tryFail(error);
      });

    if (kafkaProducer.writeQueueFull()) {
      log.info("Producer write queue full...");
      readStream.pause();
      kafkaProducer.drainHandler(notUsed -> {
        log.info("Producer write queue empty again...");
        readStream.resume();
      });
    }
  }

  Future<Void> publishRecordUpdated(String instanceId, T oldRecord, T newRecord, Map<String, String> headers) {
    final DomainEvent<T> domainEvent = updateEvent(oldRecord, newRecord, tenantId(headers));

    return publish(instanceId, domainEvent, headers);
  }

  Future<Void> publishRecordsUpdated(Collection<Triple<String, T, T>> updatedRecords,
                                     Map<String, String> headers) {
    if (updatedRecords.isEmpty()) {
      return succeededFuture();
    }

    return all(updatedRecords.stream()
      .map(triple -> publishRecordUpdated(triple.getLeft(), triple.getMiddle(), triple.getRight(), headers))
      .toList())
      .map(notUsed -> null);
  }

  Future<Void> publishRecordCreated(String instanceId, T newRecord, Map<String, String> headers) {
    final DomainEvent<T> domainEvent = createEvent(newRecord, tenantId(headers));

    return publish(instanceId, domainEvent, headers);
  }

  Future<Void> publishRecordsCreated(List<Pair<String, T>> records, Map<String, String> headers) {
    if (records.isEmpty()) {
      return succeededFuture();
    }

    return all(records.stream()
      .map(pair -> publishRecordCreated(pair.getKey(), pair.getValue(), headers))
      .toList())
      .map(notUsed -> null);
  }

  public Future<Void> publishReindexRecords(String key,
                                            ReindexRecordsRequest.RecordType recordType,
                                            List<T> records, Map<String, String> headers) {
    var domainEvent = ReindexRecordEvent.reindexEvent(tenantId(headers), recordType, records);
    return publish(reindexKafkaTopic(headers), key, domainEvent, headers);
  }

  Future<Void> publishRecordRemoved(String instanceId, T oldEntity, Map<String, String> headers) {
    final DomainEvent<T> domainEvent = deleteEvent(oldEntity, tenantId(headers));

    return publish(instanceId, domainEvent, headers);
  }

  Future<Void> publishRecordRemoved(String instanceId, String oldEntity, Map<String, String> headers) {
    final DomainEventRaw domainEvent = DomainEventRaw.deleteEvent(oldEntity, tenantId(headers));

    return publish(instanceId, domainEvent, headers);
  }

  Future<Void> publishAllRecordsRemoved(Map<String, String> headers) {
    return publish(NULL_ID, deleteAllEvent(tenantId(headers)), headers);
  }

  private Future<Void> publish(String key, Object value, Map<String, String> headers) {
    return publish(kafkaTopic(headers), key, value, headers);
  }

  private Future<Void> publish(String topic, String key, Object value, Map<String, String> headers) {
    log.debug("Sending domain event [{}], payload [{}]", key, value);

    var producerRecord = new KafkaProducerRecordBuilder<String, Object>(TenantTool.tenantId(headers))
      .key(key)
      .value(value)
      .topic(topic)
      .propagateOkapiHeaders(headers)
      .build();

    KafkaProducer<String, String> producer = getOrCreateProducer(topic);

    return producer.send(producerRecord)
      .<Void>mapEmpty()
      .eventually(producer::flush)
      .eventually(producer::close)
      .onFailure(cause -> {
        log.error("Unable to send domain event [{}], payload - [{}]",
          key, value, cause);

        failureHandler.handleFailure(cause, producerRecord, headers);
      });
  }

  private KafkaProducer<String, String> getOrCreateProducer(String topic) {
    return getOrCreateProducer(topic, "");
  }

  private KafkaProducer<String, String> getOrCreateProducer(String topic, String prefix) {
    return producerManager.createShared(prefix + topic);
  }

  private String kafkaTopic(Map<String, String> headers) {
    return topicResolver.apply(headers);
  }

  private String reindexKafkaTopic(Map<String, String> headers) {
    return REINDEX_RECORDS.fullTopicName(tenantId(headers));
  }
}
