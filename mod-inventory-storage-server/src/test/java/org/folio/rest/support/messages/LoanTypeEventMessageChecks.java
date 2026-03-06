package org.folio.rest.support.messages;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.rest.support.AwaitConfiguration.awaitAtMost;
import static org.folio.utility.ModuleUtility.vertxUrl;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.empty;

import io.vertx.core.json.JsonObject;
import org.folio.rest.support.kafka.FakeKafkaConsumer;
import org.folio.rest.support.messages.matchers.EventMessageMatchers;

public class LoanTypeEventMessageChecks {

  private final FakeKafkaConsumer kafkaConsumer;
  private final EventMessageMatchers eventMessageMatchers = new EventMessageMatchers(
    TENANT_ID, vertxUrl(""));

  public LoanTypeEventMessageChecks(FakeKafkaConsumer kafkaConsumer) {
    this.kafkaConsumer = kafkaConsumer;
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
