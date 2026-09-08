package org.folio.rest.api;

import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.vertx.core.json.JsonObject;
import java.net.HttpURLConnection;
import java.util.UUID;
import org.folio.rest.support.http.ResourceClient;
import org.folio.rest.support.messages.LoanTypeEventMessageChecks;
import org.folio.utility.ModuleUtility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoanTypeDomainEventTest extends TestBase {

  private final ResourceClient loanTypesClient = ResourceClient.forLoanTypes(ModuleUtility.getClient());
  private final LoanTypeEventMessageChecks loanTypeMessageChecks = new LoanTypeEventMessageChecks(KAFKA_CONSUMER);

  @BeforeEach
  void beforeEach() {
    removeAllEvents();
  }

  @Test
  void createdEventIsSentWhenLoanTypeCreated() {
    var createdLoanType = loanTypesClient.create(new JsonObject().put("name", uniqueName())).getJson();

    assertNotNull(createdLoanType);
    loanTypeMessageChecks.createdMessagePublished(createdLoanType);
  }

  @Test
  void updatedEventIsSentWhenLoanTypeUpdated() {
    var createdLoanType = loanTypesClient.create(new JsonObject().put("name", uniqueName())).getJson();
    var loanTypeId = createdLoanType.getString("id");

    var updateRequest = new JsonObject()
      .put("id", loanTypeId)
      .put("name", uniqueName());

    var updateResponse = loanTypesClient.attemptToReplace(loanTypeId, updateRequest);
    assertThat(updateResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    var updatedLoanType = loanTypesClient.getByIdIfPresent(loanTypeId).getJson();
    loanTypeMessageChecks.updatedMessagePublished(createdLoanType, updatedLoanType);
  }

  @Test
  void deletedEventIsSentWhenLoanTypeDeleted() {
    var createdLoanType = loanTypesClient.create(new JsonObject().put("name", uniqueName())).getJson();
    var loanTypeId = createdLoanType.getString("id");

    var deleteResponse = loanTypesClient.deleteIfPresent(loanTypeId);
    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    loanTypeMessageChecks.deletedMessagePublished(createdLoanType);
  }

  @Test
  void eventIsNotSentWhenLoanTypeUpdateFailed() {
    var missingLoanTypeId = UUID.randomUUID().toString();

    var updateResponse = loanTypesClient.attemptToReplace(
      missingLoanTypeId, new JsonObject().put("name", uniqueName()));
    assertThat(updateResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));

    loanTypeMessageChecks.noUpdatedMessagePublished(missingLoanTypeId);
  }

  @Test
  void eventIsNotSentWhenLoanTypeDeleteFailed() {
    var missingLoanTypeId = UUID.randomUUID().toString();

    var deleteResponse = loanTypesClient.deleteIfPresent(missingLoanTypeId);
    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));

    loanTypeMessageChecks.noDeletedMessagePublished(missingLoanTypeId);
  }

  @Test
  void eventIsNotSentWhenLoanTypeCreateFailed() {
    var loanTypeId = UUID.randomUUID().toString();
    var invalidRequest = new JsonObject()
      .put("id", loanTypeId)
      .put("name", uniqueName())
      .put("additional", "invalid");

    var createResponse = loanTypesClient.attemptToCreate(invalidRequest);
    assertThat(createResponse.getStatusCode(), is(HTTP_UNPROCESSABLE_ENTITY.toInt()));

    loanTypeMessageChecks.noMessagesPublished(loanTypeId);
  }

  private static String uniqueName() {
    return "loan-type-" + UUID.randomUUID();
  }
}
