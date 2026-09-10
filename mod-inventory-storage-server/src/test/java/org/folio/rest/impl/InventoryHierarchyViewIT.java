package org.folio.rest.impl;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.rest.impl.HoldingsStorageFixtures.createHolding;
import static org.folio.rest.impl.HoldingsStorageFixtures.createHoldingsRecordsSource;
import static org.folio.rest.impl.HoldingsStorageFixtures.createMaterialType;
import static org.folio.rest.impl.InstanceStorageFixtures.createInstance;
import static org.folio.rest.impl.InstanceStorageFixtures.createInstanceType;
import static org.folio.rest.impl.LocationStorageFixtures.createCampus;
import static org.folio.rest.impl.LocationStorageFixtures.createInstitution;
import static org.folio.rest.impl.LocationStorageFixtures.createLibrary;
import static org.folio.rest.impl.LocationStorageFixtures.createLocation;
import static org.folio.rest.impl.LocationStorageFixtures.createServicePoint;
import static org.folio.utility.RestUtility.TENANT_ID;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.folio.rest.jaxrs.model.InventoryHierarchyInstanceIds;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.support.builders.BoundWithPartBuilder;
import org.folio.rest.support.builders.HoldingRequestBuilder;
import org.folio.rest.support.builders.ItemRequestBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InventoryHierarchyViewIT extends BaseIntegrationTest {

  private static final String QUERY_PARAM_SKIP_SUPPRESSED = "skipSuppressedFromDiscoveryRecords";
  private static final String SOURCE_PARAM = "source";
  private static final String START_DATE_PARAM = "startDate";
  private static final String END_DATE_PARAM = "endDate";
  private static final String INSTANCE_ID_FIELD = "instanceId";
  private static final String ITEMS_FIELD = "items";
  private static final String HOLDINGS_FIELD = "holdings";
  private static final String LOCATION_FIELD = "location";
  private static final String CALL_NUMBER_FIELD = "callNumber";
  private static final String EFFECTIVE_LOCATION_FIELD = "effectiveLocation";
  private static final String PERMANENT_LOCATION_FIELD = "permanentLocation";
  private static final String TEMPORARY_LOCATION_FIELD = "temporaryLocation";
  private static final String NAME_FIELD = "name";
  private static final String CODE_FIELD = "code";
  private static final String TEST_SOURCE = "TEST";

  private static String instanceTypeId;
  private static String materialTypeId;
  private static String secondMaterialTypeId;
  private static String loanTypeId;
  private static String institutionName;
  private static String libraryCode;
  private static String mainLocationId;
  private static JsonObject mainLocation;
  private static String thirdFloorLocationId;
  private static JsonObject thirdFloorLocation;
  private static String annexLocationId;
  private static JsonObject annexLocation;

  private Map<String, String> params;
  private String instanceId;
  private String holdingId;
  private JsonObject predefinedInstance;
  private JsonObject predefinedHolding;

  @BeforeAll
  static void seedReferenceData() {
    instanceTypeId = createInstanceType(client);
    materialTypeId = createMaterialType(client);
    secondMaterialTypeId = createMaterialType(client);
    loanTypeId = HoldingsStorageFixtures.createLoanType(client);

    var institutionId = createInstitution(client);
    var campusId = createCampus(client, institutionId);
    var libraryId = createLibrary(client, campusId);
    var servicePointId = createServicePoint(client);
    mainLocationId = createLocation(client, institutionId, campusId, libraryId, servicePointId);
    thirdFloorLocationId = createLocation(client, institutionId, campusId, libraryId, servicePointId);
    annexLocationId = createLocation(client, institutionId, campusId, libraryId, servicePointId);

    institutionName = getJson(ResourcePaths.LOCATION_UNITS_INSTITUTIONS + "/" + institutionId).getString(NAME_FIELD);
    libraryCode = getJson(ResourcePaths.LOCATION_UNITS_LIBRARIES + "/" + libraryId).getString(CODE_FIELD);
    mainLocation = getJson(ResourcePaths.LOCATIONS + "/" + mainLocationId);
    thirdFloorLocation = getJson(ResourcePaths.LOCATIONS + "/" + thirdFloorLocationId);
    annexLocation = getJson(ResourcePaths.LOCATIONS + "/" + annexLocationId);
  }

  @BeforeEach
  void createFixtures() {
    runQuery("TRUNCATE TABLE instance, holdings_record, item, bound_with_part, "
      + "audit_instance, audit_holdings_record, audit_item CASCADE");
    params = new HashMap<>();

    instanceId = createInstance(client, "an instance", instanceTypeId);
    holdingId = createHoldingWithLocations(mainLocationId, null);
    predefinedInstance = getJson(ResourcePaths.INSTANCES + "/" + instanceId);
    predefinedHolding = getJson(ResourcePaths.HOLDINGS + "/" + holdingId);

    createItem(itemRequest(holdingId, 1, "item barcode", "item effective call number 1",
      materialTypeId, mainLocationId));
    createItem(itemRequest(holdingId, 2, "item barcode 2", "item effective call number 2",
      secondMaterialTypeId, thirdFloorLocationId));
  }

  @Test
  @DisplayName("should return a 500 with an error message when the items-and-holdings database function is missing")
  void shouldReturn500_whenDatabaseFunctionIsMissing() {
    var sql = "ALTER FUNCTION " + TENANT_ID + "_mod_inventory_storage.get_items_and_holdings_view RENAME TO x";
    runQuery(sql);
    try {
      params.put(QUERY_PARAM_SKIP_SUPPRESSED, "false");
      var updatedInstanceIds = requestUpdatedInstanceIds(params).orElseThrow();

      var response = requestItemsAndHoldingsRaw(List.of(updatedInstanceIds.getString(INSTANCE_ID_FIELD)), false);

      assertThat(response.status()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
      assertThat(response.body().toString())
        .contains("function get_items_and_holdings_view(unknown, unknown) does not exist");
    } finally {
      runQuery("ALTER FUNCTION " + TENANT_ID + "_mod_inventory_storage.x RENAME TO get_items_and_holdings_view");
    }
  }

  @Test
  @DisplayName("should return the predefined instance id and source when requesting without parameters")
  void shouldReturnInstance_whenRequestingWithoutParameters() {
    params.put(QUERY_PARAM_SKIP_SUPPRESSED, "false");

    var data = getInventoryHierarchyInstance(params).orElseThrow();

    assertThat(data.getString(INSTANCE_ID_FIELD)).isEqualTo(predefinedInstance.getString("id"));
    assertThat(data.getString(SOURCE_PARAM)).isEqualTo(predefinedInstance.getString(SOURCE_PARAM));
  }

  @Test
  @DisplayName("should return the predefined holding with its effective and permanent location when "
    + "requesting without parameters")
  void shouldReturnHoldings_whenRequestingWithoutParameters() {
    params.put(QUERY_PARAM_SKIP_SUPPRESSED, "false");

    var data = getInventoryHierarchyInstance(params).orElseThrow();
    var holding = asObjects(holdings(data)).getFirst();

    assertThat(holdings(data)).hasSize(1);
    assertThat(holding.getString("id")).isEqualTo(holdingId);
    assertThat(holdingLocationField(holding, EFFECTIVE_LOCATION_FIELD, NAME_FIELD))
      .isEqualTo(mainLocation.getString(NAME_FIELD));
    assertThat(holdingLocationField(holding, EFFECTIVE_LOCATION_FIELD, CODE_FIELD))
      .isEqualTo(mainLocation.getString(CODE_FIELD));
    assertThat(holdingLocationField(holding, PERMANENT_LOCATION_FIELD, NAME_FIELD))
      .isEqualTo(mainLocation.getString(NAME_FIELD));
    assertThat(holdingLocationField(holding, PERMANENT_LOCATION_FIELD, CODE_FIELD))
      .isEqualTo(mainLocation.getString(CODE_FIELD));
  }

  @Test
  @DisplayName("should use the temporary location as the effective location once a holding has one set")
  void shouldUseTemporaryLocation_asEffectiveLocation() {
    var holding = getJson(ResourcePaths.HOLDINGS + "/" + holdingId);
    holding.put("temporaryLocationId", annexLocationId);
    holding.put("sourceId", createHoldingsRecordsSource(client));
    assertThat(get(doPut(client, ResourcePaths.HOLDINGS + "/" + holdingId, holding)).status())
      .isEqualTo(SC_NO_CONTENT);

    params.put(QUERY_PARAM_SKIP_SUPPRESSED, "false");
    var data = getInventoryHierarchyInstance(params).orElseThrow();
    var updatedHolding = asObjects(holdings(data)).getFirst();

    assertThat(holdingLocationField(updatedHolding, TEMPORARY_LOCATION_FIELD, NAME_FIELD))
      .isEqualTo(annexLocation.getString(NAME_FIELD));
    assertThat(holdingLocationField(updatedHolding, TEMPORARY_LOCATION_FIELD, CODE_FIELD))
      .isEqualTo(annexLocation.getString(CODE_FIELD));
    assertThat(holdingLocationField(updatedHolding, EFFECTIVE_LOCATION_FIELD, NAME_FIELD))
      .isEqualTo(annexLocation.getString(NAME_FIELD));
    assertThat(holdingLocationField(updatedHolding, EFFECTIVE_LOCATION_FIELD, CODE_FIELD))
      .isEqualTo(annexLocation.getString(CODE_FIELD));
    assertThat(holdingLocationField(updatedHolding, PERMANENT_LOCATION_FIELD, NAME_FIELD))
      .isEqualTo(mainLocation.getString(NAME_FIELD));
  }

  @Test
  @DisplayName("should return item location, material type, and call number details when requesting without source")
  void shouldReturnItems_whenRequestingWithoutSource() {
    params.put(QUERY_PARAM_SKIP_SUPPRESSED, "false");

    assertDefaultItemsData(getInventoryHierarchyInstance(params).orElseThrow());
  }

  @Test
  @DisplayName("should return item location, material type, and call number details when the source matches")
  void shouldReturnItems_whenRequestingWithMatchingSource() {
    params.put(QUERY_PARAM_SKIP_SUPPRESSED, "false");
    params.put(SOURCE_PARAM, TEST_SOURCE);

    assertDefaultItemsData(getInventoryHierarchyInstance(params).orElseThrow());
  }

  @Test
  @DisplayName("should return no instances when the database is empty")
  void shouldReturnNoInstances_whenDatabaseIsEmpty() {
    runQuery("TRUNCATE TABLE instance, holdings_record, item CASCADE");

    assertThat(getInventoryHierarchyInstance(params)).isEmpty();
  }

  @Test
  @DisplayName("should mark an instance as deleted only when deletedRecordSupport is requested")
  void shouldReturnDeletedInstance_onlyWhenDeletedRecordSupportRequested() {
    // a real per-row DELETE, not TRUNCATE: deletedRecordSupport is served from the audit tables,
    // which only the row-level AFTER DELETE triggers populate - TRUNCATE bypasses those entirely.
    runQuery("DELETE FROM item");
    runQuery("DELETE FROM holdings_record");
    runQuery("DELETE FROM instance");

    params.put("deletedRecordSupport", "true");
    var withSupport = requestUpdatedInstanceIds(params).orElseThrow();
    assertThat(Boolean.parseBoolean(withSupport.getString("deleted"))).isTrue();

    params.put("deletedRecordSupport", "false");
    assertThat(requestUpdatedInstanceIds(params)).isEmpty();
  }

  @Test
  @DisplayName("should filter by date range when the source matches")
  void shouldFilterByDates_whenSourceMatches() {
    params.put(QUERY_PARAM_SKIP_SUPPRESSED, "false");
    params.put(SOURCE_PARAM, TEST_SOURCE);
    params.put(START_DATE_PARAM, offsetDateTime(2000).toString());

    assertHasDefaultItems(getInventoryHierarchyInstance(params).orElseThrow());
    assertFilteredByDateRange();

    params.put(SOURCE_PARAM, "FAKE_SOURCE");
    assertThat(getInventoryHierarchyInstance(params)).isEmpty();
  }

  @Test
  @DisplayName("should filter by date range when no source is provided")
  void shouldFilterByDates_whenNoSourceProvided() {
    params.put(QUERY_PARAM_SKIP_SUPPRESSED, "false");
    params.put(START_DATE_PARAM, offsetDateTime(2000).toString());

    assertHasDefaultItems(getInventoryHierarchyInstance(params).orElseThrow());
    assertFilteredByDateRange();
  }

  @Test
  @DisplayName("should return no instances when the source parameter does not match any record")
  void shouldReturnNoInstances_whenSourceDoesNotMatch() {
    params.put(QUERY_PARAM_SKIP_SUPPRESSED, "false");
    params.put(SOURCE_PARAM, "invalid");
    params.put(START_DATE_PARAM, offsetDateTime(2000).toString());

    assertThat(getInventoryHierarchyInstance(params)).isEmpty();
  }

  @Test
  @DisplayName("should skip or include discovery-suppressed items based on the query parameter")
  void shouldSkipOrIncludeSuppressedItems_basedOnQueryParameter() {
    createItem(itemRequest(holdingId, 3, "item barcode 3", "item effective call number 3",
      secondMaterialTypeId, thirdFloorLocationId).withDiscoverySuppress(true));

    params.put(QUERY_PARAM_SKIP_SUPPRESSED, "true");
    params.put(SOURCE_PARAM, TEST_SOURCE);
    var skipped = getInventoryHierarchyInstance(params).orElseThrow();
    assertThat(callNumbersOfItems(skipped)).containsExactlyInAnyOrder(
      "item effective call number 1", "item effective call number 2");
    assertThat(items(skipped)).hasSize(2);

    params.put(QUERY_PARAM_SKIP_SUPPRESSED, "false");
    var withSuppressed = getInventoryHierarchyInstance(params).orElseThrow();
    assertThat(callNumbersOfItems(withSuppressed)).containsExactlyInAnyOrder(
      "item effective call number 1", "item effective call number 2", "item effective call number 3");
    assertThat(items(withSuppressed)).hasSize(3);
    assertThat(ordersOfItems(withSuppressed)).containsExactlyInAnyOrder(1, 2, 3);
  }

  @Test
  @DisplayName("should retrieve the instance when only its items were deleted within the requested time window")
  void shouldRetrieveInstance_whenOnlyItemsDeletedWithinPeriod() {
    awaitAtLeast5SecondsAfterFixtureCreation();

    var deletionTime = LocalDateTime.now(ZoneOffset.UTC);
    runQuery("DELETE FROM item");

    assertThat(getInventoryHierarchyInstance(deletionWindowParams(deletionTime))).isPresent();
  }

  @Test
  @DisplayName("should retrieve the instance when only a holding was deleted within the requested time window")
  void shouldRetrieveInstance_whenOnlyHoldingDeletedWithinPeriod() {
    var extraHoldingId = createHoldingWithLocations(mainLocationId, mainLocationId);
    awaitAtLeast5SecondsAfterFixtureCreation();

    var deletionTime = LocalDateTime.now(ZoneOffset.UTC);
    runQuery("DELETE FROM holdings_record WHERE id = '" + extraHoldingId + "'");

    assertThat(getInventoryHierarchyInstance(deletionWindowParams(deletionTime))).isPresent();
  }

  @Test
  @DisplayName("should retrieve the instance when both items and holdings were deleted within the requested "
    + "time window")
  void shouldRetrieveInstance_whenItemsAndHoldingsDeletedWithinPeriod() {
    awaitAtLeast5SecondsAfterFixtureCreation();

    var deletionTime = LocalDateTime.now(ZoneOffset.UTC);
    runQuery("DELETE FROM item");
    runQuery("DELETE FROM holdings_record");

    assertThat(getInventoryHierarchyInstance(deletionWindowParams(deletionTime))).isPresent();
  }

  @Test
  @DisplayName("should include bound-with items borrowed from another holding")
  void shouldRetrieveBoundWithItems() {
    var boundWithHoldingId = createHoldingWithLocations(mainLocationId, null);
    var boundWithItemId = createItem(itemRequest(boundWithHoldingId, 1, "bound-with",
      "item effective call number 1", materialTypeId, mainLocationId));
    createBoundWithPart(holdingId, boundWithItemId);

    var data = requestItemsAndHoldings(List.of(instanceId), false).orElseThrow();

    assertThat(items(data)).hasSize(3);
    assertThat(asObjects(items(data)).stream().anyMatch(item -> "bound-with".equals(item.getString("barcode"))))
      .isTrue();
  }

  @Test
  @DisplayName("should include populated location names for both items and holdings")
  void shouldHaveLocationNames_forItemsAndHoldings() {
    var data = requestItemsAndHoldings(List.of(instanceId), false).orElseThrow();
    var item = asObjects(items(data)).getFirst();
    var holding = asObjects(holdings(data)).getFirst();

    assertThat(itemLocationField(item, LOCATION_FIELD, NAME_FIELD)).isNotBlank();
    assertThat(holdingLocationField(holding, EFFECTIVE_LOCATION_FIELD, NAME_FIELD)).isNotBlank();
    assertThat(holdingLocationField(holding, PERMANENT_LOCATION_FIELD, NAME_FIELD)).isNotBlank();
  }

  @Test
  @DisplayName("should populate hrId for both items and holdings")
  void shouldHaveHrId_forItemsAndHoldings() {
    var data = requestItemsAndHoldings(List.of(instanceId), false).orElseThrow();

    assertThat(items(data)).hasSize(2);
    assertThat(holdings(data)).hasSize(1);
    assertThat(asObjects(items(data))).allSatisfy(item -> assertThat(item.getString("hrId")).isNotBlank());
    assertThat(asObjects(holdings(data)).getFirst().getString("hrId"))
      .isEqualTo(predefinedHolding.getString("hrid"));
  }

  @Test
  @DisplayName("should return 400 when the start date is invalid")
  void shouldReturn400_whenStartDateIsInvalid() {
    params.put(START_DATE_PARAM, "invalidDate");

    assertThat(requestUpdatedInstanceIdsRaw(params).status()).isEqualTo(SC_BAD_REQUEST);
  }

  @Test
  @DisplayName("should return 400 when the end date is invalid")
  void shouldReturn400_whenEndDateIsInvalid() {
    params.put(END_DATE_PARAM, "invalidDate");

    assertThat(requestUpdatedInstanceIdsRaw(params).status()).isEqualTo(SC_BAD_REQUEST);
  }

  @Test
  @DisplayName("should include a holding whose items are all discovery-suppressed when skipping suppressed items")
  void shouldIncludeHolding_whenAllItsItemsAreSuppressed() {
    var secondHoldingId = createHoldingWithLocations(mainLocationId, null);
    createItem(itemRequest(secondHoldingId, 0, "21734", "item suppressed call number",
      materialTypeId, mainLocationId).withDiscoverySuppress(true));

    params.put(QUERY_PARAM_SKIP_SUPPRESSED, "true");
    var data = getInventoryHierarchyInstance(params).orElseThrow();

    assertThat(holdings(data)).hasSize(2);
    assertThat(items(data)).hasSize(2);
  }

  @Test
  @DisplayName("should preserve the entered order of a holding's electronic access entries")
  void shouldRetrieveHierarchyWithOrderedElectronicAccess() {
    var electronicAccess = new JsonArray()
      .add(new JsonObject().put("uri", "http://electronicAccess-c-entered-first"))
      .add(new JsonObject().put("uri", "http://electronicAccess-z-entered-second"))
      .add(new JsonObject().put("uri", "http://electronicAccess-a-entered-third"));
    createHolding(client, new HoldingRequestBuilder()
      .forInstance(UUID.fromString(instanceId))
      .withPermanentLocation(UUID.fromString(mainLocationId))
      .withSource(UUID.fromString(createHoldingsRecordsSource(client)))
      .withElectronicAccess(electronicAccess));

    var data = getInventoryHierarchyInstance(params).orElseThrow();
    var electronicAccessLists = asObjects(holdings(data)).stream()
      .map(holding -> holding.getJsonArray("electronicAccess"))
      .toList();

    assertThat(electronicAccessLists).contains(electronicAccess);
  }

  private void assertFilteredByDateRange() {
    params.put(END_DATE_PARAM, offsetDateTime(2500).toString());
    assertHasDefaultItems(getInventoryHierarchyInstance(params).orElseThrow());

    params.put(START_DATE_PARAM, offsetDateTime(2050).toString());
    assertThat(getInventoryHierarchyInstance(params)).isEmpty();

    params.put(END_DATE_PARAM, offsetDateTime(2000).toString());
    assertThat(getInventoryHierarchyInstance(params)).isEmpty();

    params.put(START_DATE_PARAM, offsetDateTime(2000).toString());
    params.put(END_DATE_PARAM, offsetDateTime(2050).toString());
    assertHasDefaultItems(getInventoryHierarchyInstance(params).orElseThrow());

    params.put(END_DATE_PARAM, offsetDateTime(2001).toString());
    assertThat(getInventoryHierarchyInstance(params)).isEmpty();
  }

  private static void assertHasDefaultItems(JsonObject data) {
    assertThat(callNumbersOfItems(data)).contains("item effective call number 1", "item effective call number 2");
    assertThat(items(data)).hasSize(2);
    assertThat(itemLocationFields(data, LOCATION_FIELD, "institutionName")).containsOnly(institutionName);
  }

  private static void assertDefaultItemsData(JsonObject data) {
    assertHasDefaultItems(data);
    assertThat(itemLocationFields(data, LOCATION_FIELD, "libraryCode")).containsOnly(libraryCode);
    assertThat(itemLocationFields(data, TEMPORARY_LOCATION_FIELD, "libraryCode")).containsOnly(libraryCode);
    assertThat(itemLocationFields(data, TEMPORARY_LOCATION_FIELD, "id")).contains(mainLocationId, thirdFloorLocationId);
    assertThat(itemLocationFields(data, LOCATION_FIELD, "id")).contains(mainLocationId, thirdFloorLocationId);
    assertThat(itemLocationFields(data, LOCATION_FIELD, CODE_FIELD)).contains(
      mainLocation.getString(CODE_FIELD), thirdFloorLocation.getString(CODE_FIELD));
    assertThat(materialTypeIdsOfItems(data)).contains(materialTypeId, secondMaterialTypeId);
  }

  private static Map<String, String> deletionWindowParams(LocalDateTime deletionTime) {
    var windowParams = new HashMap<String, String>();
    windowParams.put(START_DATE_PARAM, OffsetDateTime.of(deletionTime.minusSeconds(2), ZoneOffset.UTC).toString());
    windowParams.put(END_DATE_PARAM, OffsetDateTime.of(deletionTime.plusSeconds(2), ZoneOffset.UTC).toString());
    windowParams.put("onlyInstanceUpdateDate", "false");
    return windowParams;
  }

  private static void awaitAtLeast5SecondsAfterFixtureCreation() {
    var fixtureCreationTime = LocalDateTime.now(ZoneOffset.UTC);
    Awaitility.await().until(() -> fixtureCreationTime.plusSeconds(5).isBefore(LocalDateTime.now(ZoneOffset.UTC)));
  }

  private static OffsetDateTime offsetDateTime(int year) {
    return OffsetDateTime.of(LocalDateTime.of(year, 1, 1, 0, 0, 0), ZoneOffset.UTC);
  }

  private String createHoldingWithLocations(String permanentLocationId, String temporaryLocationId) {
    var builder = new HoldingRequestBuilder()
      .forInstance(UUID.fromString(instanceId))
      .withPermanentLocation(UUID.fromString(permanentLocationId))
      .withSource(UUID.fromString(createHoldingsRecordsSource(client)));
    if (temporaryLocationId != null) {
      builder = builder.withTemporaryLocation(UUID.fromString(temporaryLocationId));
    }
    return createHolding(client, builder);
  }

  private static ItemRequestBuilder itemRequest(String holdingId, int order, String barcode, String callNumber,
                                                 String materialTypeId, String temporaryLocationId) {
    return new ItemRequestBuilder()
      .forHolding(UUID.fromString(holdingId))
      .withOrder(order)
      .withPermanentLoanType(UUID.fromString(loanTypeId))
      .withTemporaryLocation(UUID.fromString(temporaryLocationId))
      .withBarcode(barcode)
      .withItemLevelCallNumber(callNumber)
      .withMaterialType(UUID.fromString(materialTypeId));
  }

  private static String createItem(ItemRequestBuilder builder) {
    var response = get(doPost(client, ResourcePaths.ITEMS, builder.create()));
    assertThat(response.status()).isEqualTo(SC_CREATED);
    return response.bodyAsClass(Item.class).getId();
  }

  private static void createBoundWithPart(String holdingId, String itemId) {
    var request = new BoundWithPartBuilder(UUID.fromString(holdingId), UUID.fromString(itemId)).create();
    var response = get(doPost(client, ResourcePaths.BOUND_WITH_PARTS, request));
    assertThat(response.status()).isEqualTo(SC_CREATED);
  }

  private static JsonObject getJson(String path) {
    return get(doGet(client, path)).jsonBody();
  }

  private static Optional<JsonObject> getInventoryHierarchyInstance(Map<String, String> queryParams) {
    return requestUpdatedInstanceIds(queryParams)
      .flatMap(idsJson -> requestItemsAndHoldings(
        List.of(idsJson.getString(INSTANCE_ID_FIELD)),
        Boolean.parseBoolean(queryParams.get(QUERY_PARAM_SKIP_SUPPRESSED))));
  }

  private static Optional<JsonObject> requestUpdatedInstanceIds(Map<String, String> queryParams) {
    var response = requestUpdatedInstanceIdsRaw(queryParams);
    assertThat(response.status()).isEqualTo(SC_OK);
    return parseSingleObject(response);
  }

  private static TestResponse requestUpdatedInstanceIdsRaw(Map<String, String> queryParams) {
    var query = queryParams.entrySet().stream()
      .map(e -> e.getKey() + "=" + e.getValue())
      .collect(Collectors.joining("&"));
    return get(doGet(client, ResourcePaths.INVENTORY_HIERARCHY_UPDATED_INSTANCE_IDS + "?" + query));
  }

  private static Optional<JsonObject> requestItemsAndHoldings(List<String> instanceIds, boolean skipSuppressed) {
    var response = requestItemsAndHoldingsRaw(instanceIds, skipSuppressed);
    assertThat(response.status()).isEqualTo(SC_OK);
    return parseSingleObject(response);
  }

  private static TestResponse requestItemsAndHoldingsRaw(List<String> instanceIds, boolean skipSuppressed) {
    var payload = new InventoryHierarchyInstanceIds()
      .withInstanceIds(instanceIds)
      .withSkipSuppressedFromDiscoveryRecords(skipSuppressed);
    return get(doPost(client, ResourcePaths.INVENTORY_HIERARCHY_ITEMS_AND_HOLDINGS, pojo2JsonObject(payload)));
  }

  private static Optional<JsonObject> parseSingleObject(TestResponse response) {
    var body = response.body().toString();
    return body.isBlank() ? Optional.empty() : Optional.of(new JsonObject(body));
  }

  private static JsonArray items(JsonObject instanceData) {
    return instanceData.getJsonArray(ITEMS_FIELD);
  }

  private static JsonArray holdings(JsonObject instanceData) {
    return instanceData.getJsonArray(HOLDINGS_FIELD);
  }

  private static List<JsonObject> asObjects(JsonArray array) {
    return array.stream().map(JsonObject.class::cast).toList();
  }

  private static List<String> callNumbersOfItems(JsonObject instanceData) {
    return asObjects(items(instanceData)).stream()
      .map(item -> item.getJsonObject(CALL_NUMBER_FIELD).getString(CALL_NUMBER_FIELD))
      .toList();
  }

  private static List<Integer> ordersOfItems(JsonObject instanceData) {
    return asObjects(items(instanceData)).stream().map(item -> item.getInteger("order")).toList();
  }

  private static List<String> materialTypeIdsOfItems(JsonObject instanceData) {
    return asObjects(items(instanceData)).stream().map(item -> item.getString("materialTypeId")).toList();
  }

  private static String itemLocationField(JsonObject item, String which, String field) {
    return item.getJsonObject(LOCATION_FIELD).getJsonObject(which).getString(field);
  }

  private static List<String> itemLocationFields(JsonObject instanceData, String which, String field) {
    return asObjects(items(instanceData)).stream().map(item -> itemLocationField(item, which, field)).toList();
  }

  private static String holdingLocationField(JsonObject holding, String which, String field) {
    return holding.getJsonObject(LOCATION_FIELD).getJsonObject(which).getString(field);
  }
}
