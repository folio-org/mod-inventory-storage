package org.folio.rest.impl;

import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.rest.impl.HoldingsStorageFixtures.createHolding;
import static org.folio.rest.impl.HoldingsStorageFixtures.createItem;
import static org.folio.rest.impl.HoldingsStorageFixtures.createLoanType;
import static org.folio.rest.impl.HoldingsStorageFixtures.createMaterialType;
import static org.folio.rest.impl.InstanceStorageFixtures.createInstance;
import static org.folio.rest.impl.InstanceStorageFixtures.createInstanceType;
import static org.folio.rest.impl.LocationStorageFixtures.createLocation;

import io.vertx.core.json.JsonObject;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers the {@code complete_updated_date} column on {@code instance}, maintained by Postgres
 * triggers (see {@code create_instance_complete_updated_date_triggers.sql}) so that OAI-PMH
 * harvesting can detect an instance needs re-harvesting even when only a child holdings record,
 * item, or bound-with part changed - not the instance row itself. Renamed from the legacy
 * {@code OaiPmhTriggersTest}, whose name described the trigger's consumer rather than what it
 * actually updates.
 */
class InstanceCompleteUpdatedDateIT extends BaseIntegrationTest {

  private static final String COMPLETE_UPDATED_DATE_COLUMN = "complete_updated_date";
  private static final String ID_FIELD = "id";
  private static final String INSTANCE_ID_FIELD = "instanceId";
  private static final String HOLDINGS_RECORD_ID_FIELD = "holdingsRecordId";
  private static final String ITEM_ID_FIELD = "itemId";

  private static String instanceTypeId;
  private static String materialTypeId;
  private static String loanTypeId;
  private static String locationId;

  @BeforeAll
  static void seedReferenceData() {
    instanceTypeId = createInstanceType(client);
    materialTypeId = createMaterialType(client);
    loanTypeId = createLoanType(client);
    locationId = createLocation(client);
  }

  @BeforeEach
  void clearRecords() {
    runQuery("TRUNCATE TABLE bound_with_part, item, holdings_record, instance CASCADE");
  }

  @Test
  @DisplayName("should set completeUpdatedDate when an instance is created")
  void shouldSetCompleteUpdatedDate_whenInstanceCreated() {
    var instanceId = createInstance(client, "An instance", instanceTypeId);

    assertThat(completeUpdatedDate(instanceId)).isNotNull();
  }

  @Test
  @DisplayName("should bump completeUpdatedDate when an item is created under one of the instance's holdings")
  void shouldBumpCompleteUpdatedDate_whenItemCreated() {
    var instanceId = createInstance(client, "An instance", instanceTypeId);
    var holdingId = createHolding(client, instanceId, locationId);
    var dateBefore = completeUpdatedDate(instanceId);

    createItem(client, holdingId, materialTypeId, loanTypeId);

    assertThat(completeUpdatedDate(instanceId)).isAfter(dateBefore);
  }

  @Test
  @DisplayName("should bump completeUpdatedDate when a holding is created under the instance")
  void shouldBumpCompleteUpdatedDate_whenHoldingCreated() {
    var instanceId = createInstance(client, "An instance", instanceTypeId);
    var dateBefore = completeUpdatedDate(instanceId);

    createHolding(client, instanceId, locationId);

    assertThat(completeUpdatedDate(instanceId)).isAfter(dateBefore);
  }

  @Test
  @DisplayName("should bump completeUpdatedDate when the instance row itself is updated")
  void shouldBumpCompleteUpdatedDate_whenInstanceUpdated() {
    var instanceId = createInstance(client, "An instance", instanceTypeId);
    var holdingId = createHolding(client, instanceId, locationId);
    createItem(client, holdingId, materialTypeId, loanTypeId);
    var dateBefore = completeUpdatedDate(instanceId);

    runQuery("UPDATE instance SET created_by = 'some user' WHERE id = '" + instanceId + "'");

    assertThat(completeUpdatedDate(instanceId)).isAfter(dateBefore);
  }

  @Test
  @DisplayName("should bump completeUpdatedDate when an item is updated directly")
  void shouldBumpCompleteUpdatedDate_whenItemUpdated() {
    var instanceId = createInstance(client, "An instance", instanceTypeId);
    var holdingId = createHolding(client, instanceId, locationId);
    createItem(client, holdingId, materialTypeId, loanTypeId);
    var dateBefore = completeUpdatedDate(instanceId);

    runQuery("UPDATE item SET created_by = 'some user'");

    assertThat(completeUpdatedDate(instanceId)).isAfter(dateBefore);
  }

  @Test
  @DisplayName("should bump completeUpdatedDate for both instances sharing a bound-with item when it's updated")
  void shouldBumpCompleteUpdatedDate_forBothInstances_whenBoundItemUpdated() {
    var ownerInstanceId = createInstance(client, "Owner instance", instanceTypeId);
    var ownerHoldingId = createHolding(client, ownerInstanceId, locationId);
    var itemId = createItem(client, ownerHoldingId, materialTypeId, loanTypeId);

    var boundInstanceId = createInstance(client, "Bound-to instance", instanceTypeId);
    var boundHoldingId = createHolding(client, boundInstanceId, locationId);
    createBoundWithPart(boundHoldingId, itemId);

    var datesBefore = completeUpdatedDates();

    runQuery("UPDATE item SET created_by = 'some user'");

    var datesAfter = completeUpdatedDates();

    assertThat(datesAfter.keySet()).isEqualTo(datesBefore.keySet());
    assertThat(datesAfter.get(ownerInstanceId)).isAfter(datesBefore.get(ownerInstanceId));
    assertThat(datesAfter.get(boundInstanceId)).isAfter(datesBefore.get(boundInstanceId));
  }

  @Test
  @DisplayName("should bump completeUpdatedDate when a holding is updated directly")
  void shouldBumpCompleteUpdatedDate_whenHoldingUpdated() {
    var instanceId = createInstance(client, "An instance", instanceTypeId);
    createHolding(client, instanceId, locationId);
    var dateBefore = completeUpdatedDate(instanceId);

    runQuery("UPDATE holdings_record SET created_by = 'some user'");

    assertThat(completeUpdatedDate(instanceId)).isAfter(dateBefore);
  }

  @Test
  @DisplayName("should bump completeUpdatedDate when a holding is deleted")
  void shouldBumpCompleteUpdatedDate_whenHoldingDeleted() {
    var instanceId = createInstance(client, "An instance", instanceTypeId);
    var holdingId = createHolding(client, instanceId, locationId);
    var dateBefore = completeUpdatedDate(instanceId);

    get(doDelete(client, ResourcePaths.HOLDINGS + "/" + holdingId));

    assertThat(completeUpdatedDate(instanceId)).isAfter(dateBefore);
  }

  @Test
  @DisplayName("should bump completeUpdatedDate for both source and target instances when a holding is moved")
  void shouldBumpCompleteUpdatedDate_forBothInstances_whenHoldingMovedToAnotherInstance() {
    var sourceInstanceId = createInstance(client, "Source instance", instanceTypeId);
    var targetInstanceId = createInstance(client, "Target instance", instanceTypeId);
    var holdingId = createHolding(client, sourceInstanceId, locationId);

    var datesBefore = completeUpdatedDates();

    var holdingJson = get(doGet(client, ResourcePaths.HOLDINGS + "/" + holdingId)).jsonBody();
    holdingJson.put(INSTANCE_ID_FIELD, targetInstanceId);
    var response = get(doPut(client, ResourcePaths.HOLDINGS + "/" + holdingId, holdingJson));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);

    var datesAfter = completeUpdatedDates();

    assertThat(datesAfter.keySet()).isEqualTo(datesBefore.keySet());
    assertThat(datesAfter.get(sourceInstanceId)).isAfter(datesBefore.get(sourceInstanceId));
    assertThat(datesAfter.get(targetInstanceId)).isAfter(datesBefore.get(targetInstanceId));
  }

  @Test
  @DisplayName("should bump completeUpdatedDate for both source and target instances when an item is moved")
  void shouldBumpCompleteUpdatedDate_forBothInstances_whenItemMovedToAnotherHolding() {
    var sourceInstanceId = createInstance(client, "Source instance", instanceTypeId);
    var sourceHoldingId = createHolding(client, sourceInstanceId, locationId);
    var targetInstanceId = createInstance(client, "Target instance", instanceTypeId);
    var targetHoldingId = createHolding(client, targetInstanceId, locationId);
    var itemId = createItem(client, sourceHoldingId, materialTypeId, loanTypeId);

    var datesBefore = completeUpdatedDates();

    var itemJson = get(doGet(client, ResourcePaths.ITEMS + "/" + itemId)).jsonBody();
    itemJson.put(HOLDINGS_RECORD_ID_FIELD, targetHoldingId);
    var response = get(doPut(client, ResourcePaths.ITEMS + "/" + itemId, itemJson));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);

    var datesAfter = completeUpdatedDates();

    assertThat(datesAfter.keySet()).isEqualTo(datesBefore.keySet());
    assertThat(datesAfter.get(sourceInstanceId)).isAfter(datesBefore.get(sourceInstanceId));
    assertThat(datesAfter.get(targetInstanceId)).isAfter(datesBefore.get(targetInstanceId));
  }

  @Test
  @DisplayName("should bump completeUpdatedDate when an item is deleted")
  void shouldBumpCompleteUpdatedDate_whenItemDeleted() {
    var instanceId = createInstance(client, "An instance", instanceTypeId);
    var holdingId = createHolding(client, instanceId, locationId);
    var itemId = createItem(client, holdingId, materialTypeId, loanTypeId);
    var dateBefore = completeUpdatedDate(instanceId);

    get(doDelete(client, ResourcePaths.ITEMS + "/" + itemId));

    assertThat(completeUpdatedDate(instanceId)).isAfter(dateBefore);
  }

  private static void createBoundWithPart(String holdingsRecordId, String itemId) {
    var request = new JsonObject()
      .put(HOLDINGS_RECORD_ID_FIELD, holdingsRecordId)
      .put(ITEM_ID_FIELD, itemId);

    get(doPost(client, ResourcePaths.BOUND_WITH_PARTS, request));
  }

  private static OffsetDateTime completeUpdatedDate(String instanceId) {
    return completeUpdatedDates().get(instanceId);
  }

  private static Map<String, OffsetDateTime> completeUpdatedDates() {
    var rows = runQuery("SELECT " + ID_FIELD + ", " + COMPLETE_UPDATED_DATE_COLUMN + " FROM instance");
    Map<String, OffsetDateTime> dates = new HashMap<>();

    for (var row : rows) {
      var json = row.toJson();
      var value = json.getString(COMPLETE_UPDATED_DATE_COLUMN);
      dates.put(json.getString(ID_FIELD), value == null ? null : OffsetDateTime.parse(value));
    }

    return dates;
  }
}
