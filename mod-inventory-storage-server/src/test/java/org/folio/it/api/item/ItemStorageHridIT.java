package org.folio.it.api.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.HttpStatus.SC_BAD_REQUEST;
import static org.folio.HttpStatus.SC_CREATED;
import static org.folio.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.folio.HttpStatus.SC_NO_CONTENT;
import static org.folio.HttpStatus.SC_UNPROCESSABLE_CONTENT;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.support.ResourcePaths.ITEMS;

import io.vertx.core.json.JsonObject;
import java.util.UUID;
import org.folio.rest.jaxrs.model.Errors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ItemStorageHridIT extends ItemStorageTestBase {

  @Test
  @DisplayName("should create an item when a hrid is supplied")
  void shouldCreateItem_whenHridIsSupplied() {
    var holdingId = createHoldingRecord();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId).put("hrid", "ITEM12345")
      .put("tags", tags(TAG_VALUE));

    var item = createItem(itemToCreate);

    assertThat(item.getString("hrid")).isEqualTo("ITEM12345");
    assertThat(getItemJsonById(item.getString("id")).getString("hrid")).isEqualTo("ITEM12345");
  }

  @Test
  @DisplayName("should update an item when the hrid has not changed")
  void shouldUpdateItem_whenHridHasNotChanged() {
    var holdingId = createHoldingRecord();
    var itemId = UUID.randomUUID();
    var itemToCreate = minimalItemRequest(itemId, holdingId);
    setItemSequence(1);
    createItem(itemToCreate);

    var createdItem = getItemJsonById(itemId.toString());
    assertThat(updateItem(createdItem.put("tags", tags("new tag"))).status()).isEqualTo(SC_NO_CONTENT);

    var updatedItem = getItemJsonById(itemId.toString());
    assertThat(updatedItem.getString("hrid")).isEqualTo("it00000000001");
    assertThat(getTags(updatedItem)).containsExactly("new tag");
  }

  @Test
  @DisplayName("should return 400 when creating an item with a duplicate hrid")
  void shouldReturn400_whenCreatingItemWithDuplicateHrid() {
    var holdingId = createHoldingRecord();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId);
    setItemSequence(1);
    createItem(itemToCreate);
    assertThat(getItemJsonById(itemToCreate.getString("id")).getString("hrid")).isEqualTo("it00000000001");

    var duplicate = minimalItemRequest(UUID.randomUUID(), holdingId).put("hrid", "it00000000001");
    var response = await(doPost(client, ITEMS, duplicate));

    assertHridError(response, "it00000000001");
  }

  @Test
  @DisplayName("should return 500 when hrid generation fails because the sequence is exhausted")
  void shouldReturn500_whenHridGenerationFails() {
    var holdingId = createHoldingRecord();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId);
    setItemSequence(99_999_999_999L);
    createItem(itemToCreate);
    assertThat(getItemJsonById(itemToCreate.getString("id")).getString("hrid")).isEqualTo("it99999999999");

    var response =
      await(doPost(client, ITEMS, minimalItemRequest(UUID.randomUUID(), holdingId)));

    assertThat(response.status()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
    assertThat(response.body().toString()).contains("hrid_items_seq");
  }

  @Test
  @DisplayName("should return 400 when changing the hrid after creation")
  void shouldReturn400_whenChangingHridAfterCreation() {
    var holdingId = createHoldingRecord();
    var itemId = UUID.randomUUID();
    setItemSequence(1);
    createItem(minimalItemRequest(itemId, holdingId));
    var item = getItemJsonById(itemId.toString()).put("hrid", "ABC123");

    var response = updateItem(item);

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body()).hasToString("The hrid field cannot be changed: new=ABC123, old=it00000000001");
  }

  @Test
  @DisplayName("should return 400 when removing the hrid after creation")
  void shouldReturn400_whenRemovingHridAfterCreation() {
    var holdingId = createHoldingRecord();
    var itemId = UUID.randomUUID();
    setItemSequence(1);
    createItem(minimalItemRequest(itemId, holdingId));
    var item = getItemJsonById(itemId.toString());
    item.remove("hrid");

    var response = updateItem(item);

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body()).hasToString("The hrid field cannot be changed: new=null, old=it00000000001");
  }

  @Test
  @DisplayName("should generate hrids for a synchronous batch")
  void shouldGenerateHrids_forSynchronousBatch() {
    setItemSequence(1);
    var itemsArray = threeItems();

    assertThat(syncBatch(itemsArray).status()).isEqualTo(SC_CREATED);

    itemsArray.forEach(item -> {
      var id = ((JsonObject) item).getString("id");
      assertExists((JsonObject) item);
      assertThat(getItemJsonById(id).getString("hrid")).isBetween("it00000000001", "it00000000003");
    });
  }

  @Test
  @DisplayName("should generate hrids for items missing one in a synchronous batch")
  void shouldGenerateHrids_forItemsMissingOneInSynchronousBatch() {
    setItemSequence(1);
    var hrid = "ABC123";
    var itemsArray = threeItems();
    itemsArray.getJsonObject(1).put("hrid", hrid);

    assertThat(syncBatch(itemsArray).status()).isEqualTo(SC_CREATED);

    var first = getItemJsonById(itemsArray.getJsonObject(0).getString("id"));
    assertExists(itemsArray.getJsonObject(0));
    assertThat(first.getString("hrid")).isBetween("it00000000001", "it00000000002");
    assertThat(getItemJsonById(itemsArray.getJsonObject(1).getString("id")).getString("hrid")).isEqualTo(hrid);
    var third = getItemJsonById(itemsArray.getJsonObject(2).getString("id"));
    assertThat(third.getString("hrid")).isBetween("it00000000001", "it00000000002");
  }

  @Test
  @DisplayName("should return 422 when a synchronous batch has duplicate hrids")
  void shouldReturn422_whenSynchronousBatchHasDuplicateHrids() {
    setItemSequence(1);
    var duplicateHrid = "it00000000001";
    var itemsArray = threeItems();
    itemsArray.getJsonObject(0).put("hrid", duplicateHrid);
    itemsArray.getJsonObject(1).put("hrid", duplicateHrid);

    var response = syncBatch(itemsArray);

    assertHridError(response, duplicateHrid);
    itemsArray.forEach(item -> assertGetNotFound(ITEMS + "/" + ((JsonObject) item).getString("id")));
  }

  @Test
  @DisplayName("should return 500 when hrid generation fails for a synchronous batch")
  void shouldReturn500_whenSynchronousBatchHridGenerationFails() {
    setItemSequence(99_999_999_999L);
    var itemsArray = threeItems();

    var response = syncBatch(itemsArray);

    assertThat(response.status()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
    assertThat(response.body().toString()).contains("hrid_items_seq");
    itemsArray.forEach(item -> assertGetNotFound(ITEMS + "/" + ((JsonObject) item).getString("id")));
  }

  private static void assertHridError(TestResponse response, String hrid) {
    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    var error = response.jsonBody().mapTo(Errors.class).getErrors().getFirst();
    assertThat(error.getMessage()).isEqualTo("HRID value already exists in table item: " + hrid);
    var parameter = error.getParameters().getFirst();
    assertThat(parameter.getKey()).isEqualTo("lower(f_unaccent(jsonb ->> 'hrid'::text))");
    assertThat(parameter.getValue()).isEqualTo(hrid);
  }
}
