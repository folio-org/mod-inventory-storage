package org.folio.rest.impl;

import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.rest.impl.HoldingsStorageFixtures.createCallNumberType;
import static org.folio.rest.impl.HoldingsStorageFixtures.createHoldingsRecordsSource;
import static org.folio.rest.impl.HoldingsStorageFixtures.createLoanType;
import static org.folio.rest.impl.HoldingsStorageFixtures.createMaterialType;
import static org.folio.rest.impl.InstanceStorageFixtures.createInstance;
import static org.folio.rest.impl.InstanceStorageFixtures.createInstanceType;
import static org.folio.rest.impl.LocationStorageFixtures.createLocation;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.folio.rest.support.builders.HoldingRequestBuilder;
import org.folio.rest.support.messages.HoldingsEventMessageChecks;
import org.folio.rest.support.messages.ItemEventMessageChecks;
import org.folio.rest.tools.utils.OptimisticLockingUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Covers how changes to a holdings record propagate (or deliberately don't) to the items that
 * belong to it: effective location, effective call number components and shelving order, and
 * item-vs-holding metadata/version bumps. Split out of {@link HoldingsStorageIT} because this
 * cluster of tests is large and shares a distinct "holding change -> item side effect" concern.
 *
 * <p>Also covers the {@code effectiveLocationId} trigger logic itself (the {@code
 * update_effective_location}/{@code update_item_references} triggers on {@code holdings_record}
 * and {@code item}, see {@code itemEffectiveLocation.sql}) across the full matrix of permanent
 * vs. temporary location permutations at both the holding and item level - merged in from the
 * legacy {@code ItemEffectiveLocationTest} since both concerns are "how a holding's location
 * change reaches its items".
 */
class HoldingsItemPropagationIT extends BaseIntegrationTest {

  // The exact reference-data ids CallNumberUtils/tests key off; cannot be fresh/random ids.
  private static final String LC_CALL_NUMBER_TYPE_ID = "95467209-6d7b-468b-94df-0f5d7ad2747d";
  private static final String DEWEY_CALL_NUMBER_TYPE_ID = "03dd64d0-5626-4ecd-8ece-4531e0069f35";
  private static final String NLM_CALL_NUMBER_TYPE_ID = "054d460d-d6b9-4469-9e37-7a78a2266655";
  private static final String MOYS_CALL_NUMBER_TYPE_ID = "828ae637-dfa3-4265-a1af-5279c436edff";
  private static final String EFFECTIVE_CALL_NUMBER_COMPONENTS_KEY = "effectiveCallNumberComponents";
  private static final String EFFECTIVE_LOCATION_ID_KEY = "effectiveLocationId";
  private static final String PERMANENT_LOCATION_ID_KEY = "permanentLocationId";
  private static final String TEMPORARY_LOCATION_ID_KEY = "temporaryLocationId";
  private static final String VERSION_KEY = "_version";

  private static String instanceTypeId;
  private static String materialTypeId;
  private static String loanTypeId;
  private static String mainLibraryLocationId;
  private static String annexLibraryLocationId;
  private static String onlineLocationId;
  private static String secondFloorLocationId;
  private static String thirdFloorLocationId;
  private static String fourthFloorLocationId;
  private static String lcCallNumberTypeId;
  private static String deweyCallNumberTypeId;

  private final HoldingsEventMessageChecks holdingsMessageChecks
    = new HoldingsEventMessageChecks(KAFKA_CONSUMER, wm.baseUrl());
  private final ItemEventMessageChecks itemMessageChecks
    = new ItemEventMessageChecks(KAFKA_CONSUMER, wm.baseUrl());

  @BeforeAll
  static void seedReferenceData() {
    instanceTypeId = createInstanceType(client);
    materialTypeId = createMaterialType(client);
    loanTypeId = createLoanType(client);
    mainLibraryLocationId = createLocation(client);
    annexLibraryLocationId = createLocation(client);
    onlineLocationId = createLocation(client);
    secondFloorLocationId = createLocation(client);
    thirdFloorLocationId = createLocation(client);
    fourthFloorLocationId = createLocation(client);
    lcCallNumberTypeId = createCallNumberType(client, LC_CALL_NUMBER_TYPE_ID, "Library of Congress classification");
    deweyCallNumberTypeId = createCallNumberType(client, DEWEY_CALL_NUMBER_TYPE_ID, "Dewey Decimal classification");
    createCallNumberType(client, NLM_CALL_NUMBER_TYPE_ID, "NLM classification");
    createCallNumberType(client, MOYS_CALL_NUMBER_TYPE_ID, "MOYS classification");
  }

  @BeforeEach
  void clearHoldings() {
    runQuery("TRUNCATE TABLE instance, holdings_record, item CASCADE");
  }

  @AfterEach
  void resetHridSequenceAndOptimisticLockingOverride() {
    setHoldingsSequence(1);
    OptimisticLockingUtil.configureAllowSuppressOptimisticLocking(Map.of());
  }

  @Test
  @DisplayName("should change the effective location when the permanent location changes and no "
               + "temporary location is set")
  void shouldChangeEffectiveLocation_whenPermanentLocationChangesAndNoTemporarySet() {
    var holding = createHolding(holdingRequest(createInstanceRecord()));
    assertThat(holding.getString(EFFECTIVE_LOCATION_ID_KEY)).isEqualTo(mainLibraryLocationId);

    holding.put(PERMANENT_LOCATION_ID_KEY, annexLibraryLocationId);
    updateHoldingExpectNoContent(holding);

    var updated = getHoldingById(holding.getString("id"));
    assertThat(updated.getString(EFFECTIVE_LOCATION_ID_KEY)).isEqualTo(annexLibraryLocationId);
  }

  @Test
  @DisplayName("should not change the effective location when the permanent location changes but a "
               + "temporary location is set")
  void shouldNotChangeEffectiveLocation_whenPermanentLocationChangesButTemporarySet() {
    var holding = createHolding(holdingRequest(createInstanceRecord())
      .withTemporaryLocation(UUID.fromString(annexLibraryLocationId)));
    assertThat(holding.getString(EFFECTIVE_LOCATION_ID_KEY)).isEqualTo(annexLibraryLocationId);

    holding.put(PERMANENT_LOCATION_ID_KEY, secondFloorLocationId);
    updateHoldingExpectNoContent(holding);

    var updated = getHoldingById(holding.getString("id"));
    assertThat(updated.getString(EFFECTIVE_LOCATION_ID_KEY)).isEqualTo(annexLibraryLocationId);
  }

  @Test
  @DisplayName("should change the effective location when the temporary location is set, changed or removed")
  void shouldChangeEffectiveLocation_whenTemporaryLocationSetOrRemoved() {
    var holding = createHolding(holdingRequest(createInstanceRecord()));
    final var holdingId = holding.getString("id");
    assertThat(holding.getString(EFFECTIVE_LOCATION_ID_KEY)).isEqualTo(mainLibraryLocationId);

    holding.put(TEMPORARY_LOCATION_ID_KEY, annexLibraryLocationId);
    updateHoldingExpectNoContent(holding);
    var afterFirstUpdate = getHoldingById(holdingId);
    assertThat(afterFirstUpdate.getString(EFFECTIVE_LOCATION_ID_KEY)).isEqualTo(annexLibraryLocationId);

    afterFirstUpdate.put(TEMPORARY_LOCATION_ID_KEY, secondFloorLocationId);
    updateHoldingExpectNoContent(afterFirstUpdate);
    var afterSecondUpdate = getHoldingById(holdingId);
    assertThat(afterSecondUpdate.getString(EFFECTIVE_LOCATION_ID_KEY)).isEqualTo(secondFloorLocationId);

    afterSecondUpdate.remove(TEMPORARY_LOCATION_ID_KEY);
    updateHoldingExpectNoContent(afterSecondUpdate);
    assertThat(getHoldingById(holdingId).getString(EFFECTIVE_LOCATION_ID_KEY)).isEqualTo(mainLibraryLocationId);
  }

  @CsvSource({
    "PN 12 A6,PN12 .A6,,PN2 .A6,,,,,",
    "PN 12 A6 V 13 NO 12 41999,PN2 .A6 v.3 no.2 1999,,PN2 .A6,v. 3,no. 2,1999,,",
    "PN 12 A6 41999,PN12 .A6 41999,,PN2 .A6 1999,,,,,",
    "PN 12 A6 41999 CD,PN12 .A6 41999 CD,,PN2 .A6 1999,,,,,CD",
    "PN 12 A6 41999 12,PN12 .A6 41999 C.12,,PN2 .A6 1999,,,,2,",
    "PN 12 A69 41922 12,PN12 .A69 41922 C.12,,PN2 .A69,,,1922,2,",
    "PN 12 A69 NO 12,PN12 .A69 NO.12,,PN2 .A69,,no. 2,,,",
    "PN 12 A69 NO 12 41922 11,PN12 .A69 NO.12 41922 C.11,,PN2 .A69,,no. 2,1922,1,",
    "PN 12 A69 NO 12 41922 12,PN12 .A69 NO.12 41922 C.12,Wordsworth,PN2 .A69,,no. 2,1922,2,",
    "PN 12 A69 V 11 NO 11,PN12 .A69 V.11 NO.11,,PN2 .A69,v.1,no. 1,,,",
    "PN 12 A69 V 11 NO 11 +,PN12 .A69 V.11 NO.11 +,Over,PN2 .A69,v.1,no. 1,,,+",
    "PN 12 A69 V 11 NO 11 41921,PN12 .A69 V.11 NO.11 41921,,PN2 .A69,v.1,no. 1,1921,,",
    "PR 49199.3 41920 L33 41475 A6,PR 49199.3 41920 .L33 41475 .A6,,PR9199.3 1920 .L33 1475 .A6,,,,,",
    "PQ 42678 K26 P54,PQ 42678 .K26 P54,,PQ2678.K26 P54,,,,,",
    "PQ 48550.21 R57 V5 41992,PQ 48550.21 .R57 V15 41992,,PQ8550.21.R57 V5 1992,,,,,",
    "PQ 48550.21 R57 V5 41992,PQ 48550.21 .R57 V15 41992,,PQ8550.21.R57 V5,,,1992,,",
    "PR 3919 L33 41990,PR 3919 .L33 41990,,PR919 .L33 1990,,,,,",
    "PR 49199 A39,PR 49199 .A39,,PR9199 .A39,,,,,",
    "PR 49199.48 B3,PR 49199.48 .B3,,PR9199.48 .B3,,,,,"
  })
  @ParameterizedTest
  @DisplayName("should update item shelving order when the holding's call number changes")
  void shouldUpdateItemShelvingOrder_whenHoldingCallNumberChanges(
    String desiredShelvingOrder, String initiallyDesiredShelvesOrder, String prefix, String callNumber,
    String volume, String enumeration, String chronology, String copy, String suffix) {
    var instanceId = createInstanceRecord();
    var holding = createHolding(holdingRequest(instanceId).withCallNumber("testCallNumber"));
    var holdingId = holding.getString("id");
    var itemIds = new String[] {
      createItem(complexItemRequest(holdingId, prefix, suffix, volume, enumeration, chronology, copy)).getString("id"),
      createItem(complexItemRequest(holdingId, prefix, suffix, volume, enumeration, chronology, copy)).getString("id")
    };
    assertItemsHaveCallNumber(itemIds, "testCallNumber");

    holding.put("callNumber", callNumber);
    updateHoldingExpectNoContent(holding);

    assertItemsHaveCallNumber(itemIds, callNumber);
  }

  @Test
  @DisplayName("should update the effective call number for all items when the holding's call number changes")
  void shouldUpdateItemEffectiveCallNumber_forAllItems_whenHoldingCallNumberChanges() {
    var holding = createHolding(holdingRequest(createInstanceRecord()).withCallNumber("testCallNumber"));
    var itemIds = new String[] {createItem(holding.getString("id")).getString("id"),
                                createItem(holding.getString("id")).getString("id")};
    assertItemsHaveCallNumber(itemIds, "testCallNumber");

    holding.put("callNumber", "updatedCallNumber");
    updateHoldingExpectNoContent(holding);

    assertItemsHaveCallNumber(itemIds, "updatedCallNumber");
  }

  @Test
  @DisplayName("should remove the item effective call number when the holding's call number is removed")
  void shouldRemoveItemEffectiveCallNumber_whenHoldingCallNumberRemoved() {
    var holding = createHolding(holdingRequest(createInstanceRecord()).withCallNumber("testCallNumber"));
    var itemId = createItem(holding.getString("id")).getString("id");
    assertItemsHaveCallNumber(new String[] {itemId}, "testCallNumber");

    holding.remove("callNumber");
    updateHoldingExpectNoContent(holding);

    assertThat(getItemById(itemId).getJsonObject(EFFECTIVE_CALL_NUMBER_COMPONENTS_KEY)
      .containsKey("callNumber")).isFalse();
  }

  @Test
  @DisplayName("should not supersede an item-level call number when the holding's call number is updated")
  void shouldNotSupersedeItemLevelCallNumber_whenHoldingCallNumberUpdated() {
    var holding = createHolding(holdingRequest(createInstanceRecord()).withCallNumber("holdingsCallNumber"));
    var item = createItem(itemRequest(holding.getString("id")).put("itemLevelCallNumber", "itemLevelCallNumber"));
    assertThat(getEffectiveComponent(item, "callNumber")).isEqualTo("itemLevelCallNumber");

    holding.put("callNumber", "updatedHoldingCallNumber");
    updateHoldingExpectNoContent(holding);

    assertThat(getEffectiveComponent(getItemById(item.getString("id")), "callNumber")).isEqualTo("itemLevelCallNumber");
  }

  @Test
  @DisplayName("should update the effective call number suffix for all items when the holding's suffix changes")
  void shouldUpdateItemEffectiveCallNumberSuffix_forAllItems_whenHoldingSuffixChanges() {
    var holding = createHolding(holdingRequest(createInstanceRecord()).withCallNumberSuffix("testCallNumberSuffix"));
    var itemIds = new String[] {createItem(holding.getString("id")).getString("id"),
                                createItem(holding.getString("id")).getString("id")};
    assertItemsHaveEffectiveComponent(itemIds, "suffix", "testCallNumberSuffix");

    holding.put("callNumberSuffix", "updatedCallNumberSuffix");
    updateHoldingExpectNoContent(holding);

    assertItemsHaveEffectiveComponent(itemIds, "suffix", "updatedCallNumberSuffix");
  }

  @Test
  @DisplayName("should remove the item effective call number suffix when the holding's suffix is removed")
  void shouldRemoveItemEffectiveCallNumberSuffix_whenHoldingSuffixRemoved() {
    var holding = createHolding(holdingRequest(createInstanceRecord()).withCallNumberSuffix("testCallNumberSuffix"));
    var itemId = createItem(holding.getString("id")).getString("id");
    assertItemsHaveEffectiveComponent(new String[] {itemId}, "suffix", "testCallNumberSuffix");

    holding.remove("callNumberSuffix");
    updateHoldingExpectNoContent(holding);

    assertThat(getEffectiveComponent(getItemById(itemId), "suffix")).isNull();
  }

  @Test
  @DisplayName("should not supersede an item-level call number suffix when the holding's suffix is updated")
  void shouldNotSupersedeItemLevelCallNumberSuffix_whenHoldingSuffixUpdated() {
    var holding = createHolding(holdingRequest(createInstanceRecord())
      .withCallNumberSuffix("holdingsCallNumberSuffix"));
    var item = createItem(itemRequest(holding.getString("id"))
      .put("itemLevelCallNumberSuffix", "itemLevelCallNumberSuffix"));
    assertThat(getEffectiveComponent(item, "suffix")).isEqualTo("itemLevelCallNumberSuffix");

    holding.put("callNumberSuffix", "updatedHoldingCallNumberSuffix");
    updateHoldingExpectNoContent(holding);

    assertThat(getEffectiveComponent(getItemById(item.getString("id")), "suffix"))
      .isEqualTo("itemLevelCallNumberSuffix");
  }

  @Test
  @DisplayName("should update the effective call number prefix for all items when the holding's prefix changes")
  void shouldUpdateItemEffectiveCallNumberPrefix_forAllItems_whenHoldingPrefixChanges() {
    var holding = createHolding(holdingRequest(createInstanceRecord()).withCallNumberPrefix("testCallNumberPrefix"));
    var itemIds = new String[] {createItem(holding.getString("id")).getString("id"),
                                createItem(holding.getString("id")).getString("id")};
    assertItemsHaveEffectiveComponent(itemIds, "prefix", "testCallNumberPrefix");

    holding.put("callNumberPrefix", "updatedCallNumberPrefix");
    updateHoldingExpectNoContent(holding);

    assertItemsHaveEffectiveComponent(itemIds, "prefix", "updatedCallNumberPrefix");
  }

  @Test
  @DisplayName("should remove the item effective call number prefix when the holding's prefix is removed")
  void shouldRemoveItemEffectiveCallNumberPrefix_whenHoldingPrefixRemoved() {
    var holding = createHolding(holdingRequest(createInstanceRecord()).withCallNumberPrefix("testCallNumberPrefix"));
    var itemId = createItem(holding.getString("id")).getString("id");
    assertItemsHaveEffectiveComponent(new String[] {itemId}, "prefix", "testCallNumberPrefix");

    holding.remove("callNumberPrefix");
    updateHoldingExpectNoContent(holding);

    assertThat(getEffectiveComponent(getItemById(itemId), "prefix")).isNull();
  }

  @Test
  @DisplayName("should not supersede an item-level call number prefix when the holding's prefix is updated")
  void shouldNotSupersedeItemLevelCallNumberPrefix_whenHoldingPrefixUpdated() {
    var holding = createHolding(holdingRequest(createInstanceRecord())
      .withCallNumberPrefix("holdingsCallNumberPrefix"));
    var item = createItem(itemRequest(holding.getString("id"))
      .put("itemLevelCallNumberPrefix", "itemLevelCallNumberPrefix"));
    assertThat(getEffectiveComponent(item, "prefix")).isEqualTo("itemLevelCallNumberPrefix");

    holding.put("callNumberPrefix", "updatedHoldingCallNumberPrefix");
    updateHoldingExpectNoContent(holding);

    assertThat(getEffectiveComponent(getItemById(item.getString("id")), "prefix"))
      .isEqualTo("itemLevelCallNumberPrefix");
  }

  @Test
  @DisplayName("should only update items belonging to the updated holding, not items on another holding")
  void shouldLimitCallNumberUpdate_toOwnHolding_notOtherHoldings() {
    var instanceId = createInstanceRecord();
    var firstHolding = createHolding(holdingRequestWithAllCallNumberComponents(
      instanceId, "firstTestCallNumber", "firstTestCallNumberPrefix", "firstTestCallNumberSuffix"));
    var secondHolding = createHolding(holdingRequestWithAllCallNumberComponents(
      instanceId, "secondTestCallNumber", "secondTestCallNumberPrefix", "secondTestCallNumberSuffix"));
    var firstItemId = createItem(firstHolding.getString("id")).getString("id");
    var secondItemId = createItem(secondHolding.getString("id")).getString("id");

    assertItemsHaveExpectedCallNumberComponents(firstItemId,
      "firstTestCallNumber", "firstTestCallNumberPrefix", "firstTestCallNumberSuffix");
    assertItemsHaveExpectedCallNumberComponents(secondItemId,
      "secondTestCallNumber", "secondTestCallNumberPrefix", "secondTestCallNumberSuffix");

    firstHolding.put("callNumber", "updatedFirstCallNumber")
      .put("callNumberPrefix", "updatedFirstCallNumberPrefix")
      .put("callNumberSuffix", "updatedFirstCallNumberSuffix");
    updateHoldingExpectNoContent(firstHolding);

    assertItemsHaveExpectedCallNumberComponents(firstItemId,
      "updatedFirstCallNumber", "updatedFirstCallNumberPrefix", "updatedFirstCallNumberSuffix");
    assertItemsHaveExpectedCallNumberComponents(secondItemId,
      "secondTestCallNumber", "secondTestCallNumberPrefix", "secondTestCallNumberSuffix");
  }

  @Test
  @DisplayName("should update the item's effective call number components when the holding's call number type changes")
  void shouldUpdateItemEffectiveComponents_whenHoldingCallNumberTypeChanges() {
    var effectiveComponents = createHoldingAndItemWithAllCallNumberComponents();

    effectiveComponents.holding().put("callNumberTypeId", deweyCallNumberTypeId);
    updateHoldingExpectNoContent(effectiveComponents.holding());
    var updatedItem = getItemById(effectiveComponents.itemId());

    assertItemHasEffectiveComponents(updatedItem, "2", "testCallNumber", "testCallNumberPrefix",
      "testCallNumberSuffix", deweyCallNumberTypeId);
    assertThat(updatedItem.getString(EFFECTIVE_LOCATION_ID_KEY)).isEqualTo(mainLibraryLocationId);
  }

  @Test
  @DisplayName("should update the item's effective call number components when the holding's call number "
               + "prefix changes")
  void shouldUpdateItemEffectiveComponents_whenHoldingCallNumberPrefixChanges() {
    var effectiveComponents = createHoldingAndItemWithAllCallNumberComponents();

    effectiveComponents.holding().put("callNumberPrefix", "updatedCallNumberPrefix");
    updateHoldingExpectNoContent(effectiveComponents.holding());
    var updatedItem = getItemById(effectiveComponents.itemId());

    assertItemHasEffectiveComponents(updatedItem, "2", "testCallNumber", "updatedCallNumberPrefix",
      "testCallNumberSuffix", lcCallNumberTypeId);
  }

  @Test
  @DisplayName("should update the item's effective call number components when the holding's call number "
               + "suffix changes")
  void shouldUpdateItemEffectiveComponents_whenHoldingCallNumberSuffixChanges() {
    var effectiveComponents = createHoldingAndItemWithAllCallNumberComponents();

    effectiveComponents.holding().put("callNumberSuffix", "updatedCallNumberSuffix");
    updateHoldingExpectNoContent(effectiveComponents.holding());
    var updatedItem = getItemById(effectiveComponents.itemId());

    assertItemHasEffectiveComponents(updatedItem, "2", "testCallNumber", "testCallNumberPrefix",
      "updatedCallNumberSuffix", lcCallNumberTypeId);
  }

  @Test
  @DisplayName("should update the item's effective call number components when the holding's call number changes")
  void shouldUpdateItemEffectiveComponents_whenHoldingCallNumberChanges() {
    var effectiveComponents = createHoldingAndItemWithAllCallNumberComponents();

    effectiveComponents.holding().put("callNumber", "updatedCallNumber");
    updateHoldingExpectNoContent(effectiveComponents.holding());
    var updatedItem = getItemById(effectiveComponents.itemId());

    assertItemHasEffectiveComponents(updatedItem, "2", "updatedCallNumber", "testCallNumberPrefix",
      "testCallNumberSuffix", lcCallNumberTypeId);
  }

  @Test
  @DisplayName("should update the item's effective location when the holding's permanent location changes")
  void shouldUpdateItemEffectiveComponents_whenHoldingPermanentLocationChanges() {
    var effectiveComponents = createHoldingAndItemWithAllCallNumberComponents();

    effectiveComponents.holding().put(PERMANENT_LOCATION_ID_KEY, annexLibraryLocationId);
    updateHoldingExpectNoContent(effectiveComponents.holding());
    var updatedItem = getItemById(effectiveComponents.itemId());

    assertThat(updatedItem.getString(EFFECTIVE_LOCATION_ID_KEY)).isEqualTo(annexLibraryLocationId);
    assertItemHasEffectiveComponents(updatedItem, "2", "testCallNumber", "testCallNumberPrefix",
      "testCallNumberSuffix", lcCallNumberTypeId);
  }

  @Test
  @DisplayName("should update the item's effective location when the holding's temporary location changes")
  void shouldUpdateItemEffectiveComponents_whenHoldingTemporaryLocationChanges() {
    var effectiveComponents = createHoldingAndItemWithAllCallNumberComponents();

    effectiveComponents.holding().put(TEMPORARY_LOCATION_ID_KEY, annexLibraryLocationId);
    updateHoldingExpectNoContent(effectiveComponents.holding());
    var updatedItem = getItemById(effectiveComponents.itemId());

    assertThat(updatedItem.getString(EFFECTIVE_LOCATION_ID_KEY)).isEqualTo(annexLibraryLocationId);
    assertItemHasEffectiveComponents(updatedItem, "2", "testCallNumber", "testCallNumberPrefix",
      "testCallNumberSuffix", lcCallNumberTypeId);
  }

  @Test
  @DisplayName("should update the item's call numbers and effective location when several holding fields change")
  void shouldUpdateItemEffectiveComponents_whenHoldingCallNumbersAndLocationsChange() {
    var effectiveComponents = createHoldingAndItemWithAllCallNumberComponents();

    effectiveComponents.holding()
      .put("callNumber", "updatedCallNumber")
      .put("callNumberPrefix", "updatedCallNumberPrefix")
      .put("callNumberSuffix", "updatedCallNumberSuffix")
      .put("callNumberTypeId", deweyCallNumberTypeId)
      .put(PERMANENT_LOCATION_ID_KEY, annexLibraryLocationId)
      .putNull(TEMPORARY_LOCATION_ID_KEY);
    updateHoldingExpectNoContent(effectiveComponents.holding());
    var updatedItem = getItemById(effectiveComponents.itemId());

    assertThat(updatedItem.getString(EFFECTIVE_LOCATION_ID_KEY)).isEqualTo(annexLibraryLocationId);
    assertItemHasEffectiveComponents(updatedItem, "2", "updatedCallNumber", "updatedCallNumberPrefix",
      "updatedCallNumberSuffix", deweyCallNumberTypeId);
  }

  @Test
  @DisplayName("should update the item's effective call number and metadata when the holding's call number "
               + "and notes change")
  void shouldUpdateItemEffectiveComponentsAndNote_whenHoldingCallNumberAndNotesChange() {
    var effectiveComponents = createHoldingAndItemWithAllCallNumberComponents();

    effectiveComponents.holding()
      .put("callNumber", "updatedCallNumber")
      .put("notes", new JsonArray().add(new JsonObject().put("note", "test note").put("staffOnly", false)));
    updateHoldingExpectNoContent(effectiveComponents.holding());
    var updatedItem = getItemById(effectiveComponents.itemId());

    assertThat(getHoldingById(effectiveComponents.holding().getString("id"))
      .getJsonArray("notes").getJsonObject(0).getString("note")).isEqualTo("test note");
    assertItemHasEffectiveComponents(updatedItem, "2", "updatedCallNumber", "testCallNumberPrefix",
      "testCallNumberSuffix", lcCallNumberTypeId);
  }

  @Test
  @DisplayName("should update the item's effective call number when the holding's call number is deleted")
  void shouldUpdateItemEffectiveComponents_whenHoldingCallNumberDeleted() {
    var effectiveComponents = createHoldingAndItemWithAllCallNumberComponents();

    effectiveComponents.holding().remove("callNumber");
    updateHoldingExpectNoContent(effectiveComponents.holding());
    var updatedItem = getItemById(effectiveComponents.itemId());

    assertItemHasEffectiveComponents(updatedItem, "2", null, "testCallNumberPrefix",
      "testCallNumberSuffix", lcCallNumberTypeId);
  }

  @Test
  @DisplayName("should not update an item when a holding field unrelated to items changes")
  void shouldNotUpdateItem_whenHoldingFieldUnrelatedToItemChanges() {
    var holding = createHolding(holdingRequest(createInstanceRecord()).withCallNumberPrefix("call number prefix"));
    var item = createItem(holding.getString("id"));
    assertThat(getEffectiveComponent(item, "prefix")).isEqualTo("call number prefix");

    holding.put("copyNumber", "copy number");
    updateHoldingExpectNoContent(holding);

    assertThat(getHoldingById(holding.getString("id")).getString("copyNumber")).isEqualTo("copy number");
    assertThat(getItemById(item.getString("id")).getString(VERSION_KEY)).isEqualTo("1");
  }

  @Test
  @DisplayName("should not update an item when the holding is updated with the same related fields")
  void shouldNotUpdateItem_whenHoldingUpdatedWithSameRelatedFields() {
    var holding = createHolding(holdingRequest(createInstanceRecord())
      .withCallNumberPrefix("call number prefix").create().put("administrativeNotes", new JsonArray().add("note")));
    var item = createItem(holding.getString("id"));
    assertThat(getEffectiveComponent(item, "prefix")).isEqualTo("call number prefix");

    holding.put("administrativeNotes", new JsonArray().add("note upd"));
    updateHoldingExpectNoContent(holding);

    assertThat(getItemById(item.getString("id")).getString(VERSION_KEY)).isEqualTo("1");
  }

  @Test
  @DisplayName("should update an item when a synchronous batch changes a related holding field")
  void shouldUpdateItem_whenSyncBatchChangesRelatedField() {
    var holding = createHolding(holdingRequest(createInstanceRecord()).withCallNumberPrefix("call number prefix"));
    var item = createItem(holding.getString("id"));
    assertThat(item.getString(EFFECTIVE_LOCATION_ID_KEY)).isEqualTo(mainLibraryLocationId);

    holding.put(VERSION_KEY, 1).put(PERMANENT_LOCATION_ID_KEY, annexLibraryLocationId);
    assertThat(syncBatchUpsert(holding).status()).isEqualTo(SC_CREATED);

    var updatedItem = getItemById(item.getString("id"));
    assertThat(updatedItem.getString(VERSION_KEY)).isEqualTo("2");
    assertThat(updatedItem.getString(EFFECTIVE_LOCATION_ID_KEY)).isEqualTo(annexLibraryLocationId);
  }

  @Test
  @DisplayName("should not update an item when a synchronous batch changes an unrelated holding field")
  void shouldNotUpdateItem_whenSyncBatchChangesUnrelatedField() {
    var holding = createHolding(holdingRequest(createInstanceRecord()).withCallNumberPrefix("call number prefix"));
    var item = createItem(holding.getString("id"));

    holding.put(VERSION_KEY, 1).put("administrativeNotes", new JsonArray().add("new note"));
    assertThat(syncBatchUpsert(holding).status()).isEqualTo(SC_CREATED);

    assertThat(getItemById(item.getString("id")).getString(VERSION_KEY)).isEqualTo("1");
  }

  @Test
  @DisplayName("should update an item when an unsafe synchronous batch changes a related holding field")
  void shouldUpdateItem_whenUnsafeBatchChangesRelatedField() {
    OptimisticLockingUtil.configureAllowSuppressOptimisticLocking(
      Map.of(OptimisticLockingUtil.DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING, "9999-12-31T23:59:59Z"));
    var holding = createHolding(holdingRequest(createInstanceRecord()).withPermanentLocation(
      UUID.fromString(annexLibraryLocationId)).withCallNumberPrefix("call number prefix"));
    var item = createItem(holding.getString("id"));
    assertThat(item.getString(EFFECTIVE_LOCATION_ID_KEY)).isEqualTo(annexLibraryLocationId);

    holding.put("callNumber", "call number");
    assertThat(syncBatchUnsafe(holding).status()).isEqualTo(SC_CREATED);

    var updatedItem = getItemById(item.getString("id"));
    assertThat(updatedItem.getString(VERSION_KEY)).isEqualTo("2");
    assertThat(updatedItem.getString("effectiveShelvingOrder")).isEqualTo("call number");
    assertThat(getEffectiveComponent(updatedItem, "callNumber")).isEqualTo("call number");
  }

  @Test
  @DisplayName("should not update an item when an unsafe synchronous batch changes an unrelated holding field")
  void shouldNotUpdateItem_whenUnsafeBatchChangesUnrelatedField() {
    OptimisticLockingUtil.configureAllowSuppressOptimisticLocking(
      Map.of(OptimisticLockingUtil.DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING, "9999-12-31T23:59:59Z"));
    var holding = createHolding(holdingRequest(createInstanceRecord()).withPermanentLocation(
      UUID.fromString(annexLibraryLocationId)).withCallNumberPrefix("call number prefix"));
    var item = createItem(holding.getString("id"));

    holding.put("administrativeNotes", new JsonArray().add("admin note"));
    assertThat(syncBatchUnsafe(holding).status()).isEqualTo(SC_CREATED);

    assertThat(getItemById(item.getString("id")).getString(VERSION_KEY)).isEqualTo("1");
  }

  @Test
  @DisplayName("should not update an item when a patch changes an unrelated holding field")
  void shouldNotUpdateItem_whenPatchChangesUnrelatedField() {
    var holding = createHolding(withHrid(holdingRequest(createInstanceRecord()), "hrid")
      .withPermanentLocation(UUID.fromString(annexLibraryLocationId)));
    var item = createItem(holding.getString("id"));

    var patch = new JsonObject().put("id", holding.getString("id")).put(VERSION_KEY, 1)
      .put(PERMANENT_LOCATION_ID_KEY, annexLibraryLocationId)
      .put("administrativeNotes", new JsonArray().add("new note"));
    assertThat(patchHolding(holding.getString("id"), patch).status()).isEqualTo(SC_NO_CONTENT);

    assertThat(getItemById(item.getString("id")).getString(VERSION_KEY)).isEqualTo("1");
  }

  @ParameterizedTest
  @MethodSource("relatedHoldingFields")
  @DisplayName("should update an item when a patch changes a related holding field")
  void shouldUpdateItem_whenPatchChangesRelatedField(String holdingField, String initialValue, String newValue,
                                                     String expectedComponentSubField) {
    var holdingId = createHoldingWithField(holdingField, initialValue);
    var item = createItem(holdingId);
    assertThat(item.getString(VERSION_KEY)).isEqualTo("1");

    var patch = new JsonObject().put("id", holdingId).put(VERSION_KEY, 1)
      .put(PERMANENT_LOCATION_ID_KEY, mainLibraryLocationId).put(holdingField, newValue);
    assertThat(patchHolding(holdingId, patch).status()).isEqualTo(SC_NO_CONTENT);

    assertItemUpdated(item.getString("id"), newValue, expectedComponentSubField);
  }

  @ParameterizedTest
  @MethodSource("relatedHoldingFields")
  @DisplayName("should update an item when a synchronous batch upsert changes a related holding field")
  void shouldUpdateItem_whenSyncBatchUpsertChangesRelatedField(String holdingField, String initialValue,
                                                               String newValue, String expectedComponentSubField) {
    var holdingId = createHoldingWithField(holdingField, initialValue);
    var item = createItem(holdingId);

    var holding = getHoldingById(holdingId).put(VERSION_KEY, 1).put(holdingField, newValue);
    assertThat(syncBatchUpsert(holding).status()).isEqualTo(SC_CREATED);

    assertItemUpdated(item.getString("id"), newValue, expectedComponentSubField);
  }

  @Test
  @DisplayName("should upsert a holding via a synchronous batch multiple times and update its item")
  void shouldUpsertHolding_viaSynchronousBatchMultipleTimes_andUpdateItem() {
    var holding = createHolding(holdingRequest(createInstanceRecord()));
    var holdingId = holding.getString("id");
    final var item = createItem(holdingId);

    holding.put(VERSION_KEY, 1).put("callNumber", "call number");
    assertThat(syncBatchUpsert(holding).status()).isEqualTo(SC_CREATED);
    var afterFirstUpdate = getHoldingById(holdingId);
    assertThat(afterFirstUpdate.getString(VERSION_KEY)).isEqualTo("2");

    afterFirstUpdate.put(PERMANENT_LOCATION_ID_KEY, annexLibraryLocationId);
    assertThat(syncBatchUpsert(afterFirstUpdate).status()).isEqualTo(SC_CREATED);

    var updatedItem = getItemById(item.getString("id"));
    // item version bumps once per holding update that touches an item-relevant field: once for
    // the callNumber change above, once for the permanentLocationId change here
    assertThat(updatedItem.getString(VERSION_KEY)).isEqualTo("3");
    assertThat(updatedItem.getString(EFFECTIVE_LOCATION_ID_KEY)).isEqualTo(annexLibraryLocationId);
  }

  // -- effective-location trigger matrix (merged from the legacy ItemEffectiveLocationTest) --

  @ParameterizedTest
  @MethodSource("itemUpdateEffectiveLocationParams")
  @DisplayName("should calculate item effective location on item update across permanent/temporary "
               + "location permutations")
  void shouldCalculateItemEffectiveLocation_onItemUpdate(
    PermTemp holdingLoc, PermTemp itemStartLoc, PermTemp itemEndLoc) {
    var holdingId = createHolding(holdingRequestWithLocations(createInstanceRecord(), holdingLoc)).getString("id");

    var item = createItem(itemRequestWithLocations(holdingId, itemStartLoc));
    assertThat(item.getString(EFFECTIVE_LOCATION_ID_KEY)).isEqualTo(effectiveLocation(holdingLoc, itemStartLoc));

    setPermanentTemporaryLocation(item, itemEndLoc);
    updateItemExpectNoContent(item);

    var updatedItem = getItemById(item.getString("id"));
    assertThat(updatedItem.getString(EFFECTIVE_LOCATION_ID_KEY)).isEqualTo(effectiveLocation(holdingLoc, itemEndLoc));
  }

  @ParameterizedTest
  @MethodSource("holdingUpdateEffectiveLocationParams")
  @DisplayName("should calculate item effective location on holding update across permanent/temporary "
               + "location permutations")
  void shouldCalculateItemEffectiveLocation_onHoldingUpdate(
    PermTemp itemLoc, PermTemp holdingStartLoc, PermTemp holdingEndLoc) {
    var instanceId = createInstanceRecord();
    var holding = createHolding(holdingRequestWithLocations(instanceId, holdingStartLoc));
    var holdingId = holding.getString("id");

    var item = createItem(itemRequestWithLocations(holdingId, itemLoc));
    assertThat(item.getString(EFFECTIVE_LOCATION_ID_KEY)).isEqualTo(effectiveLocation(holdingStartLoc, itemLoc));

    var holdingToUpdate = holding.copy();
    setPermanentTemporaryLocation(holdingToUpdate, holdingEndLoc);
    var locationsChanged = !locationsEqual(holding, holdingToUpdate);
    updateHoldingExpectNoContent(holdingToUpdate);

    var updatedItem = getItemById(item.getString("id"));
    assertThat(updatedItem.getString(EFFECTIVE_LOCATION_ID_KEY)).isEqualTo(effectiveLocation(holdingEndLoc, itemLoc));

    var updatedHolding = getHoldingById(holdingId);
    if (locationsChanged) {
      // the item's own holding doesn't change here, so old and new instanceId are the same;
      // pass both explicitly rather than resolving via TestBase.holdingsClient, which is only
      // initialized on the legacy rest.api stack, not the shared *IT verticle.
      itemMessageChecks.updatedMessagePublished(item, updatedItem, instanceId, instanceId);
    }
    holdingsMessageChecks.updatedMessagePublished(holding, updatedHolding);
  }

  @Test
  @DisplayName("should fall back all items to the holding's permanent location when its temporary "
               + "location is removed")
  void shouldFallBackAllItemsToHoldingPermanentLocation_whenHoldingTemporaryLocationRemoved() {
    var holding = createHolding(holdingRequestWithLocations(createInstanceRecord(),
      new PermTemp(mainLibraryLocationId, annexLibraryLocationId)));
    var holdingId = holding.getString("id");
    var itemIds = new String[] {
      createItem(itemRequest(holdingId)).getString("id"),
      createItem(itemRequest(holdingId)).getString("id"),
      createItem(itemRequest(holdingId)).getString("id")};
    for (var itemId : itemIds) {
      assertThat(getItemById(itemId).getString(EFFECTIVE_LOCATION_ID_KEY)).isEqualTo(annexLibraryLocationId);
    }

    holding.remove(TEMPORARY_LOCATION_ID_KEY);
    updateHoldingExpectNoContent(holding);

    for (var itemId : itemIds) {
      assertThat(getItemById(itemId).getString(EFFECTIVE_LOCATION_ID_KEY)).isEqualTo(mainLibraryLocationId);
    }
  }

  @Test
  @DisplayName("should only update items without their own location override when the holding's "
               + "temporary location changes")
  void shouldOnlyUpdateItemsWithoutOwnLocationOverride_whenHoldingTemporaryLocationChanges() {
    var holding = createHolding(holdingRequestWithLocations(createInstanceRecord(),
      new PermTemp(mainLibraryLocationId, null)));
    var holdingId = holding.getString("id");
    var itemWithOwnPermLocationId = createItem(itemRequestWithLocations(holdingId,
      new PermTemp(onlineLocationId, null))).getString("id");
    final var itemWithNoOverrideId = createItem(itemRequest(holdingId)).getString("id");
    final var itemWithOwnTempLocationId = createItem(itemRequestWithLocations(holdingId,
      new PermTemp(null, annexLibraryLocationId))).getString("id");

    holding.put(TEMPORARY_LOCATION_ID_KEY, secondFloorLocationId);
    updateHoldingExpectNoContent(holding);

    assertThat(getItemById(itemWithOwnPermLocationId).getString(EFFECTIVE_LOCATION_ID_KEY))
      .isEqualTo(onlineLocationId);
    assertThat(getItemById(itemWithNoOverrideId).getString(EFFECTIVE_LOCATION_ID_KEY))
      .isEqualTo(secondFloorLocationId);
    assertThat(getItemById(itemWithOwnTempLocationId).getString(EFFECTIVE_LOCATION_ID_KEY))
      .isEqualTo(annexLibraryLocationId);
  }

  @Test
  @DisplayName("should move an item's effective location when it's moved to another holding and has "
               + "no location of its own")
  void shouldMoveItemEffectiveLocation_whenItemWithoutOwnLocationMovedToAnotherHolding() {
    var firstHoldingId = createHolding(holdingRequestWithLocations(createInstanceRecord(),
      new PermTemp(mainLibraryLocationId, annexLibraryLocationId))).getString("id");
    var secondHoldingId = createHolding(holdingRequestWithLocations(createInstanceRecord(),
      new PermTemp(onlineLocationId, secondFloorLocationId))).getString("id");
    var item = createItem(itemRequest(firstHoldingId));
    assertThat(item.getString(EFFECTIVE_LOCATION_ID_KEY)).isEqualTo(annexLibraryLocationId);

    item.put("holdingsRecordId", secondHoldingId);
    updateItemExpectNoContent(item);

    assertThat(getItemById(item.getString("id")).getString(EFFECTIVE_LOCATION_ID_KEY))
      .isEqualTo(secondFloorLocationId);
  }

  @Test
  @DisplayName("should keep an item's own effective location when it's moved to another holding")
  void shouldKeepItemEffectiveLocation_whenItemWithOwnPermanentLocationMovedToAnotherHolding() {
    var firstHoldingId = createHolding(holdingRequestWithLocations(createInstanceRecord(),
      new PermTemp(mainLibraryLocationId, annexLibraryLocationId))).getString("id");
    var secondHoldingId = createHolding(holdingRequestWithLocations(createInstanceRecord(),
      new PermTemp(secondFloorLocationId, null))).getString("id");
    var item = createItem(itemRequestWithLocations(firstHoldingId, new PermTemp(onlineLocationId, null)));
    assertThat(item.getString(EFFECTIVE_LOCATION_ID_KEY)).isEqualTo(onlineLocationId);

    item.put("holdingsRecordId", secondHoldingId);
    updateItemExpectNoContent(item);

    assertThat(getItemById(item.getString("id")).getString(EFFECTIVE_LOCATION_ID_KEY)).isEqualTo(onlineLocationId);
  }

  @Test
  @DisplayName("should set both the jsonb and the column effective location on item update")
  void shouldSetBothJsonbAndColumnEffectiveLocationId_onItemUpdate() {
    var holdingId = createHolding(holdingRequestWithLocations(createInstanceRecord(),
      new PermTemp(mainLibraryLocationId, annexLibraryLocationId))).getString("id");
    var item = createItem(itemRequestWithLocations(holdingId, new PermTemp(onlineLocationId, null)));

    item.put(TEMPORARY_LOCATION_ID_KEY, secondFloorLocationId);
    updateItemExpectNoContent(item);

    var row = runQuery("SELECT jsonb, effectivelocationid FROM item WHERE id='"
                       + item.getString("id") + "'").iterator().next();
    var jsonb = (JsonObject) row.getValue(0);
    assertThat(jsonb.getString(EFFECTIVE_LOCATION_ID_KEY)).isEqualTo(secondFloorLocationId);
    assertThat(row.getUUID(1).toString()).isEqualTo(secondFloorLocationId);
  }

  @ParameterizedTest(name = "[{index}]: {arguments}")
  @MethodSource("effectiveCallNumberPropertiesOnCreateParams")
  @DisplayName("should calculate the effective call number component on item creation")
  void shouldCalculateEffectiveCallNumberComponent_onItemCreate(
    CallNumberComponentPropertyNames callNumberProperties,
    String holdingsPropertyValue,
    String itemPropertyValue) {

    var holding = createHoldingWithCallNumberProperty(
      callNumberProperties.holdingsPropertyName(), holdingsPropertyValue);
    var item = createItem(itemRequest(holding.getString("id"))
      .put(callNumberProperties.itemPropertyName(), itemPropertyValue));

    assertThat(getEffectiveComponent(item, callNumberProperties.effectivePropertyName()))
      .isEqualTo(firstNonBlank(itemPropertyValue, holdingsPropertyValue));
  }

  @ParameterizedTest(name = "[{index}]: {arguments}")
  @MethodSource("effectiveCallNumberPropertiesOnUpdateParams")
  @DisplayName("should calculate the effective call number component on update")
  void shouldCalculateEffectiveCallNumberComponent_onUpdate(
    CallNumberComponentPropertyNames callNumberProperties,
    String holdingsInitValue,
    String holdingsTargetValue,
    String itemInitValue,
    String itemTargetValue) {

    var holding = createHoldingWithCallNumberProperty(
      callNumberProperties.holdingsPropertyName(), holdingsInitValue);
    var item = createItem(itemRequest(holding.getString("id"))
      .put(callNumberProperties.itemPropertyName(), itemInitValue));

    assertThat(getEffectiveComponent(item, callNumberProperties.effectivePropertyName()))
      .isEqualTo(firstNonBlank(itemInitValue, holdingsInitValue));

    var holdingToUpdate = getHoldingById(holding.getString("id"))
      .put(callNumberProperties.holdingsPropertyName(), holdingsTargetValue);
    updateHoldingExpectNoContent(holdingToUpdate);

    if (!Objects.equals(itemInitValue, itemTargetValue)) {
      var itemToUpdate = getItemById(item.getString("id"))
        .put(callNumberProperties.itemPropertyName(), itemTargetValue);
      updateItemExpectNoContent(itemToUpdate);
    }

    var updatedItem = getItemById(item.getString("id"));
    assertThat(getEffectiveComponent(updatedItem, callNumberProperties.effectivePropertyName()))
      .isEqualTo(firstNonBlank(itemTargetValue, holdingsTargetValue));
  }

  private static Stream<Arguments> itemUpdateEffectiveLocationParams() {
    var holdingLocations = List.of(
      new PermTemp(mainLibraryLocationId, null),
      new PermTemp(mainLibraryLocationId, annexLibraryLocationId));
    var itemStartLocations = List.of(
      new PermTemp(null, null),
      new PermTemp(null, onlineLocationId),
      new PermTemp(onlineLocationId, null),
      new PermTemp(onlineLocationId, secondFloorLocationId));
    var itemEndLocations = List.of(
      new PermTemp(null, null),
      new PermTemp(null, onlineLocationId),
      new PermTemp(onlineLocationId, null),
      new PermTemp(onlineLocationId, secondFloorLocationId),
      new PermTemp(null, thirdFloorLocationId),
      new PermTemp(thirdFloorLocationId, null),
      new PermTemp(thirdFloorLocationId, fourthFloorLocationId));

    return holdingLocations.stream().flatMap(holdingLoc -> itemStartLocations.stream()
      .flatMap(itemStart -> itemEndLocations.stream().map(itemEnd -> arguments(holdingLoc, itemStart, itemEnd))));
  }

  private static Stream<Arguments> holdingUpdateEffectiveLocationParams() {
    var itemLocations = List.of(
      new PermTemp(null, null),
      new PermTemp(null, mainLibraryLocationId),
      new PermTemp(mainLibraryLocationId, null),
      new PermTemp(mainLibraryLocationId, annexLibraryLocationId));
    var holdingStartLocations = List.of(
      new PermTemp(onlineLocationId, null),
      new PermTemp(onlineLocationId, secondFloorLocationId));
    var holdingEndLocations = List.of(
      new PermTemp(onlineLocationId, null),
      new PermTemp(onlineLocationId, secondFloorLocationId),
      new PermTemp(thirdFloorLocationId, null),
      new PermTemp(thirdFloorLocationId, fourthFloorLocationId));

    return itemLocations.stream().flatMap(itemLoc -> holdingStartLocations.stream()
      .flatMap(holdingStart -> holdingEndLocations.stream()
        .map(holdingEnd -> arguments(itemLoc, holdingStart, holdingEnd))));
  }

  private static JsonObject holdingRequestWithLocations(String instanceId, PermTemp locations) {
    var builder = new HoldingRequestBuilder()
      .forInstance(UUID.fromString(instanceId))
      .withSource(UUID.fromString(createHoldingsRecordsSource(client)))
      .withPermanentLocation(UUID.fromString(locations.perm()));
    if (locations.temp() != null) {
      builder = builder.withTemporaryLocation(UUID.fromString(locations.temp()));
    }
    return builder.create();
  }

  private static JsonObject itemRequestWithLocations(String holdingId, PermTemp locations) {
    var request = itemRequest(holdingId);
    setPermanentTemporaryLocation(request, locations);
    return request;
  }

  private static void setPermanentTemporaryLocation(JsonObject json, PermTemp locations) {
    if (locations.perm() == null) {
      json.remove(PERMANENT_LOCATION_ID_KEY);
    } else {
      json.put(PERMANENT_LOCATION_ID_KEY, locations.perm());
    }
    if (locations.temp() == null) {
      json.remove(TEMPORARY_LOCATION_ID_KEY);
    } else {
      json.put(TEMPORARY_LOCATION_ID_KEY, locations.temp());
    }
  }

  // No NPE: a holding's own permanentLocationId is required, so the chain always bottoms out.
  private static String effectiveLocation(PermTemp holdingLocations, PermTemp itemLocations) {
    if (itemLocations.temp() != null) {
      return itemLocations.temp();
    }
    if (itemLocations.perm() != null) {
      return itemLocations.perm();
    }
    if (holdingLocations.temp() != null) {
      return holdingLocations.temp();
    }
    return holdingLocations.perm();
  }

  private static boolean locationsEqual(JsonObject firstHolding, JsonObject secondHolding) {
    return Objects.equals(firstHolding.getString(PERMANENT_LOCATION_ID_KEY),
      secondHolding.getString(PERMANENT_LOCATION_ID_KEY))
           && Objects.equals(firstHolding.getString(TEMPORARY_LOCATION_ID_KEY),
      secondHolding.getString(TEMPORARY_LOCATION_ID_KEY));
  }

  private static void updateItemExpectNoContent(JsonObject item) {
    var response = get(doPut(client, ResourcePaths.ITEMS + "/" + item.getString("id"), item));
    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
  }

  private static Stream<Arguments> relatedHoldingFields() {
    return Stream.of(
      arguments("callNumberTypeId", lcCallNumberTypeId, deweyCallNumberTypeId, "typeId"),
      arguments("callNumberPrefix", null, "new prefix", "prefix"),
      arguments("callNumber", "initial call number", "updated call number", "callNumber"),
      arguments("callNumberSuffix", null, "new suffix", "suffix"),
      arguments(TEMPORARY_LOCATION_ID_KEY, null, annexLibraryLocationId, null));
  }

  private static String createHoldingWithField(String holdingField, String initialValue) {
    var request = holdingRequest(createInstanceRecord()).create();
    if (initialValue != null) {
      request.put(holdingField, initialValue);
    }
    return createHolding(request).getString("id");
  }

  private static void assertItemUpdated(String itemId, String newValue, String expectedComponentSubField) {
    var item = getItemById(itemId);
    assertThat(item.getString(VERSION_KEY)).isEqualTo("2");
    if (expectedComponentSubField != null) {
      assertThat(getEffectiveComponent(item, expectedComponentSubField)).isEqualTo(newValue);
    } else {
      assertThat(item.getString(EFFECTIVE_LOCATION_ID_KEY)).isEqualTo(newValue);
    }
  }

  private static HoldingAndItem createHoldingAndItemWithAllCallNumberComponents() {
    var holding = createHolding(holdingRequestWithAllCallNumberComponents(
      createInstanceRecord(), "testCallNumber", "testCallNumberPrefix", "testCallNumberSuffix"));
    var item = createItem(holding.getString("id"));
    assertItemHasEffectiveComponents(item, "1", "testCallNumber", "testCallNumberPrefix",
      "testCallNumberSuffix", lcCallNumberTypeId);
    assertThat(item.getString(EFFECTIVE_LOCATION_ID_KEY)).isEqualTo(mainLibraryLocationId);
    return new HoldingAndItem(holding, item.getString("id"));
  }

  private static void assertItemHasEffectiveComponents(JsonObject item, String expectedVersion,
                                                       String callNumber, String prefix, String suffix,
                                                       String typeId) {
    assertThat(item.getString(VERSION_KEY)).isEqualTo(expectedVersion);
    var components = item.getJsonObject(EFFECTIVE_CALL_NUMBER_COMPONENTS_KEY);
    assertThat(components.getString("callNumber")).isEqualTo(callNumber);
    assertThat(components.getString("prefix")).isEqualTo(prefix);
    assertThat(components.getString("suffix")).isEqualTo(suffix);
    assertThat(components.getString("typeId")).isEqualTo(typeId);
  }

  private static void assertItemsHaveExpectedCallNumberComponents(String itemId, String callNumber, String prefix,
                                                                  String suffix) {
    var item = getItemById(itemId);
    assertThat(getEffectiveComponent(item, "callNumber")).isEqualTo(callNumber);
    assertThat(getEffectiveComponent(item, "prefix")).isEqualTo(prefix);
    assertThat(getEffectiveComponent(item, "suffix")).isEqualTo(suffix);
  }

  private static void assertItemsHaveCallNumber(String[] itemIds, String expectedCallNumber) {
    for (var itemId : itemIds) {
      assertThat(getEffectiveComponent(getItemById(itemId), "callNumber")).isEqualTo(expectedCallNumber);
    }
  }

  // -- effective call-number component coverage (migrated from ItemEffectiveCallNumberComponentsTest) --

  private static void assertItemsHaveEffectiveComponent(String[] itemIds, String componentName,
                                                        String expectedValue) {
    for (var itemId : itemIds) {
      assertThat(getEffectiveComponent(getItemById(itemId), componentName)).isEqualTo(expectedValue);
    }
  }

  private static String getEffectiveComponent(JsonObject item, String componentName) {
    return item.getJsonObject(EFFECTIVE_CALL_NUMBER_COMPONENTS_KEY).getString(componentName);
  }

  private static Stream<Arguments> effectiveCallNumberPropertiesOnCreateParams() {
    return Stream.of(
      // Call number
      arguments(forProperty("callNumber"), "hrCallNumber", "itCallNumber"),
      arguments(forProperty("callNumber"), null, "itCallNumber"),
      arguments(forProperty("callNumber"), "hrCallNumber", null),
      arguments(forProperty("callNumber"), null, null),

      // Call number suffix
      arguments(forProperty("suffix"), "hrCNSuffix", "itCNSuffix"),
      arguments(forProperty("suffix"), "hrCNSuffix", null),
      arguments(forProperty("suffix"), null, "itCNSuffix"),
      arguments(forProperty("suffix"), null, null),

      // Call number prefix
      arguments(forProperty("prefix"), "hrCNPrefix", "itCNPrefix"),
      arguments(forProperty("prefix"), "hrCNPrefix", null),
      arguments(forProperty("prefix"), null, "itCNPrefix"),
      arguments(forProperty("prefix"), null, null),

      // Call number type
      arguments(forProperty("typeId"), DEWEY_CALL_NUMBER_TYPE_ID, LC_CALL_NUMBER_TYPE_ID),
      arguments(forProperty("typeId"), DEWEY_CALL_NUMBER_TYPE_ID, null),
      arguments(forProperty("typeId"), null, LC_CALL_NUMBER_TYPE_ID),
      arguments(forProperty("typeId"), null, null)
    );
  }

  private static Stream<Arguments> effectiveCallNumberPropertiesOnUpdateParams() {
    return Stream.of(
        callNumberUpdateParams(),
        suffixUpdateParams(),
        prefixUpdateParams(),
        typeIdUpdateParams())
      .flatMap(Function.identity());
  }

  private static Stream<Arguments> callNumberUpdateParams() {
    return Stream.of(
      arguments(forProperty("callNumber"), "initHrCN", "targetHrCN", "initItCN", "targetItCN"),
      arguments(forProperty("callNumber"), "initHrCN", null, "initItCN", "targetItCN"),
      arguments(forProperty("callNumber"), "initHrCN", "targetHrCN", "initItCN", null),
      arguments(forProperty("callNumber"), "initHrCN", null, "initItCN", null),
      arguments(forProperty("callNumber"), "initHrCN", null, "initItCN", "initItCN"),
      arguments(forProperty("callNumber"), "initHrCN", "initHrCN", "initItCN", null),
      arguments(forProperty("callNumber"), "initHrCN", "targetHrCN", null, null),
      arguments(forProperty("callNumber"), null, "targetHrCN", "initItCN", null));
  }

  private static Stream<Arguments> suffixUpdateParams() {
    return Stream.of(
      arguments(forProperty("suffix"), "initHrCNSuffix", "targetHrCNSuffix", "initItCNSuffix", "targetItCNSuffix"),
      arguments(forProperty("suffix"), "initHrCNSuffix", null, "initItCNSuffix", "targetItCNSuffix"),
      arguments(forProperty("suffix"), "initHrCNSuffix", "targetHrCNSuffix", "initItCNSuffix", null),
      arguments(forProperty("suffix"), "initHrCNSuffix", null, "initItCNSuffix", null),
      arguments(forProperty("suffix"), "initHrCNSuffix", null, "initItCNSuffix", "initItCNSuffix"),
      arguments(forProperty("suffix"), "initHrCNSuffix", "initHrCNSuffix", "initItCNSuffix", null),
      arguments(forProperty("suffix"), "initHrCNSuffix", "targetHrCNSuffix", null, null),
      arguments(forProperty("suffix"), null, "targetHrCNSuffix", "initItCNSuffix", null));
  }

  private static Stream<Arguments> prefixUpdateParams() {
    return Stream.of(
      arguments(forProperty("prefix"), "initHrCNPrefix", "targetHrCNPrefix", "initItCNPrefix", "targetItCNPrefix"),
      arguments(forProperty("prefix"), "initHrCNPrefix", null, "initItCNPrefix", "targetItCNPrefix"),
      arguments(forProperty("prefix"), "initHrCNPrefix", "targetHrCNPrefix", "initItCNPrefix", null),
      arguments(forProperty("prefix"), "initHrCNPrefix", null, "initItCNPrefix", null),
      arguments(forProperty("prefix"), "initHrCNPrefix", null, "initItCNPrefix", "initItCNPrefix"),
      arguments(forProperty("prefix"), "initHrCNPrefix", "initHrCNPrefix", "initItCNPrefix", null),
      arguments(forProperty("prefix"), "initHrCNPrefix", "targetHrCNPrefix", null, null),
      arguments(forProperty("prefix"), null, "targetHrCNPrefix", "initItCNPrefix", null));
  }

  private static Stream<Arguments> typeIdUpdateParams() {
    return Stream.of(
      arguments(forProperty("typeId"), DEWEY_CALL_NUMBER_TYPE_ID, NLM_CALL_NUMBER_TYPE_ID,
        LC_CALL_NUMBER_TYPE_ID, MOYS_CALL_NUMBER_TYPE_ID),
      arguments(forProperty("typeId"), DEWEY_CALL_NUMBER_TYPE_ID, null,
        LC_CALL_NUMBER_TYPE_ID, MOYS_CALL_NUMBER_TYPE_ID),
      arguments(forProperty("typeId"), DEWEY_CALL_NUMBER_TYPE_ID, NLM_CALL_NUMBER_TYPE_ID,
        LC_CALL_NUMBER_TYPE_ID, null),
      arguments(forProperty("typeId"), DEWEY_CALL_NUMBER_TYPE_ID, null,
        LC_CALL_NUMBER_TYPE_ID, null),
      arguments(forProperty("typeId"), DEWEY_CALL_NUMBER_TYPE_ID, null,
        LC_CALL_NUMBER_TYPE_ID, LC_CALL_NUMBER_TYPE_ID),
      arguments(forProperty("typeId"), DEWEY_CALL_NUMBER_TYPE_ID, DEWEY_CALL_NUMBER_TYPE_ID,
        LC_CALL_NUMBER_TYPE_ID, null),
      arguments(forProperty("typeId"), DEWEY_CALL_NUMBER_TYPE_ID, NLM_CALL_NUMBER_TYPE_ID,
        null, null),
      arguments(forProperty("typeId"), null, NLM_CALL_NUMBER_TYPE_ID,
        LC_CALL_NUMBER_TYPE_ID, null));
  }

  private static JsonObject createHoldingWithCallNumberProperty(String propertyName, String propertyValue) {
    var request = holdingRequest(createInstanceRecord()).create();
    if (propertyValue == null) {
      request.remove(propertyName);
    } else {
      request.put(propertyName, propertyValue);
    }
    return createHolding(request);
  }

  private static String firstNonBlank(String first, String second) {
    return first != null && !first.isBlank() ? first : second;
  }

  private static CallNumberComponentPropertyNames forProperty(String effectivePropertyName) {
    return switch (effectivePropertyName) {
      case "callNumber" -> new CallNumberComponentPropertyNames(
        "callNumber", "itemLevelCallNumber", "callNumber");
      case "suffix" -> new CallNumberComponentPropertyNames(
        "callNumberSuffix", "itemLevelCallNumberSuffix", "suffix");
      case "prefix" -> new CallNumberComponentPropertyNames(
        "callNumberPrefix", "itemLevelCallNumberPrefix", "prefix");
      case "typeId" -> new CallNumberComponentPropertyNames(
        "callNumberTypeId", "itemLevelCallNumberTypeId", "typeId");
      default -> throw new IllegalArgumentException(
        "Unknown effective call number property: " + effectivePropertyName);
    };
  }

  private static String createInstanceRecord() {
    return createInstance(client, "an instance " + UUID.randomUUID(), instanceTypeId);
  }

  // -- shared helpers --

  private static HoldingRequestBuilder holdingRequest(String instanceId) {
    return new HoldingRequestBuilder()
      .forInstance(UUID.fromString(instanceId))
      .withSource(UUID.fromString(createHoldingsRecordsSource(client)))
      .withPermanentLocation(UUID.fromString(mainLibraryLocationId));
  }

  private static HoldingRequestBuilder withHrid(HoldingRequestBuilder builder, String hrid) {
    return builder.withHrid(hrid);
  }

  private static JsonObject holdingRequestWithAllCallNumberComponents(String instanceId, String callNumber,
                                                                      String prefix, String suffix) {
    return holdingRequest(instanceId)
      .withCallNumber(callNumber)
      .withCallNumberPrefix(prefix)
      .withCallNumberSuffix(suffix)
      .withCallNumberTypeId(lcCallNumberTypeId)
      .create();
  }

  private static JsonObject createHolding(HoldingRequestBuilder builder) {
    return createHolding(builder.create());
  }

  private static JsonObject createHolding(JsonObject request) {
    var response = get(doPost(client, ResourcePaths.HOLDINGS, request));
    assertThat(response.status()).isEqualTo(SC_CREATED);
    return response.jsonBody();
  }

  private static void updateHoldingExpectNoContent(JsonObject holding) {
    var response = get(doPut(client, ResourcePaths.HOLDINGS + "/" + holding.getString("id"), holding));
    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
  }

  private static JsonObject getHoldingById(String id) {
    return get(doGet(client, ResourcePaths.HOLDINGS + "/" + id)).jsonBody();
  }

  private static TestResponse patchHolding(String holdingId, JsonObject patch) {
    return get(doPatch(client, ResourcePaths.HOLDINGS + "/" + holdingId, patch));
  }

  private static TestResponse syncBatchUpsert(JsonObject holding) {
    return get(doPost(client, ResourcePaths.HOLDINGS_SYNC + "?upsert=true",
      new JsonObject().put("holdingsRecords", new JsonArray().add(holding))));
  }

  private static TestResponse syncBatchUnsafe(JsonObject holding) {
    return get(doPost(client, ResourcePaths.HOLDINGS_SYNC_UNSAFE,
      new JsonObject().put("holdingsRecords", new JsonArray().add(holding))));
  }

  private static void setHoldingsSequence(long sequenceNumber) {
    runQuery("select setval('hrid_holdings_seq'," + sequenceNumber + ",FALSE)");
  }

  private static JsonObject itemRequest(String holdingId) {
    return new JsonObject()
      .put("holdingsRecordId", holdingId)
      .put("status", new JsonObject().put("name", "Available"))
      .put("permanentLoanTypeId", loanTypeId)
      .put("materialTypeId", materialTypeId);
  }

  private static JsonObject complexItemRequest(String holdingId, String prefix, String suffix, String volume,
                                               String enumeration, String chronology, String copy) {
    return itemRequest(holdingId)
      .put("itemLevelCallNumberSuffix", suffix)
      .put("itemLevelCallNumberPrefix", prefix)
      .put("itemLevelCallNumberTypeId", lcCallNumberTypeId)
      .put("volume", volume)
      .put("enumeration", enumeration)
      .put("chronology", chronology)
      .put("copyNumber", copy);
  }

  private static JsonObject createItem(String holdingId) {
    return createItem(itemRequest(holdingId));
  }

  private static JsonObject createItem(JsonObject request) {
    var response = get(doPost(client, ResourcePaths.ITEMS, request));
    assertThat(response.status()).isEqualTo(SC_CREATED);
    return response.jsonBody();
  }

  private static JsonObject getItemById(String id) {
    return get(doGet(client, ResourcePaths.ITEMS + "/" + id)).jsonBody();
  }

  private record CallNumberComponentPropertyNames(
    String holdingsPropertyName,
    String itemPropertyName,
    String effectivePropertyName) {
  }

  private record HoldingAndItem(JsonObject holding, String itemId) {
  }

  private record PermTemp(String perm, String temp) {
  }
}
