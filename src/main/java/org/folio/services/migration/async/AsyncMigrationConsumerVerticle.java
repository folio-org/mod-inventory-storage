package org.folio.services.migration.async;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import org.folio.services.kafka.topic.KafkaTopic;

import java.util.HashMap;

import static org.folio.Environment.environmentName;
import static org.folio.services.kafka.KafkaProperties.getKafkaConsumerProperties;

public class AsyncMigrationConsumerVerticle extends AbstractVerticle {
  private static final String TENANT_HEADER = "x-okapi-tenant";

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    JsonObject config = vertx.getOrCreateContext().config();
    var tenantId = config.getString(TENANT_HEADER);
    var topicName = KafkaTopic.asyncMigration(tenantId, environmentName()).getTopicName();
    var headers = new HashMap<String, String>();
    headers.put(TENANT_HEADER, tenantId);

    KafkaConsumer<String, JsonObject> consumer = KafkaConsumer
      .<String, JsonObject>create(vertx, getKafkaConsumerProperties(tenantId,
        topicName + "_group"))
      .subscribe(topicName, startPromise);
    vertx.setPeriodic(800L, v ->
      new AsyncMigrationUtils().pollAsyncMigrationsMessages(consumer, headers, vertx.getOrCreateContext()));
  }
}
