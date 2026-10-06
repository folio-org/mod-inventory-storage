package org.folio.it.api.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.HttpStatus.SC_BAD_REQUEST;
import static org.folio.HttpStatus.SC_NO_CONTENT;
import static org.folio.HttpStatus.SC_UNPROCESSABLE_CONTENT;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.support.ResourcePaths.ITEMS;

import io.vertx.core.json.JsonObject;
import java.time.Instant;
import java.util.UUID;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Item;
import org.folio.support.builders.ItemRequestBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ItemStorageStatusIT extends ItemStorageTestBase {

  @Test
  @DisplayName("should place an item in transit")
  void shouldPlaceItem_inTransit() {
    var holdingId = createHoldingRecord();
    var id = UUID.randomUUID();
    createItem(smallAngryPlanet(id, holdingId).put("hrid", "testHRID"));
    var inTransitServicePointId = UUID.randomUUID().toString();

    var replacement = getItemJsonById(id.toString())
      .put(STATUS_KEY, new JsonObject().put("name", "In transit"))
      .put("inTransitDestinationServicePointId", inTransitServicePointId).put("tags", tags(TAG_VALUE));
    assertThat(updateItem(replacement).status()).isEqualTo(SC_NO_CONTENT);

    var item = getItemJsonById(id.toString());
    assertThat(item.getJsonObject(STATUS_KEY).getString("name")).isEqualTo("In transit");
    assertThat(item.getString("inTransitDestinationServicePointId")).isEqualTo(inTransitServicePointId);
  }

  @Test
  @DisplayName("should set the status date when an item's status is updated")
  void shouldSetStatusDate_whenItemStatusUpdated() {
    var holdingId = createHoldingRecord();
    var id = UUID.randomUUID();
    var initialItem = createItem(smallAngryPlanet(id, holdingId).put("hrid", "testHRID")).mapTo(Item.class);
    var initialStatusDate = initialItem.getStatus().getDate().toInstant();

    var replacement = getItemJsonById(id.toString()).put(STATUS_KEY, new JsonObject().put("name", "Checked out"));
    assertThat(updateItem(replacement).status()).isEqualTo(SC_NO_CONTENT);

    var item = getItemJsonById(id.toString()).mapTo(Item.class);
    assertThat(item.getStatus().getName().value()).isEqualTo("Checked out");
    assertThat(item.getStatus().getDate().toInstant()).isAfter(initialStatusDate);
  }

  @Test
  @DisplayName("should change the status date when the item's status changes")
  void shouldChangeStatusDate_whenItemStatusChanges() {
    var holdingId = createHoldingRecord();
    var id = UUID.randomUUID();
    createItem(smallAngryPlanet(id, holdingId).put("hrid", "testHRID"));

    var checkedOutStatusDate = replaceStatusAndGetDate(id, "Checked out");
    var availableStatusDate = replaceStatusAndGetDate(id, "Available");

    assertThat(availableStatusDate).isAfter(checkedOutStatusDate);
  }

  @Test
  @DisplayName("should not allow the item status date to be set directly")
  void shouldNotAllowItemStatusDate_toBeSetDirectly() {
    var holdingId = createHoldingRecord();
    var id = UUID.randomUUID();
    var createdItem = createItem(smallAngryPlanet(id, holdingId));
    var initialStatusDate = createdItem.getJsonObject(STATUS_KEY).getString("date");

    var itemWithUpdatedStatus = getItemJsonById(id.toString())
      .put(STATUS_KEY, new JsonObject().put("name", "Checked out"));
    updateItem(itemWithUpdatedStatus);
    var updatedStatus = getItemJsonById(id.toString()).getJsonObject(STATUS_KEY);
    assertThat(updatedStatus.getString("name")).isEqualTo("Checked out");
    assertThat(Instant.parse(updatedStatus.getString("date"))).isAfter(Instant.parse(initialStatusDate));

    var itemWithUpdatedStatusDate = getItemJsonById(id.toString());
    itemWithUpdatedStatusDate.getJsonObject(STATUS_KEY).put("date", Instant.now().plusSeconds(86_400).toString());
    updateItem(itemWithUpdatedStatusDate);

    assertThat(getItemJsonById(id.toString()).getJsonObject(STATUS_KEY).getString("date"))
      .isEqualTo(updatedStatus.getString("date"));
  }

  @Test
  @DisplayName("should not change the item status date when the status has not changed")
  void shouldNotChangeItemStatusDate_whenStatusHasNotChanged() {
    var holdingId = createHoldingRecord();
    var id = UUID.randomUUID();
    createItem(smallAngryPlanet(id, holdingId));
    var initialStatusDate = getItemJsonById(id.toString()).getJsonObject(STATUS_KEY).getString("date");

    var itemWithUpdatedStatus = getItemJsonById(id.toString())
      .put(STATUS_KEY, new JsonObject().put("name", "Available").put("date", Instant.now().toString()));
    updateItem(itemWithUpdatedStatus);

    assertThat(getItemJsonById(id.toString()).getJsonObject(STATUS_KEY).getString("date")).isEqualTo(initialStatusDate);
  }

  @Test
  @DisplayName("should not change the status date after an update unrelated to status")
  void shouldNotChangeStatusDate_afterUnrelatedUpdate() {
    var holdingId = createHoldingRecord();
    var id = UUID.randomUUID();
    createItem(smallAngryPlanet(id, holdingId));
    var initialStatusDate = getItemJsonById(id.toString()).getJsonObject(STATUS_KEY).getString("date");

    var itemWithUpdatedStatus = getItemJsonById(id.toString())
      .put(STATUS_KEY, new JsonObject().put("name", "Checked out"));
    updateItem(itemWithUpdatedStatus);
    var checkedOutStatusDate = getItemJsonById(id.toString()).getJsonObject(STATUS_KEY).getString("date");
    assertThat(checkedOutStatusDate).isNotEqualTo(initialStatusDate);

    var itemWithUpdatedCallNumber = getItemJsonById(id.toString()).put("itemLevelCallNumber", "newItemLevelCallNumber");
    updateItem(itemWithUpdatedCallNumber);

    var afterCallNumberUpdate = getItemJsonById(id.toString());
    assertThat(afterCallNumberUpdate.getString("itemLevelCallNumber")).isEqualTo("newItemLevelCallNumber");
    assertThat(afterCallNumberUpdate.getJsonObject(STATUS_KEY).getString("date")).isEqualTo(checkedOutStatusDate);
  }

  @Test
  @DisplayName("should return 400 when creating an item with an unknown status")
  void shouldReturn400_whenCreatingItemWithUnknownStatus() {
    var itemToCreate = new JsonObject().put("id", UUID.randomUUID().toString())
      .put(STATUS_KEY, new JsonObject().put("name", "Wrong status name"));

    var response = await(doPost(client, ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString()).contains("problem: Wrong status name");
  }

  @Test
  @DisplayName("should return 422 when removing the item status")
  void shouldReturn422_whenRemovingItemStatus() {
    var holdingId = createHoldingRecord();
    var id = UUID.randomUUID();
    createItem(smallAngryPlanet(id, holdingId));
    var replacement = getItemJsonById(id.toString());
    replacement.remove(STATUS_KEY);

    var response = updateItem(replacement);

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    var errors = response.jsonBody().mapTo(Errors.class).getErrors();
    assertThat(errors).hasSize(1);
    assertThat(errors.getFirst().getMessage()).isIn("may not be null", "must not be null");
    assertThat(errors.getFirst().getParameters().getFirst().getKey()).isEqualTo(STATUS_KEY);
  }

  @Test
  @DisplayName("should return 422 when removing the item status name")
  void shouldReturn422_whenRemovingItemStatusName() {
    var holdingId = createHoldingRecord();
    var id = UUID.randomUUID();
    createItem(smallAngryPlanet(id, holdingId));
    var replacement = getItemJsonById(id.toString());
    replacement.getJsonObject(STATUS_KEY).remove("name");

    var response = updateItem(replacement);

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    var errors = response.jsonBody().mapTo(Errors.class).getErrors();
    assertThat(errors).hasSize(1);
    assertThat(errors.getFirst().getMessage()).isIn("may not be null", "must not be null");
    assertThat(errors.getFirst().getParameters().getFirst().getKey()).isEqualTo("status.name");
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "Aged to lost", "Available", "Awaiting pickup", "Awaiting delivery", "Checked out", "Claimed returned",
    "Declared lost", "In process", "In process (non-requestable)", "In transit", "Intellectual item",
    "Long missing", "Lost and paid", "Missing", "On order", "Paged", "Restricted", "Order closed",
    "Unavailable", "Unknown", "Withdrawn"
  })
  @DisplayName("should create an item with each allowed status")
  void shouldCreateItem_withEachAllowedStatus(String status) {
    var holdingId = createHoldingRecord();
    var itemToCreate = new ItemRequestBuilder().forHolding(UUID.fromString(holdingId))
      .withMaterialType(UUID.fromString(materialTypeId)).withPermanentLoanType(UUID.fromString(loanTypeId))
      .withStatus(status);

    var item = createItem(itemToCreate.create());

    assertThat(item.getJsonObject(STATUS_KEY).getString("name")).isEqualTo(status);
    assertThat(getItemJsonById(item.getString("id")).getJsonObject(STATUS_KEY).getString("name")).isEqualTo(status);
  }

  private static Instant replaceStatusAndGetDate(UUID id, String status) {
    var replacement = getItemJsonById(id.toString()).put(STATUS_KEY, new JsonObject().put("name", status));
    assertThat(updateItem(replacement).status()).isEqualTo(SC_NO_CONTENT);
    return Instant.parse(getItemJsonById(id.toString()).getJsonObject(STATUS_KEY).getString("date"));
  }
}
