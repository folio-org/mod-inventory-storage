package org.folio.rest.support.messages;

import static org.folio.rest.support.AwaitConfiguration.awaitAtMost;
import static org.folio.utility.ModuleUtility.vertxUrl;
import static org.folio.utility.RestUtility.TENANT_ID;

import io.vertx.core.json.JsonObject;
import org.folio.rest.support.kafka.FakeKafkaConsumer;
import org.folio.rest.support.messages.matchers.EventMessageMatchers;

public class ServicePointEventMessageChecks {

  private final FakeKafkaConsumer kafkaConsumer;
  private final EventMessageMatchers eventMessageMatchers = new EventMessageMatchers(
    TENANT_ID, vertxUrl(""));

  public ServicePointEventMessageChecks(FakeKafkaConsumer kafkaConsumer) {
    this.kafkaConsumer = kafkaConsumer;
  }

  public void updatedMessagePublished(JsonObject oldServicePoint, JsonObject newServicePoint) {
    final var servicePointId = oldServicePoint.getString("id");

    awaitAtMost().until(() -> kafkaConsumer.getMessagesForServicePoint(servicePointId),
      eventMessageMatchers.hasUpdateEventMessageFor(oldServicePoint, newServicePoint));
  }

  public void updatedMessageWasNotPublished(JsonObject oldServicePoint, JsonObject newServicePoint) {
    final var servicePointId = oldServicePoint.getString("id");

    awaitAtMost().until(() -> kafkaConsumer.getMessagesForServicePoint(servicePointId),
      eventMessageMatchers.hasNoUpdateEventMessage());
  }
}
