package org.folio.rest.impl;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.rest.impl.HoldingsStorageFixtures.createHolding;
import static org.folio.rest.impl.InstanceStorageFixtures.createInstance;
import static org.folio.rest.impl.InstanceStorageFixtures.createInstanceType;
import static org.folio.rest.impl.LocationStorageFixtures.createLocation;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;
import org.folio.rest.support.builders.InstanceRequestBuilder;
import org.folio.rest.support.messages.InstanceEventMessageChecks;
import org.junit.jupiter.api.Test;

class InstanceDomainEventIT extends BaseIntegrationTest {

  private final InstanceEventMessageChecks eventChecks = eventMessageChecks();

  @Test
  void eventIsNotSentWhenUpdateFailed() {
    var instanceTypeId = createInstanceType(client);
    var instanceId = createInstance(client, "an instance", instanceTypeId);

    var instance = get(doGet(client, ResourcePaths.INSTANCES + "/" + instanceId)).jsonBody()
      // setting an invalid type id so a FK constraint happens
      .put("instanceTypeId", UUID.randomUUID().toString());

    var updateResponse = get(doPut(client, ResourcePaths.INSTANCES + "/" + instanceId, instance));

    assertThat(updateResponse.status()).isEqualTo(SC_BAD_REQUEST);
    eventChecks.noUpdatedMessagePublished(instanceId);
  }

  @Test
  void eventIsNotSentWhenCreateFailed() {
    var instanceId = UUID.randomUUID().toString();
    var instance = new InstanceRequestBuilder()
      .withId(UUID.fromString(instanceId)).withTitle("an instance").withSource("TEST")
      // an instance type id that doesn't exist, so a FK constraint happens
      .withInstanceTypeId(UUID.randomUUID())
      .create();

    var createResponse = get(doPost(client, ResourcePaths.INSTANCES, instance));

    assertThat(createResponse.status()).isEqualTo(SC_BAD_REQUEST);
    eventChecks.noMessagesPublished(instanceId);
  }

  @Test
  void eventIsNotSentWhenRemoveFailed() {
    var instanceTypeId = createInstanceType(client);
    var instanceId = createInstance(client, "an instance", instanceTypeId);
    // create a holding so that the instance is not allowed to be removed
    createHolding(client, instanceId, createLocation(client));

    var removeResponse = get(doDelete(client, ResourcePaths.INSTANCES + "/" + instanceId));

    assertThat(removeResponse.status()).isEqualTo(SC_BAD_REQUEST);
    eventChecks.noDeletedMessagePublished(instanceId);
  }

  @Test
  void eventIsNotSentWhenRemoveAllFailed() {
    var instanceTypeId = createInstanceType(client);
    var instanceId = createInstance(client, "an instance", instanceTypeId);
    createInstance(client, "another instance", instanceTypeId);
    // create a holding so that the instance is not allowed to be removed
    createHolding(client, instanceId, createLocation(client));

    var removeResponse = get(doDelete(client, ResourcePaths.INSTANCES + "?query=cql.allRecords%3D1"));

    assertThat(removeResponse.status()).isEqualTo(SC_BAD_REQUEST);
    eventChecks.noDeletedMessagePublished(instanceId);
  }

  private static InstanceEventMessageChecks eventMessageChecks() {
    try {
      return new InstanceEventMessageChecks(KAFKA_CONSUMER, new URL(wm.baseUrl()));
    } catch (MalformedURLException e) {
      throw new IllegalStateException(e);
    }
  }
}
