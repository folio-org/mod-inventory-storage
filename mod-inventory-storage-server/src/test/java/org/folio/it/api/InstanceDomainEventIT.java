package org.folio.it.api;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.it.HoldingsStorageFixtures.createHolding;
import static org.folio.it.InstanceStorageFixtures.createInstance;
import static org.folio.it.InstanceStorageFixtures.createInstanceType;
import static org.folio.it.LocationStorageFixtures.createLocation;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;
import org.folio.it.BaseIntegrationTest;
import org.folio.support.ResourcePaths;
import org.folio.support.builders.InstanceRequestBuilder;
import org.folio.support.messages.InstanceEventMessageChecks;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InstanceDomainEventIT extends BaseIntegrationTest {

  private final InstanceEventMessageChecks eventChecks = eventMessageChecks();

  @Test
  @DisplayName("should not publish an update event when an instance update fails")
  void shouldNotPublishUpdatedEvent_whenInstanceUpdateFailed() {
    var instanceTypeId = createInstanceType(client);
    var instanceId = createInstance(client, "an instance", instanceTypeId);

    var instance = await(doGet(client, ResourcePaths.INSTANCES + "/" + instanceId)).jsonBody()
      // setting an invalid type id so a FK constraint happens
      .put("instanceTypeId", UUID.randomUUID().toString());

    var updateResponse = await(doPut(client, ResourcePaths.INSTANCES + "/" + instanceId, instance));

    assertThat(updateResponse.status()).isEqualTo(SC_BAD_REQUEST);
    eventChecks.noUpdatedMessagePublished(instanceId);
  }

  @Test
  @DisplayName("should not publish any event when an instance create fails")
  void shouldNotPublishAnyEvent_whenInstanceCreateFailed() {
    var instanceId = UUID.randomUUID().toString();
    var instance = new InstanceRequestBuilder()
      .withId(UUID.fromString(instanceId)).withTitle("an instance").withSource("TEST")
      // an instance type id that doesn't exist, so a FK constraint happens
      .withInstanceTypeId(UUID.randomUUID())
      .create();

    var createResponse = await(doPost(client, ResourcePaths.INSTANCES, instance));

    assertThat(createResponse.status()).isEqualTo(SC_BAD_REQUEST);
    eventChecks.noMessagesPublished(instanceId);
  }

  @Test
  @DisplayName("should not publish a delete event when an instance delete fails")
  void shouldNotPublishDeletedEvent_whenInstanceDeleteFailed() {
    var instanceTypeId = createInstanceType(client);
    var instanceId = createInstance(client, "an instance", instanceTypeId);
    // create a holding so that the instance is not allowed to be removed
    createHolding(client, instanceId, createLocation(client));

    var removeResponse = await(doDelete(client, ResourcePaths.INSTANCES + "/" + instanceId));

    assertThat(removeResponse.status()).isEqualTo(SC_BAD_REQUEST);
    eventChecks.noDeletedMessagePublished(instanceId);
  }

  @Test
  @DisplayName("should not publish a delete event when a delete-all instances request fails")
  void shouldNotPublishDeletedEvent_whenDeleteAllInstancesFailed() {
    var instanceTypeId = createInstanceType(client);
    var instanceId = createInstance(client, "an instance", instanceTypeId);
    createInstance(client, "another instance", instanceTypeId);
    // create a holding so that the instance is not allowed to be removed
    createHolding(client, instanceId, createLocation(client));

    var removeResponse = await(doDelete(client, ResourcePaths.INSTANCES + "?query=cql.allRecords%3D1"));

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
