package org.folio.support.messages;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.support.AwaitConfiguration.awaitAtMost;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.empty;

import io.vertx.core.json.JsonObject;
import java.net.URL;
import org.folio.support.kafka.FakeKafkaConsumer;
import org.folio.support.messages.matchers.EventMessageMatchers;

public class LoanTypeEventMessageChecks {

  private final FakeKafkaConsumer kafkaConsumer;
  private final EventMessageMatchers eventMessageMatchers;

  public LoanTypeEventMessageChecks(FakeKafkaConsumer kafkaConsumer, URL expectedUrl) {
    this.kafkaConsumer = kafkaConsumer;
    this.eventMessageMatchers = new EventMessageMatchers(TENANT_ID, expectedUrl);
  }

  public void noMessagesPublished(String loanTypeId) {
    awaitAtMost().during(1, SECONDS).until(() -> kafkaConsumer.getMessagesForLoanType(loanTypeId), is(empty()));
  }

  public void createdMessagePublished(JsonObject loanType) {
    final var loanTypeId = loanType.getString("id");
    awaitAtMost().until(() -> kafkaConsumer.getMessagesForLoanType(loanTypeId),
      eventMessageMatchers.hasCreateEventMessageFor(loanType));
  }

  public void updatedMessagePublished(JsonObject oldLoanType, JsonObject newLoanType) {
    final var loanTypeId = oldLoanType.getString("id");
    awaitAtMost().until(() -> kafkaConsumer.getMessagesForLoanType(loanTypeId),
      eventMessageMatchers.hasUpdateEventMessageFor(oldLoanType, newLoanType));
  }

  public void noUpdatedMessagePublished(String loanTypeId) {
    awaitAtMost().during(1, SECONDS).until(() -> kafkaConsumer.getMessagesForLoanType(loanTypeId),
      eventMessageMatchers.hasNoUpdateEventMessage());
  }

  public void deletedMessagePublished(JsonObject loanType) {
    final var loanTypeId = loanType.getString("id");
    awaitAtMost().until(() -> kafkaConsumer.getMessagesForLoanType(loanTypeId),
      eventMessageMatchers.hasDeleteEventMessageFor(loanType));
  }

  public void noDeletedMessagePublished(String loanTypeId) {
    awaitAtMost().during(1, SECONDS).until(() -> kafkaConsumer.getMessagesForLoanType(loanTypeId),
      eventMessageMatchers.hasNoDeleteEventMessage());
  }
}
