package org.folio.rest.support.messages;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.folio.rest.support.AwaitConfiguration.awaitAtMost;
import static org.folio.services.domainevent.CommonDomainEventPublisher.NULL_ID;
import static org.folio.utility.ModuleUtility.vertxUrl;
import static org.folio.utility.RestUtility.TENANT_ID;

import org.folio.rest.support.kafka.FakeKafkaConsumer;
import org.folio.rest.support.messages.matchers.EventMessageMatchers;
import org.hamcrest.Matcher;

import io.vertx.core.json.JsonObject;

public class AuthorityEventMessageChecks {
  private final EventMessageMatchers eventMessageMatchers
    = new EventMessageMatchers(TENANT_ID, vertxUrl(""));
  private final FakeKafkaConsumer kafkaConsumer;

  public AuthorityEventMessageChecks(FakeKafkaConsumer kafkaConsumer) {
    this.kafkaConsumer = kafkaConsumer;
  }

  public void createdMessagePublished(JsonObject authority) {
    final String authorityId = getId(authority);

    awaitAtMost().until(() -> kafkaConsumer.getMessagesForAuthority(authorityId),
      eventMessageMatchers.hasCreateEventMessageFor(authority));
  }

  public void updatedMessagePublished(JsonObject oldAuthority,
    JsonObject newAuthority) {

    final String authorityId = getId(oldAuthority);

    awaitAtMost().until(() -> kafkaConsumer.getMessagesForAuthority(authorityId),
      eventMessageMatchers.hasUpdateEventMessageFor(oldAuthority, newAuthority));
  }

  public void deletedMessagePublished(JsonObject authority) {
    final String authorityId = getId(authority);

    awaitAtMost().until(() -> kafkaConsumer.getMessagesForAuthority(authorityId),
      eventMessageMatchers.hasDeleteEventMessageFor(authority));
  }

  public void allAuthoritiesDeletedMessagePublished() {
    awaitAtMost().until(() -> kafkaConsumer.getMessagesForAuthority(NULL_ID),
      eventMessageMatchers.hasDeleteAllEventMessage());
  }

  public Integer countOfAllPublishedAuthoritiesIs(Matcher<Integer> matcher) {
    return await().atMost(10, SECONDS)
      .until(kafkaConsumer::getAllPublishedAuthoritiesCount, matcher);
  }

  private String getId(JsonObject json) {
    return json.getString("id");
  }
}
