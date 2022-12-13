package org.folio.rest.support.messages;

import static org.folio.rest.support.AwaitConfiguration.awaitAtMost;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getMessagesForHoldings;
import static org.folio.utility.ModuleUtility.vertxUrl;
import static org.folio.utility.RestUtility.TENANT_ID;

import org.folio.rest.support.messages.matchers.EventMessageMatchers;

import io.vertx.core.json.JsonObject;

public class HoldingsEventMessageChecks {
  private static final EventMessageMatchers eventMessageMatchers
    = new EventMessageMatchers(TENANT_ID, vertxUrl(""));

  public static void holdingsCreatedMessagePublished(JsonObject holdings) {
    final var holdingsId = getId(holdings);
    final var instanceId = holdings.getString("instanceId");

    awaitAtMost().until(() -> getMessagesForHoldings(instanceId, holdingsId),
      eventMessageMatchers.hasCreateEventMessageFor(holdings));
  }

  private static String getId(JsonObject json) {
    return json.getString("id");
  }
}
