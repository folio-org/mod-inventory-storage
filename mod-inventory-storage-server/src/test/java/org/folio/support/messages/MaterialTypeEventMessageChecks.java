package org.folio.support.messages;

import static org.folio.support.AwaitConfiguration.awaitAtMost;
import static org.folio.utility.RestUtility.TENANT_ID;

import io.vertx.core.json.JsonObject;
import java.net.URL;
import org.folio.support.kafka.FakeKafkaConsumer;
import org.folio.support.messages.matchers.EventMessageMatchers;

public class MaterialTypeEventMessageChecks {

  private final FakeKafkaConsumer kafkaConsumer;
  private final EventMessageMatchers eventMessageMatchers;

  public MaterialTypeEventMessageChecks(FakeKafkaConsumer kafkaConsumer, URL expectedUrl) {
    this.kafkaConsumer = kafkaConsumer;
    this.eventMessageMatchers = new EventMessageMatchers(TENANT_ID, expectedUrl);
  }

  public void createdMessagePublished(JsonObject materialType) {
    final var materialTypeId = materialType.getString("id");
    awaitAtMost().until(() -> kafkaConsumer.getMessagesForMaterialType(materialTypeId),
      eventMessageMatchers.hasCreateEventMessageFor(materialType));
  }

  public void updatedMessagePublished(JsonObject oldMaterialType, JsonObject newMaterialType) {
    final var materialTypeId = oldMaterialType.getString("id");

    awaitAtMost().until(() -> kafkaConsumer.getMessagesForMaterialType(materialTypeId),
      eventMessageMatchers.hasUpdateEventMessageFor(oldMaterialType, newMaterialType));
  }

  public void deletedMessagePublished(JsonObject oldMaterialType) {
    final var materialTypeId = oldMaterialType.getString("id");

    awaitAtMost().until(() -> kafkaConsumer.getMessagesForMaterialType(materialTypeId),
      eventMessageMatchers.hasDeleteEventMessageFor(oldMaterialType));
  }
}
