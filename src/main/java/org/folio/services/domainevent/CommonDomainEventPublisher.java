package org.folio.services.domainevent;

import static io.vertx.core.CompositeFuture.all;
import static io.vertx.core.Future.succeededFuture;
import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.rest.tools.utils.TenantTool.tenantId;
import static org.folio.services.domainevent.DomainEvent.createEvent;
import static org.folio.services.domainevent.DomainEvent.deleteAllEvent;
import static org.folio.services.domainevent.DomainEvent.deleteEvent;
import static org.folio.services.domainevent.DomainEvent.updateEvent;

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
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.Logger;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.KafkaProducerManager;
import org.folio.kafka.SimpleKafkaProducerManager;
import org.folio.kafka.services.KafkaEnvironmentProperties;
import org.folio.kafka.services.KafkaProducerRecordBuilder;

public class CommonDomainEventPublisher<T> {
  public static final String NULL_INSTANCE_ID = "00000000-0000-0000-0000-000000000000";
  private static final Logger log = getLogger(CommonDomainEventPublisher.class);

  private final Map<String, String> okapiHeaders;
  private final KafkaProducerManager producerManager;
  private final FailureHandler failureHandler;
  private final String kafkaTopic;

  CommonDomainEventPublisher(Map<String, String> okapiHeaders, String kafkaTopic,
    KafkaProducerManager kafkaProducerManager, FailureHandler failureHandler) {

    this.okapiHeaders = okapiHeaders;
    this.kafkaTopic = kafkaTopic;
    this.producerManager = kafkaProducerManager;
    this.failureHandler = failureHandler;
  }

  public CommonDomainEventPublisher(Context vertxContext, Map<String, String> okapiHeaders,
    String kafkaTopic) {

    this(okapiHeaders, kafkaTopic, createProducerManager(vertxContext),
      new LogToDbFailureHandler(vertxContext, okapiHeaders));
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
      .map(record -> publishRecordUpdated(record.getLeft(), record.getMiddle(), record.getRight()))
      .collect(Collectors.toList()))
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
      .map(record -> publishRecordCreated(record.getKey(), record.getValue()))
      .collect(Collectors.toList()))
      .map(notUsed -> null);
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
    return publish(NULL_INSTANCE_ID, deleteAllEvent(tenantId(okapiHeaders)));
  }

  public <R> Future<Long> publishStream(ReadStream<R> readStream,
    Function<R, KafkaProducerRecordBuilder<String, Object>> mapper,
    Function<Long, Future<?>> progressHandler) {

    var promise = Promise.<Long>promise();
    var kafkaProducer = getOrCreateProducer("stream_");
    var recordsProcessed = new AtomicLong(0);

    readStream.exceptionHandler(error -> {
      log.error("Unable to publish stream", error);
      promise.tryFail(error);
    }).endHandler(notUsed -> promise.tryComplete(recordsProcessed.get()))
      .handler(record -> {
        var producerRecord = mapper.apply(record)
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

  private Future<Void> publish(String key, Object value) {
    log.debug("Sending domain event [{}], payload [{}]", key, value);

    var producerRecord = new KafkaProducerRecordBuilder<String, Object>()
      .key(key).value(value).topic(kafkaTopic).propagateOkapiHeaders(okapiHeaders)
      .build();

    KafkaProducer<String, String> producer = getOrCreateProducer();

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

  private KafkaProducer<String, String> getOrCreateProducer() {
    return getOrCreateProducer("");
  }

  private KafkaProducer<String, String> getOrCreateProducer(String prefix) {
    return producerManager.createShared(prefix + kafkaTopic);
  }

  private static KafkaProducerManager createProducerManager(Context vertxContext) {
    var kafkaConfig = KafkaConfig.builder()
      .kafkaPort(KafkaEnvironmentProperties.port())
      .kafkaHost(KafkaEnvironmentProperties.host())
      .build();

    return new SimpleKafkaProducerManager(vertxContext.owner(), kafkaConfig);
  }
}
