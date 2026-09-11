package org.folio.support.messages;

import static org.folio.support.AwaitConfiguration.awaitAtMost;
import static org.folio.utility.RestUtility.TENANT_ID;

import io.vertx.core.json.JsonObject;
import java.net.URL;
import org.folio.support.kafka.FakeKafkaConsumer;
import org.folio.support.messages.matchers.EventMessageMatchers;

public class ServicePointEventMessageChecks {

  private final FakeKafkaConsumer kafkaConsumer;
  private final EventMessageMatchers eventMessageMatchers;

  public ServicePointEventMessageChecks(FakeKafkaConsumer kafkaConsumer, URL expectedUrl) {
    this.kafkaConsumer = kafkaConsumer;
    this.eventMessageMatchers = new EventMessageMatchers(TENANT_ID, expectedUrl);
  }

  public void updatedMessagePublished(JsonObject oldServicePoint, JsonObject newServicePoint) {
    final var servicePointId = oldServicePoint.getString("id");

    awaitAtMost().until(() -> kafkaConsumer.getMessagesForServicePoint(servicePointId),
      eventMessageMatchers.hasUpdateEventMessageFor(oldServicePoint, newServicePoint));
  }

  public void updatedMessageWasNotPublished(JsonObject oldServicePoint) {
    final var servicePointId = oldServicePoint.getString("id");

    awaitAtMost().until(() -> kafkaConsumer.getMessagesForServicePoint(servicePointId),
      eventMessageMatchers.hasNoUpdateEventMessage());
  }

  public void deletedMessagePublished(JsonObject servicePoint) {
    final var servicePointId = servicePoint.getString("id");

    awaitAtMost().until(() -> kafkaConsumer.getMessagesForServicePoint(servicePointId),
      eventMessageMatchers.hasDeleteEventMessageFor(servicePoint));
  }

  public void deletedMessageNotPublished(String servicePointId) {
    awaitAtMost().until(() -> kafkaConsumer.getMessagesForServicePoint(servicePointId),
      eventMessageMatchers.hasNoDeleteEventMessage());
  }
}
