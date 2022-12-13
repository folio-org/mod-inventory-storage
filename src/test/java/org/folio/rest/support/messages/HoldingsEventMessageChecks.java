package org.folio.rest.support.messages;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.rest.support.AwaitConfiguration.awaitAtMost;
import static org.folio.rest.support.AwaitConfiguration.awaitDuring;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getMessagesForHoldings;
import static org.folio.services.domainevent.CommonDomainEventPublisher.NULL_ID;
import static org.folio.utility.ModuleUtility.vertxUrl;
import static org.folio.utility.RestUtility.TENANT_ID;

import org.folio.rest.support.messages.matchers.EventMessageMatchers;

import io.vertx.core.json.JsonObject;

public class HoldingsEventMessageChecks {
  private static final EventMessageMatchers eventMessageMatchers
    = new EventMessageMatchers(TENANT_ID, vertxUrl(""));

  public static void holdingsCreatedMessagePublished(JsonObject holdings) {
    final var holdingsId = getId(holdings);
    final var instanceId = getInstanceId(holdings);

    awaitAtMost().until(() -> getMessagesForHoldings(instanceId, holdingsId),
      eventMessageMatchers.hasCreateEventMessageFor(holdings));
  }

  public static void holdingsUpdatedMessagePublished(JsonObject oldHoldings,
    JsonObject newHoldings) {

    final var holdingsId = getId(newHoldings);
    final var instanceId = getInstanceId(newHoldings);

    awaitAtMost().until(() -> getMessagesForHoldings(instanceId, holdingsId),
      eventMessageMatchers.hasUpdateEventMessageFor(oldHoldings, newHoldings));
  }

  public static void noHoldingsUpdatedMessagePublished(String instanceId,
    String holdingsId) {

    awaitDuring(1, SECONDS)
      .until(() -> getMessagesForHoldings(instanceId, holdingsId),
        eventMessageMatchers.hasNoUpdateEventMessage());
  }

  public static void holdingsDeletedMessagePublished(JsonObject holdings) {
    final var holdingsId = getId(holdings);
    final var instanceId = getInstanceId(holdings);

    awaitAtMost().until(() -> getMessagesForHoldings(instanceId, holdingsId),
      eventMessageMatchers.hasDeleteEventMessageFor(holdings));
  }

  public static void allHoldingsDeletedMessagePublished() {
    awaitAtMost()
      .until(() -> getMessagesForHoldings(NULL_ID, null),
        eventMessageMatchers.hasDeleteAllEventMessage());
  }

  private static String getId(JsonObject holdings) {
    return holdings.getString("id");
  }

  private static String getInstanceId(JsonObject holdings) {
    return holdings.getString("instanceId");
  }
}
