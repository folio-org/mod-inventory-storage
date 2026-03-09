package org.folio.rest.support.messages;

import static org.folio.rest.support.AwaitConfiguration.awaitAtMost;
import static org.folio.utility.ModuleUtility.vertxUrl;
import static org.folio.utility.RestUtility.TENANT_ID;

import io.vertx.core.json.JsonObject;
import org.folio.rest.support.kafka.FakeKafkaConsumer;
import org.folio.rest.support.messages.matchers.EventMessageMatchers;

public class MaterialTypeEventMessageChecks {

  private final FakeKafkaConsumer kafkaConsumer;
  private final EventMessageMatchers eventMessageMatchers = new EventMessageMatchers(
    TENANT_ID, vertxUrl(""));

  public MaterialTypeEventMessageChecks(FakeKafkaConsumer kafkaConsumer) {
    this.kafkaConsumer = kafkaConsumer;
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
