package org.folio.rest.api;

import static org.folio.rest.api.InstanceStorageTest.smallAngryPlanet;
import static org.folio.rest.support.http.InterfaceUrls.holdingsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.instancesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.vertx.core.json.JsonObject;
import java.util.Map;
import java.util.UUID;
import lombok.SneakyThrows;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.support.Response;
import org.folio.rest.support.builders.HoldingRequestBuilder;
import org.folio.rest.support.messages.InstanceEventMessageChecks;
import org.junit.Before;
import org.junit.Test;

public class InstanceDomainEventTest extends TestBaseWithInventoryUtil {
  private final InstanceEventMessageChecks instanceMessageChecks
    = new InstanceEventMessageChecks(KAFKA_CONSUMER);

  @SneakyThrows
  @Before
  public void beforeEach() {
    StorageTestSuite.deleteAll(itemsStorageUrl(""));
    StorageTestSuite.deleteAll(holdingsStorageUrl(""));
    StorageTestSuite.deleteAll(instancesStorageUrl(""));

    removeAllEvents();
  }

  @Test
  public void eventIsNotSentWhenUpdateFailed() {
    final var instance = instancesClient.create(
      smallAngryPlanet(UUID.randomUUID()));

    final JsonObject updatedInstance = instance.copyJson()
      // setting invalid type id so a FK constraint happens
      .put("instanceTypeId", UUID.randomUUID().toString());

    final var updateInstance = instancesClient.attemptToReplace(instance.getId(),
      updatedInstance);

    assertThat(updateInstance.getStatusCode(), is(400));
    instanceMessageChecks.noUpdatedMessagePublished(instance.getId().toString());
  }

  @Test
  public void eventIsNotSentWhenCreateFailed() {
    final UUID instanceId = UUID.randomUUID();
    final JsonObject instanceJson = smallAngryPlanet(instanceId)
      // setting invalid type id so a FK constraint happens
      .put("instanceTypeId", UUID.randomUUID().toString());

    final var createResponse = instancesClient.attemptToCreate(instanceJson);

    assertThat(createResponse.getStatusCode(), is(400));
    instanceMessageChecks.noMessagesPublished(instanceId.toString());
  }

  @Test
  public void eventIsNotSentWhenRemoveFailed() {
    final var instance = instancesClient.create(
      smallAngryPlanet(UUID.randomUUID()));
    // create a holding so that instance is not allowed to be removed
    HoldingRequestBuilder holdingBuilder = new HoldingRequestBuilder()
      .forInstance(instance.getId())
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID);
    holdingsClient.create(holdingBuilder.create(), TENANT_ID,
      Map.of(XOkapiHeaders.URL, mockServer.baseUrl()));

    final Response removeResponse = instancesClient.attemptToDelete(instance.getId());

    assertThat(removeResponse.getStatusCode(), is(400));

    instanceMessageChecks.noDeletedMessagePublished(instance.getId().toString());
  }

  @Test
  public void eventIsNotSentWhenRemoveAllFailed() {
    final var instance = instancesClient.create(
      smallAngryPlanet(UUID.randomUUID()));
    instancesClient.create(smallAngryPlanet(UUID.randomUUID()));

    // create a holding so that instance is not allowed to be removed
    HoldingRequestBuilder holdingBuilder = new HoldingRequestBuilder()
      .forInstance(instance.getId())
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID);
    holdingsClient.create(holdingBuilder.create(), TENANT_ID,
      Map.of(XOkapiHeaders.URL, mockServer.baseUrl()));

    final Response removeResponse = instancesClient.attemptDeleteAll();

    assertThat(removeResponse.getStatusCode(), is(400));

    instanceMessageChecks.noDeletedMessagePublished(instance.getId().toString());
  }
}
