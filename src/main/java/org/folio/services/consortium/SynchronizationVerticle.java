package org.folio.services.consortium;

import static org.folio.InventoryKafkaTopic.INSTANCE_DATE_TYPE;
import static org.folio.okapi.common.Config.getSysConfInteger;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.folio.InventoryKafkaTopic;
import org.folio.kafka.AsyncRecordHandler;
import org.folio.kafka.GlobalLoadSensor;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.KafkaConsumerWrapper;
import org.folio.kafka.SubscriptionDefinition;
import org.folio.kafka.services.KafkaEnvironmentProperties;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.services.caches.ConsortiumDataCache;

public class SynchronizationVerticle extends AbstractVerticle {

  private static final String TENANT_PATTERN = "\\w{1,}";

  private static final List<InventoryKafkaTopic> TOPICS = List.of(INSTANCE_DATE_TYPE);

  private static final Map<InventoryKafkaTopic, String> LOAD_LIMIT_PARAMS = Map.of(
    INSTANCE_DATE_TYPE, "consumer.instance-date-type-synchronization.load-limit"
  );

  private static final Map<InventoryKafkaTopic, Integer> DEFAULT_LOAD_LIMITS = Map.of(
    INSTANCE_DATE_TYPE, 10
  );

  private final List<KafkaConsumerWrapper<String, String>> consumerWrappers = new ArrayList<>();
  private final ConsortiumDataCache consortiumDataCache;

  public SynchronizationVerticle(ConsortiumDataCache consortiumDataCache) {
    this.consortiumDataCache = consortiumDataCache;
  }

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    var httpClient = vertx.createHttpClient();
    var handler = new SynchronizationAsyncRecordHandler(consortiumDataCache, httpClient, vertx);

    var futures = TOPICS.stream()
      .map(kafkaTopic -> createKafkaConsumerWrapper(kafkaTopic, handler))
      .collect(Collectors.toList());

    GenericCompositeFuture.all(futures)
      .onFailure(startPromise::fail)
      .onSuccess(ar -> {
        futures.forEach(future -> consumerWrappers.add(future.result()));
        startPromise.complete();
      });
  }

  @Override
  public void stop(Promise<Void> stopPromise) {
    List<Future<Void>> stopFutures = consumerWrappers.stream()
      .map(KafkaConsumerWrapper::stop)
      .collect(Collectors.toList());

    GenericCompositeFuture.join(stopFutures).onComplete(ar -> stopPromise.complete());
  }

  private Future<KafkaConsumerWrapper<String, String>> createKafkaConsumerWrapper(
    InventoryKafkaTopic topic, AsyncRecordHandler<String, String> recordHandler) {
    KafkaConfig kafkaConfig = getKafkaConfig();
    SubscriptionDefinition subscriptionDefinition = SubscriptionDefinition.builder()
      .eventType(topic.topicName())
      .subscriptionPattern(topic.fullTopicName(TENANT_PATTERN))
      .build();

    int loadLimit = getSysConfInteger(LOAD_LIMIT_PARAMS.get(topic), DEFAULT_LOAD_LIMITS.get(topic), new JsonObject());

    KafkaConsumerWrapper<String, String> consumerWrapper = KafkaConsumerWrapper.<String, String>builder()
      .context(context)
      .vertx(vertx)
      .kafkaConfig(kafkaConfig)
      .loadLimit(loadLimit)
      .globalLoadSensor(new GlobalLoadSensor())
      .subscriptionDefinition(subscriptionDefinition)
      .build();

    return consumerWrapper
      .start(recordHandler, topic.topicName() + "." + SynchronizationVerticle.class.getName())
      .map(consumerWrapper);
  }

  private KafkaConfig getKafkaConfig() {
    return KafkaConfig.builder()
      .envId(KafkaEnvironmentProperties.environment())
      .kafkaHost(KafkaEnvironmentProperties.host())
      .kafkaPort(KafkaEnvironmentProperties.port())
      .build();
  }

}
