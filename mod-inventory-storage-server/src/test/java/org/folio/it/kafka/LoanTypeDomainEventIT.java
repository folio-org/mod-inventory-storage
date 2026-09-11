package org.folio.it.kafka;

import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;

import io.vertx.core.json.JsonObject;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;
import org.folio.it.BaseIntegrationTest;
import org.folio.support.ResourcePaths;
import org.folio.support.messages.LoanTypeEventMessageChecks;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoanTypeDomainEventIT extends BaseIntegrationTest {

  private static final String NAME_FIELD = "name";
  private static final String ID_FIELD = "id";

  private final LoanTypeEventMessageChecks eventChecks = eventMessageChecks();

  @Test
  @DisplayName("should publish a Kafka event when a loan type is created")
  void shouldPublishCreatedEvent_whenLoanTypeIsCreated() {
    var createdLoanType = createLoanType();

    eventChecks.createdMessagePublished(createdLoanType);
  }

  @Test
  @DisplayName("should publish a Kafka event when a loan type is updated")
  void shouldPublishUpdatedEvent_whenLoanTypeIsUpdated() {
    var createdLoanType = createLoanType();
    var loanTypeId = createdLoanType.getString(ID_FIELD);

    var updateRequest = new JsonObject().put(ID_FIELD, loanTypeId).put(NAME_FIELD, uniqueName());
    var updateResponse = await(doPut(client, ResourcePaths.LOAN_TYPES + "/" + loanTypeId, updateRequest));
    assertThat(updateResponse.status()).isEqualTo(SC_NO_CONTENT);

    var updatedLoanType = await(doGet(client, ResourcePaths.LOAN_TYPES + "/" + loanTypeId)).jsonBody();
    eventChecks.updatedMessagePublished(createdLoanType, updatedLoanType);
  }

  @Test
  @DisplayName("should publish a Kafka event when a loan type is deleted")
  void shouldPublishDeletedEvent_whenLoanTypeIsDeleted() {
    var createdLoanType = createLoanType();
    var loanTypeId = createdLoanType.getString(ID_FIELD);

    var deleteResponse = await(doDelete(client, ResourcePaths.LOAN_TYPES + "/" + loanTypeId));
    assertThat(deleteResponse.status()).isEqualTo(SC_NO_CONTENT);

    eventChecks.deletedMessagePublished(createdLoanType);
  }

  @Test
  @DisplayName("should not publish an update event when a loan type update fails")
  void shouldNotPublishUpdatedEvent_whenLoanTypeUpdateFailed() {
    var missingLoanTypeId = UUID.randomUUID().toString();

    var updateResponse = await(doPut(client, ResourcePaths.LOAN_TYPES + "/" + missingLoanTypeId,
      new JsonObject().put(NAME_FIELD, uniqueName())));
    assertThat(updateResponse.status()).isEqualTo(SC_NOT_FOUND);

    eventChecks.noUpdatedMessagePublished(missingLoanTypeId);
  }

  @Test
  @DisplayName("should not publish a delete event when a loan type delete fails")
  void shouldNotPublishDeletedEvent_whenLoanTypeDeleteFailed() {
    var missingLoanTypeId = UUID.randomUUID().toString();

    var deleteResponse = await(doDelete(client, ResourcePaths.LOAN_TYPES + "/" + missingLoanTypeId));
    assertThat(deleteResponse.status()).isEqualTo(SC_NOT_FOUND);

    eventChecks.noDeletedMessagePublished(missingLoanTypeId);
  }

  @Test
  @DisplayName("should not publish any event when a loan type create fails")
  void shouldNotPublishAnyEvent_whenLoanTypeCreateFailed() {
    var loanTypeId = UUID.randomUUID().toString();
    var invalidRequest = new JsonObject()
      .put(ID_FIELD, loanTypeId)
      .put(NAME_FIELD, uniqueName())
      .put("additional", "invalid");

    var createResponse = await(doPost(client, ResourcePaths.LOAN_TYPES, invalidRequest));
    assertThat(createResponse.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);

    eventChecks.noMessagesPublished(loanTypeId);
  }

  private static JsonObject createLoanType() {
    var response = await(
        doPost(client, ResourcePaths.LOAN_TYPES, new JsonObject().put(NAME_FIELD, uniqueName())))
      .jsonBody();
    assertThat(response).isNotNull();
    return response;
  }

  private static String uniqueName() {
    return "loan-type-" + UUID.randomUUID();
  }

  private static LoanTypeEventMessageChecks eventMessageChecks() {
    try {
      return new LoanTypeEventMessageChecks(KAFKA_CONSUMER, new URL(wm.baseUrl()));
    } catch (MalformedURLException e) {
      throw new IllegalStateException(e);
    }
  }
}
