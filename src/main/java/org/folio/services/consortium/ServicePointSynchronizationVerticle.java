package org.folio.services.consortium;

import static org.folio.rest.tools.utils.ModuleName.getModuleName;
import static org.folio.rest.tools.utils.ModuleName.getModuleVersion;
import static org.folio.services.domainevent.ServicePointEventType.SERVICE_POINT_CREATED;
import static org.folio.services.domainevent.ServicePointEventType.SERVICE_POINT_DELETED;
import static org.folio.services.domainevent.ServicePointEventType.SERVICE_POINT_UPDATED;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.kafka.AsyncRecordHandler;
import org.folio.kafka.GlobalLoadSensor;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.KafkaConsumerWrapper;
import org.folio.kafka.SubscriptionDefinition;
import org.folio.kafka.services.KafkaEnvironmentProperties;
import org.folio.kafka.services.KafkaTopic;
import org.folio.services.caches.ConsortiumDataCache;
import org.folio.services.consortium.handler.ServicePointSynchronizationCreateHandler;
import org.folio.services.consortium.handler.ServicePointSynchronizationDeleteHandler;
import org.folio.services.consortium.handler.ServicePointSynchronizationUpdateHandler;
import org.folio.services.domainevent.ServicePointEventType;

public class ServicePointSynchronizationVerticle extends AbstractVerticle {

  private static final Logger log = LogManager.getLogger(ServicePointSynchronizationVerticle.class);
  private static final String TENANT_PATTERN = "\\w{1,}";
  private static final String MODULE_ID = getModuleId();
  private static final int DEFAULT_LOAD_LIMIT = 5;
  private final ConsortiumDataCache consortiumDataCache;

  private final List<KafkaConsumerWrapper<String, String>> consumers = new ArrayList<>();

  public ServicePointSynchronizationVerticle(final ConsortiumDataCache consortiumDataCache) {
    this.consortiumDataCache = consortiumDataCache;
  }

  @Override
  public void start(Promise<Void> startPromise) {
    var httpClient = vertx.createHttpClient();

    createConsumers(httpClient)
      .onSuccess(v -> log.info("start:: verticle started"))
      .onFailure(t -> log.error("start:: verticle start failed", t))
      .onComplete(startPromise);
  }

  private Future<Void> createConsumers(HttpClient httpClient) {
    final var config = getKafkaConfig();

    return createEventConsumer(SERVICE_POINT_CREATED, config,
      new ServicePointSynchronizationCreateHandler(consortiumDataCache, httpClient, vertx))
      .compose(r -> createEventConsumer(SERVICE_POINT_UPDATED, config,
        new ServicePointSynchronizationUpdateHandler(consortiumDataCache, httpClient, vertx)))
      .compose(r -> createEventConsumer(SERVICE_POINT_DELETED, config,
        new ServicePointSynchronizationDeleteHandler(consortiumDataCache, httpClient, vertx)))
      .mapEmpty();
  }

  private Future<KafkaConsumerWrapper<String, String>> createEventConsumer(
    ServicePointEventType eventType, KafkaConfig kafkaConfig,
    AsyncRecordHandler<String, String> handler) {

    var subscriptionDefinition = SubscriptionDefinition.builder()
      .eventType(eventType.name())
      .subscriptionPattern(buildSubscriptionPattern(eventType.getKafkaTopic(), kafkaConfig))
      .build();

    return createConsumer(kafkaConfig, subscriptionDefinition, handler);
  }

  private Future<KafkaConsumerWrapper<String, String>> createConsumer(KafkaConfig kafkaConfig,
    SubscriptionDefinition subscriptionDefinition,
    AsyncRecordHandler<String, String> recordHandler) {

    var consumer = KafkaConsumerWrapper.<String, String>builder()
      .context(context)
      .vertx(vertx)
      .kafkaConfig(kafkaConfig)
      .loadLimit(DEFAULT_LOAD_LIMIT)
      .globalLoadSensor(new GlobalLoadSensor())
      .subscriptionDefinition(subscriptionDefinition)
      .build();

    return consumer.start(recordHandler, MODULE_ID)
      .onSuccess(v -> consumers.add(consumer))
      .map(consumer);
  }

  private static String buildSubscriptionPattern(KafkaTopic kafkaTopic, KafkaConfig kafkaConfig) {
    return kafkaTopic.fullTopicName(kafkaConfig, TENANT_PATTERN);
  }

  private static String getModuleId() {
    return getModuleName().replace("_", "-") + "-" + getModuleVersion();
  }

  private KafkaConfig getKafkaConfig() {
    return KafkaConfig.builder()
      .envId(KafkaEnvironmentProperties.environment())
      .kafkaHost(KafkaEnvironmentProperties.host())
      .kafkaPort(KafkaEnvironmentProperties.port())
      .build();
  }
}
