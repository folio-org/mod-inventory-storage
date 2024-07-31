package org.folio.services.domainevent;

import static io.vertx.core.Future.all;
import static io.vertx.core.Future.succeededFuture;
import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.InventoryKafkaTopic.REINDEX_RECORDS;
import static org.folio.rest.tools.utils.TenantTool.tenantId;
import static org.folio.services.domainevent.DomainEvent.createEvent;
import static org.folio.services.domainevent.DomainEvent.deleteAllEvent;
import static org.folio.services.domainevent.DomainEvent.deleteEvent;
import static org.folio.services.domainevent.DomainEvent.updateEvent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Context;
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
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.KafkaProducerManager;
import org.folio.kafka.SimpleKafkaProducerManager;
import org.folio.kafka.services.KafkaEnvironmentProperties;
import org.folio.kafka.services.KafkaProducerRecordBuilder;
import org.folio.rest.jaxrs.model.PublishReindexRecords;
import org.folio.rest.tools.utils.TenantTool;

public class CommonDomainEventPublisher<T> {
  public static final String NULL_ID = "00000000-0000-0000-0000-000000000000";
  private static final Logger log = getLogger(CommonDomainEventPublisher.class);

  private final Map<String, String> okapiHeaders;
  private final KafkaProducerManager producerManager;
  private final FailureHandler failureHandler;
  private final String kafkaTopic;
  private final ObjectMapper objectMapper;

  CommonDomainEventPublisher(Map<String, String> okapiHeaders, String kafkaTopic,
                             KafkaProducerManager kafkaProducerManager, FailureHandler failureHandler) {

    this.okapiHeaders = okapiHeaders;
    this.kafkaTopic = kafkaTopic;
    this.producerManager = kafkaProducerManager;
    this.failureHandler = failureHandler;
    this.objectMapper = new ObjectMapper();
  }

  public CommonDomainEventPublisher(Context vertxContext, Map<String, String> okapiHeaders,
                                    String kafkaTopic) {

    this(okapiHeaders, kafkaTopic, createProducerManager(vertxContext),
      new LogToDbFailureHandler(vertxContext, okapiHeaders));
  }

  private static KafkaProducerManager createProducerManager(Context vertxContext) {
    var kafkaConfig = KafkaConfig.builder()
      .kafkaPort(KafkaEnvironmentProperties.port())
      .kafkaHost(KafkaEnvironmentProperties.host())
      .build();

    return new SimpleKafkaProducerManager(vertxContext.owner(), kafkaConfig);
  }

  public <R> Future<Long> publishStream(ReadStream<R> readStream,
                                        Function<R, KafkaProducerRecordBuilder<String, Object>> mapper,
                                        LongFunction<Future<?>> progressHandler) {

    var promise = Promise.<Long>promise();
    var kafkaProducer = getOrCreateProducer(kafkaTopic, "stream_");
    var recordsProcessed = new AtomicLong(0);

    readStream.exceptionHandler(error -> {
      log.error("Unable to publish stream", error);
      promise.tryFail(error);
    }).endHandler(notUsed -> promise.tryComplete(recordsProcessed.get())).handler(rec -> {
      var producerRecord = mapper.apply(rec)
        .topic(kafkaTopic).propagateOkapiHeaders(okapiHeaders).build();

      kafkaProducer.send(producerRecord)
        .onFailure(error -> {
          log.error("Unable to send event [{}]", producerRecord.value(), error);

          failureHandler.handleFailure(error, producerRecord);
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
    });

    return promise.future()
      .onSuccess(records -> log.info("Total records published from stream {}", records));
  }

  Future<Void> publishRecordUpdated(String instanceId, T oldRecord, T newRecord) {
    final DomainEvent<T> domainEvent = updateEvent(oldRecord, newRecord, tenantId(okapiHeaders));

    return publish(instanceId, domainEvent);
  }

  Future<Void> publishRecordsUpdated(Collection<Triple<String, T, T>> updatedRecords) {
    if (updatedRecords.isEmpty()) {
      return succeededFuture();
    }

    return all(updatedRecords.stream()
      .map(triple -> publishRecordUpdated(triple.getLeft(), triple.getMiddle(), triple.getRight()))
      .toList())
      .map(notUsed -> null);
  }

  Future<Void> publishRecordCreated(String instanceId, T newRecord) {
    final DomainEvent<T> domainEvent = createEvent(newRecord, tenantId(okapiHeaders));

    return publish(instanceId, domainEvent);
  }

  Future<Void> publishRecordsCreated(List<Pair<String, T>> records) {
    if (records.isEmpty()) {
      return succeededFuture();
    }

    return all(records.stream()
      .map(pair -> publishRecordCreated(pair.getKey(), pair.getValue()))
      .toList())
      .map(notUsed -> null);
  }

  public Future<Void> publishReindexRecords(String key,
                                            PublishReindexRecords.RecordType recordType,
                                            List<T> records) throws JsonProcessingException {
    if (records.isEmpty()) {
      return succeededFuture();
    }

    var payload = objectMapper
      .writerFor(new TypeReference<List<T>>() { }).writeValueAsString(records);
    var domainEvent = ReindexEventRaw.reindexEvent(tenantId(okapiHeaders), recordType, payload);
    return publish(reindexKafkaTopic(), key, domainEvent);
  }

  Future<Void> publishRecordRemoved(String instanceId, T oldEntity) {
    final DomainEvent<T> domainEvent = deleteEvent(oldEntity, tenantId(okapiHeaders));

    return publish(instanceId, domainEvent);
  }

  Future<Void> publishRecordRemoved(String instanceId, String oldEntity) {
    final DomainEventRaw domainEvent = DomainEventRaw.deleteEvent(oldEntity, tenantId(okapiHeaders));

    return publish(instanceId, domainEvent);
  }

  Future<Void> publishAllRecordsRemoved() {
    return publish(NULL_ID, deleteAllEvent(tenantId(okapiHeaders)));
  }

  private Future<Void> publish(String key, Object value) {
    return publish(kafkaTopic, key, value);
  }

  private Future<Void> publish(String topic, String key, Object value) {
    log.debug("Sending domain event [{}], payload [{}]", key, value);

    var producerRecord = new KafkaProducerRecordBuilder<String, Object>(TenantTool.tenantId(okapiHeaders))
      .key(key)
      .value(value)
      .topic(topic)
      .propagateOkapiHeaders(okapiHeaders)
      .build();

    KafkaProducer<String, String> producer = getOrCreateProducer(topic);

    return producer.send(producerRecord)
      .<Void>mapEmpty()
      .eventually(x -> producer.flush())
      .eventually(x -> producer.close())
      .onFailure(cause -> {
        log.error("Unable to send domain event [{}], payload - [{}]",
          key, value, cause);

        failureHandler.handleFailure(cause, producerRecord);
      });
  }

  private KafkaProducer<String, String> getOrCreateProducer(String topic) {
    return getOrCreateProducer(topic, "");
  }

  private KafkaProducer<String, String> getOrCreateProducer(String topic, String prefix) {
    return producerManager.createShared(prefix + topic);
  }

  private String reindexKafkaTopic() {
    return REINDEX_RECORDS.fullTopicName(tenantId(okapiHeaders));
  }
}
