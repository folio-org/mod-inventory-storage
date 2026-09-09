package org.folio.rest.impl;

import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.core.json.JsonObject;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;
import org.folio.rest.support.messages.LoanTypeEventMessageChecks;
import org.junit.jupiter.api.Test;

class LoanTypeDomainEventIT extends BaseIntegrationTest {

  private static final String NAME_FIELD = "name";
  private static final String ID_FIELD = "id";

  private final LoanTypeEventMessageChecks eventChecks = eventMessageChecks();

  @Test
  void createdEventIsSentWhenLoanTypeCreated() {
    var createdLoanType = createLoanType();

    eventChecks.createdMessagePublished(createdLoanType);
  }

  @Test
  void updatedEventIsSentWhenLoanTypeUpdated() {
    var createdLoanType = createLoanType();
    var loanTypeId = createdLoanType.getString(ID_FIELD);

    var updateRequest = new JsonObject().put(ID_FIELD, loanTypeId).put(NAME_FIELD, uniqueName());
    var updateResponse = get(doPut(client, ResourcePaths.LOAN_TYPES + "/" + loanTypeId, updateRequest));
    assertThat(updateResponse.status()).isEqualTo(SC_NO_CONTENT);

    var updatedLoanType = get(doGet(client, ResourcePaths.LOAN_TYPES + "/" + loanTypeId)).jsonBody();
    eventChecks.updatedMessagePublished(createdLoanType, updatedLoanType);
  }

  @Test
  void deletedEventIsSentWhenLoanTypeDeleted() {
    var createdLoanType = createLoanType();
    var loanTypeId = createdLoanType.getString(ID_FIELD);

    var deleteResponse = get(doDelete(client, ResourcePaths.LOAN_TYPES + "/" + loanTypeId));
    assertThat(deleteResponse.status()).isEqualTo(SC_NO_CONTENT);

    eventChecks.deletedMessagePublished(createdLoanType);
  }

  @Test
  void eventIsNotSentWhenLoanTypeUpdateFailed() {
    var missingLoanTypeId = UUID.randomUUID().toString();

    var updateResponse = get(doPut(client, ResourcePaths.LOAN_TYPES + "/" + missingLoanTypeId,
      new JsonObject().put(NAME_FIELD, uniqueName())));
    assertThat(updateResponse.status()).isEqualTo(SC_NOT_FOUND);

    eventChecks.noUpdatedMessagePublished(missingLoanTypeId);
  }

  @Test
  void eventIsNotSentWhenLoanTypeDeleteFailed() {
    var missingLoanTypeId = UUID.randomUUID().toString();

    var deleteResponse = get(doDelete(client, ResourcePaths.LOAN_TYPES + "/" + missingLoanTypeId));
    assertThat(deleteResponse.status()).isEqualTo(SC_NOT_FOUND);

    eventChecks.noDeletedMessagePublished(missingLoanTypeId);
  }

  @Test
  void eventIsNotSentWhenLoanTypeCreateFailed() {
    var loanTypeId = UUID.randomUUID().toString();
    var invalidRequest = new JsonObject()
      .put(ID_FIELD, loanTypeId)
      .put(NAME_FIELD, uniqueName())
      .put("additional", "invalid");

    var createResponse = get(doPost(client, ResourcePaths.LOAN_TYPES, invalidRequest));
    assertThat(createResponse.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);

    eventChecks.noMessagesPublished(loanTypeId);
  }

  private static JsonObject createLoanType() {
    var response = get(doPost(client, ResourcePaths.LOAN_TYPES, new JsonObject().put(NAME_FIELD, uniqueName())))
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
