package org.folio.rest.api;

import static org.folio.rest.api.InstanceStorageTest.smallAngryPlanet;
import static org.folio.rest.support.http.InterfaceUrls.holdingsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.instancesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.folio.rest.support.matchers.DomainEventAssertions.assertNoUpdateEvent;
import static org.folio.rest.support.matchers.DomainEventAssertions.noInstanceDeletedMessagePublished;
import static org.folio.rest.support.matchers.DomainEventAssertions.noInstanceMessagesPublished;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.UUID;

import org.folio.rest.support.Response;
import org.folio.rest.support.builders.HoldingRequestBuilder;
import org.junit.Before;
import org.junit.Test;

import io.vertx.core.json.JsonObject;
import lombok.SneakyThrows;

public class InstanceDomainEventTest extends TestBaseWithInventoryUtil {

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
    assertNoUpdateEvent(instance.getId().toString());
  }

  @Test
  public void eventIsNotSentWhenCreateFailed() {
    final UUID instanceId = UUID.randomUUID();
    final JsonObject instanceJson = smallAngryPlanet(instanceId)
      // setting invalid type id so a FK constraint happens
      .put("instanceTypeId", UUID.randomUUID().toString());

    final var createResponse = instancesClient.attemptToCreate(instanceJson);

    assertThat(createResponse.getStatusCode(), is(400));
    noInstanceMessagesPublished(instanceId.toString());
  }

  @Test
  public void eventIsNotSentWhenRemoveFailed() {
    final var instance = instancesClient.create(
      smallAngryPlanet(UUID.randomUUID()));
    // create a holding so that instance is not allowed to be removed
    holdingsClient.create(new HoldingRequestBuilder()
      .forInstance(instance.getId())
      .withPermanentLocation(mainLibraryLocationId));

    final Response removeResponse = instancesClient.attemptToDelete(instance.getId());

    assertThat(removeResponse.getStatusCode(), is(400));
    noInstanceDeletedMessagePublished(instance.getId().toString());
  }

  @Test
  public void eventIsNotSentWhenRemoveAllFailed() {
    final var instance = instancesClient.create(
      smallAngryPlanet(UUID.randomUUID()));
    instancesClient.create(smallAngryPlanet(UUID.randomUUID()));

    // create a holding so that instance is not allowed to be removed
    holdingsClient.create(new HoldingRequestBuilder()
      .forInstance(instance.getId())
      .withPermanentLocation(mainLibraryLocationId));

    final Response removeResponse = instancesClient.attemptDeleteAll();

    assertThat(removeResponse.getStatusCode(), is(400));
    noInstanceDeletedMessagePublished(instance.getId().toString());
  }}
