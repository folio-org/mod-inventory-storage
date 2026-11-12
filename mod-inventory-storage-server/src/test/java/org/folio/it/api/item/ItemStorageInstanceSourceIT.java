package org.folio.it.api.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.HttpStatus.SC_NO_CONTENT;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.support.AwaitConfiguration.awaitAtMost;
import static org.folio.support.ResourcePaths.HOLDINGS;
import static org.folio.support.ResourcePaths.ITEMS;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;

import java.util.UUID;
import org.folio.support.messages.EventMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Item domain events carry the {@code source} of the item's instance as {@code instanceSource}.
 * Instances created by the fixtures have the source {@code TEST}.
 */
class ItemStorageInstanceSourceIT extends ItemStorageTestBase {

  private static final String INSTANCE_SOURCE = "TEST";

  @Test
  @DisplayName("should add the instance source to the item create event")
  void shouldAddInstanceSource_whenItemCreated() {
    // given
    var holdingId = createHoldingRecord();

    // when
    var item = createItem(minimalItemRequest(UUID.randomUUID(), holdingId));

    // then
    var itemId = item.getString("id");
    var event = awaitEvent(itemId, itemId, "CREATE");
    assertThat(event.getNewRepresentation().getString("instanceSource")).isEqualTo(INSTANCE_SOURCE);
    assertThat(event.getNewRepresentation().getString("instanceId")).isEqualTo(getInstanceId(holdingId));
  }

  @Test
  @DisplayName("should add the instance source to both representations of the item update event")
  void shouldAddInstanceSource_whenItemUpdated() {
    // given
    var holdingId = createHoldingRecord();
    var item = createItem(minimalItemRequest(UUID.randomUUID(), holdingId));

    // when
    updateItem(item.copy().put("barcode", UUID.randomUUID().toString()));

    // then
    var itemId = item.getString("id");
    var event = awaitEvent(itemId, itemId, "UPDATE");
    assertThat(event.getOldRepresentation().getString("instanceSource")).isEqualTo(INSTANCE_SOURCE);
    assertThat(event.getNewRepresentation().getString("instanceSource")).isEqualTo(INSTANCE_SOURCE);
  }

  @Test
  @DisplayName("should add the instance source to the item delete event")
  void shouldAddInstanceSource_whenItemDeleted() {
    // given
    var holdingId = createHoldingRecord();
    var item = createItem(minimalItemRequest(UUID.randomUUID(), holdingId));
    var itemId = item.getString("id");

    // when
    assertThat(await(doDelete(client, ITEMS + "/" + itemId)).status()).isEqualTo(SC_NO_CONTENT);

    // then
    var event = awaitEvent(getInstanceId(holdingId), itemId, "DELETE");
    assertThat(event.getOldRepresentation().getString("instanceSource")).isEqualTo(INSTANCE_SOURCE);
  }

  @Test
  @DisplayName("should add the instance source to the item delete event when items are deleted by CQL")
  void shouldAddInstanceSource_whenItemsDeletedByCql() {
    // given
    var holdingId = createHoldingRecord();
    var item = createItem(minimalItemRequest(UUID.randomUUID(), holdingId));
    var itemId = item.getString("id");

    // when
    assertThat(await(doDelete(client, ITEMS + "?query=id==" + itemId)).status()).isEqualTo(SC_NO_CONTENT);

    // then
    var event = awaitEvent(itemId, itemId, "DELETE");
    assertThat(event.getOldRepresentation().getString("instanceSource")).isEqualTo(INSTANCE_SOURCE);
    assertThat(event.getOldRepresentation().getString("instanceId")).isEqualTo(getInstanceId(holdingId));
    assertThat(event.getOldRepresentation().getString("id")).isEqualTo(itemId);
  }

  private static EventMessage awaitEvent(String key, String itemId, String type) {
    awaitAtMost().until(() -> KAFKA_CONSUMER.getMessagesForItem(key, itemId), hasItem(hasProperty("type", is(type))));
    return KAFKA_CONSUMER.getMessagesForItem(key, itemId).stream()
      .filter(message -> type.equals(message.getType()))
      .findFirst()
      .orElseThrow();
  }

  private static String getInstanceId(String holdingId) {
    return await(doGet(client, HOLDINGS + "/" + holdingId)).jsonBody().getString("instanceId");
  }
}
