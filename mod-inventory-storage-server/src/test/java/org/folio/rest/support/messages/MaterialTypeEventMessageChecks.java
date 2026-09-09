package org.folio.rest.support.messages;

import static org.folio.rest.support.AwaitConfiguration.awaitAtMost;
import static org.folio.utility.ModuleUtility.okapiUrl;
import static org.folio.utility.RestUtility.TENANT_ID;

import io.vertx.core.json.JsonObject;
import java.net.URL;
import org.folio.rest.support.kafka.FakeKafkaConsumer;
import org.folio.rest.support.messages.matchers.EventMessageMatchers;

public class MaterialTypeEventMessageChecks {

  private final FakeKafkaConsumer kafkaConsumer;
  private final EventMessageMatchers eventMessageMatchers;

  public MaterialTypeEventMessageChecks(FakeKafkaConsumer kafkaConsumer) {
    this(kafkaConsumer, okapiUrl());
  }

  /**
   * For callers whose module instance isn't reachable at the legacy {@code rest.api} stack's
   * own {@code okapiUrl()} (e.g. the shared {@code rest.impl} verticle, reachable via its own
   * WireMock URL) — events carry whichever URL was sent as the {@code X-Okapi-Url} header on
   * the originating request, so the expected value here must match that.
   */
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
