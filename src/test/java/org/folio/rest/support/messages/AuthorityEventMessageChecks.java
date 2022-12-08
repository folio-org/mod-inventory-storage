package org.folio.rest.support.messages;

import static org.folio.rest.support.AwaitConfiguration.awaitAtMost;
import static org.folio.utility.ModuleUtility.vertxUrl;
import static org.folio.utility.RestUtility.TENANT_ID;

import org.folio.rest.support.kafka.FakeKafkaConsumer;
import org.folio.rest.support.messages.matchers.EventMessageMatchers;

import io.vertx.core.json.JsonObject;

public class AuthorityEventMessageChecks {
  private static final EventMessageMatchers eventMessageMatchers
    = new EventMessageMatchers(TENANT_ID, vertxUrl(""));

  private AuthorityEventMessageChecks() { }

  public static void authorityCreatedMessagePublished(JsonObject authority) {
    final String authorityId = getId(authority);

    awaitAtMost().until(() -> FakeKafkaConsumer.getMessagesForAuthority(authorityId),
      eventMessageMatchers.hasCreateEventMessageFor(authority));
  }

  public static void authorityDeletedEventMessagePublished(JsonObject authority) {
    final String authorityId = authority.getString("id");

    awaitAtMost().until(() -> FakeKafkaConsumer.getMessagesForAuthority(authorityId),
      eventMessageMatchers.hasDeleteEventMessageFor(authority));
  }

  public static void authorityUpdatedEventPublished(JsonObject oldAuthority,
    JsonObject newAuthority) {

    final String authorityId = getId(oldAuthority);

    awaitAtMost().until(() -> FakeKafkaConsumer.getMessagesForAuthority(authorityId),
      eventMessageMatchers.hasUpdateEventMessageFor(oldAuthority, newAuthority));
  }

  private static String getId(JsonObject json) {
    return json.getString("id");
  }
}
