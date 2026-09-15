package org.folio.it.api.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.HttpStatus.SC_CREATED;
import static org.folio.HttpStatus.SC_NO_CONTENT;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.support.ResourcePaths.ITEMS;

import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ItemStorageOrderIT extends ItemStorageTestBase {

  @Test
  @DisplayName("should calculate different order values for several items created concurrently")
  void shouldCalculateDifferentOrderValues_forItemsCreatedConcurrently() {
    var holdingId = createHoldingRecord();
    var id1 = UUID.randomUUID();
    var id2 = UUID.randomUUID();

    var responses = runConcurrentPosts(Map.of(id1, minimalItemRequest(id1, holdingId),
      id2, minimalItemRequest(id2, holdingId)));

    assertThat(responses).isNotEmpty()
      .allSatisfy(response -> assertThat(response.status()).isEqualTo(SC_CREATED));
    var order1 = getItemJsonById(id1.toString()).getInteger(ORDER_FIELD);
    var order2 = getItemJsonById(id2.toString()).getInteger(ORDER_FIELD);
    assertThat(order1).isIn(1, 2);
    assertThat(order2).isIn(1, 2);
    assertThat(order1).isNotEqualTo(order2);
  }

  @Test
  @DisplayName("should calculate order for concurrently created items mixing manual and automatic order")
  void shouldCalculateOrder_forConcurrentItemsWithManualAndAutoOrder() {
    var holdingId = createHoldingRecord();
    var id1 = UUID.randomUUID();
    var id2 = UUID.randomUUID();
    var id3 = UUID.randomUUID();
    var item2 = minimalItemRequest(id2, holdingId).put(ORDER_FIELD, 6);

    var responses = runConcurrentPosts(Map.of(id1, minimalItemRequest(id1, holdingId), id2, item2,
      id3, minimalItemRequest(id3, holdingId)));

    assertThat(responses).isNotEmpty()
      .allSatisfy(response -> assertThat(response.status()).isEqualTo(SC_CREATED));
    assertThat(getItemJsonById(id2.toString()).getInteger(ORDER_FIELD)).isEqualTo(6);
    var order1 = getItemJsonById(id1.toString()).getInteger(ORDER_FIELD);
    var order3 = getItemJsonById(id3.toString()).getInteger(ORDER_FIELD);
    assertThat(order1).isIn(1, 2, 7, 8);
    assertThat(order3).isIn(1, 2, 7, 8);
    assertThat(order1).isNotEqualTo(order3);

    var item4 = createItem(minimalItemRequest(UUID.randomUUID(), holdingId));

    assertThat(item4.getInteger(ORDER_FIELD)).isIn(7, 8, 9);
  }

  @Test
  @DisplayName("should calculate order for a new item when the previous request's order decreases")
  void shouldCalculateOrder_whenOrderDecreasesInNextRequest() {
    var holdingId = createHoldingRecord();
    var id1 = UUID.randomUUID();
    var id2 = UUID.randomUUID();

    var responses = runConcurrentPosts(Map.of(id1, minimalItemRequest(id1, holdingId).put(ORDER_FIELD, 1000),
      id2, minimalItemRequest(id2, holdingId).put(ORDER_FIELD, 10)));
    assertThat(responses).isNotEmpty()
      .allSatisfy(response -> assertThat(response.status()).isEqualTo(SC_CREATED));

    var item3 = createItem(minimalItemRequest(UUID.randomUUID(), holdingId));

    assertThat(getItemJsonById(id1.toString()).getInteger(ORDER_FIELD)).isEqualTo(1000);
    assertThat(getItemJsonById(id2.toString()).getInteger(ORDER_FIELD)).isEqualTo(10);
    assertThat(item3.getInteger(ORDER_FIELD)).isEqualTo(1001);
  }

  @Test
  @DisplayName("should reset the item order when all items are deleted and a new one is created")
  void shouldResetItemOrder_whenAllItemsAreDeletedAndNewOneIsCreated() {
    var holdingId = createHoldingRecord();
    var item1 = smallAngryPlanet(UUID.randomUUID(), holdingId);
    var item2 = smallAngryPlanet(UUID.randomUUID(), holdingId).put("barcode", "1234567890");
    createItem(item1);
    createItem(item2);
    assertThat(getMaxOrder(holdingId)).isEqualTo(2);

    assertThat(await(doDelete(client, ITEMS + "?query=holdingsRecordId==" + holdingId)).status())
      .isEqualTo(SC_NO_CONTENT);
    assertGetNotFound(ITEMS + "/" + item1.getString("id"));
    assertGetNotFound(ITEMS + "/" + item2.getString("id"));
    assertThat(getMaxOrder(holdingId)).isEqualTo(2);

    var newItem = createItem(item2);

    assertThat(getMaxOrder(holdingId)).isEqualTo(1);
    assertThat(newItem.getInteger(ORDER_FIELD)).isEqualTo(1);
  }

  @Test
  @DisplayName("should set the new item order to the next value even after an item is deleted")
  void shouldSetNewItemOrder_toNextValueEvenAfterItemDeletion() {
    var holdingId = createHoldingRecord();
    var item1 = createItem(smallAngryPlanet(UUID.randomUUID(), holdingId));
    var item2 = createItem(smallAngryPlanet(UUID.randomUUID(), holdingId).put("barcode", "1234567890"));
    assertThat(item1.getInteger(ORDER_FIELD)).isEqualTo(1);
    assertThat(item2.getInteger(ORDER_FIELD)).isEqualTo(2);
    assertThat(getMaxOrder(holdingId)).isEqualTo(2);

    assertThat(await(doDelete(client, ITEMS + "?query=id==" + item2.getString("id"))).status())
      .isEqualTo(SC_NO_CONTENT);
    assertGetNotFound(ITEMS + "/" + item2.getString("id"));
    assertThat(getMaxOrder(holdingId)).isEqualTo(2);

    var item3 = createItem(smallAngryPlanet(UUID.randomUUID(), holdingId).put("barcode", "1111111111"));
    assertThat(item3.getInteger(ORDER_FIELD)).isEqualTo(3);
    assertThat(getMaxOrder(holdingId)).isEqualTo(3);
  }

  private static int getMaxOrder(String holdingId) {
    var result = runQuery("SELECT max_order FROM item_order_tracker WHERE holdings_id = '" + holdingId + "'");
    return result.iterator().next().toJson().getInteger("max_order");
  }

  private static List<TestResponse> runConcurrentPosts(Map<UUID, JsonObject> items) {
    var barrier = new CyclicBarrier(items.size());

    List<Callable<TestResponse>> tasks = items.values().stream()
      .map(itemJson -> (Callable<TestResponse>) () -> {
        barrier.await();
        return await(doPost(client, ITEMS, itemJson));
      }).toList();

    try (ExecutorService executor = Executors.newFixedThreadPool(items.size())) {
      var futures = tasks.stream().map(executor::submit).toList();
      var results = new ArrayList<TestResponse>();
      for (var future : futures) {
        results.add(future.get());
      }
      return results;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
