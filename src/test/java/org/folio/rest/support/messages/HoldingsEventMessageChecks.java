package org.folio.rest.support.messages;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.rest.support.AwaitConfiguration.awaitAtMost;
import static org.folio.rest.support.AwaitConfiguration.awaitDuring;
import static org.folio.services.domainevent.CommonDomainEventPublisher.NULL_ID;
import static org.folio.utility.ModuleUtility.vertxUrl;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasProperty;

import io.vertx.core.json.JsonObject;
import java.net.MalformedURLException;
import java.net.URI;
import org.folio.rest.support.kafka.FakeKafkaConsumer;
import org.folio.rest.support.messages.matchers.EventMessageMatchers;

public class HoldingsEventMessageChecks {
  private final EventMessageMatchers eventMessageMatchers;

  private final FakeKafkaConsumer kafkaConsumer;

  public HoldingsEventMessageChecks(FakeKafkaConsumer kafkaConsumer) {
    this.kafkaConsumer = kafkaConsumer;
    this.eventMessageMatchers = new EventMessageMatchers(TENANT_ID, vertxUrl(""));
  }

  public HoldingsEventMessageChecks(FakeKafkaConsumer kafkaConsumer, String urlHeader) {
    this.kafkaConsumer = kafkaConsumer;
    try {
      this.eventMessageMatchers = new EventMessageMatchers(TENANT_ID, URI.create(urlHeader).toURL());
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  private static String getId(JsonObject holdings) {
    return holdings.getString("id");
  }

  public void createdMessagePublished(JsonObject holdings, String tenantIdExpected, String okapiUrlExpected) {
    final var holdingsId = getId(holdings);

    awaitAtMost().until(() -> kafkaConsumer.getMessagesForHoldings(holdingsId),
      eventMessageMatchers.hasCreateEventMessageFor(holdings, tenantIdExpected, okapiUrlExpected));
  }

  public void createdMessagePublished(String holdingsId) {
    awaitAtMost().until(() -> kafkaConsumer.getMessagesForHoldings(holdingsId),
      hasItem(hasProperty("type", is("CREATE"))));
  }

  public void updatedMessagePublished(JsonObject oldHoldings,
                                      JsonObject newHoldings) {

    final var holdingsId = getId(newHoldings);

    oldHoldings.remove("holdingsItems");
    oldHoldings.remove("bareHoldingsItems");
    newHoldings.remove("holdingsItems");
    newHoldings.remove("bareHoldingsItems");

    awaitAtMost().until(() -> kafkaConsumer.getMessagesForHoldings(holdingsId),
      eventMessageMatchers.hasUpdateEventMessageFor(oldHoldings, newHoldings));
  }

  public void updatedMessagePublished(JsonObject oldHoldings,
                                      JsonObject newHoldings,
                                      String okapiUrlExpected) {

    final var holdingsId = getId(newHoldings);

    oldHoldings.remove("holdingsItems");
    oldHoldings.remove("bareHoldingsItems");
    newHoldings.remove("holdingsItems");
    newHoldings.remove("bareHoldingsItems");

    awaitAtMost().until(() -> kafkaConsumer.getMessagesForHoldings(holdingsId),
      eventMessageMatchers.hasUpdateEventMessageFor(oldHoldings, newHoldings, okapiUrlExpected));
  }

  public void noHoldingsUpdatedMessagePublished(String holdingsId) {

    awaitDuring(1, SECONDS)
      .until(() -> kafkaConsumer.getMessagesForHoldings(holdingsId),
        eventMessageMatchers.hasNoUpdateEventMessage());
  }

  public void deletedMessagePublished(JsonObject holdings) {
    final var holdingsId = getId(holdings);

    awaitAtMost().until(() -> kafkaConsumer.getMessagesForHoldings(holdingsId),
      eventMessageMatchers.hasDeleteEventMessageFor(holdings));
  }

  public void allHoldingsDeletedMessagePublished() {
    awaitAtMost()
      .until(() -> kafkaConsumer.getMessagesForDeleteAllHoldings(NULL_ID, null),
        eventMessageMatchers.hasDeleteAllEventMessage());
  }
}
