package org.folio.rest.impl;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.rest.impl.HoldingsStorageFixtures.createHolding;
import static org.folio.rest.impl.HoldingsStorageFixtures.createHoldingsRecordsSource;
import static org.folio.rest.impl.HoldingsStorageFixtures.createLoanType;
import static org.folio.rest.impl.HoldingsStorageFixtures.createMaterialType;
import static org.folio.rest.impl.InstanceStorageFixtures.createInstance;
import static org.folio.rest.impl.InstanceStorageFixtures.createInstanceType;
import static org.folio.rest.impl.LocationStorageFixtures.createCampus;
import static org.folio.rest.impl.LocationStorageFixtures.createInstitution;
import static org.folio.rest.impl.LocationStorageFixtures.createLibrary;
import static org.folio.rest.impl.LocationStorageFixtures.createLocation;
import static org.folio.rest.impl.LocationStorageFixtures.createServicePoint;

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
import org.folio.rest.jaxrs.model.OaiPmhInstanceIds;
import org.folio.rest.support.builders.HoldingRequestBuilder;
import org.folio.rest.support.builders.ItemRequestBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers the {@code /oai-pmh-view} endpoints (used by the OAI-PMH harvesting flow): the combined
 * {@code /instances} view, and its newer split form ({@code /updatedInstanceIds} +
 * {@code /enrichedInstances}) which is exercised through the same fixtures for parity.
 */
class OaiPmhViewIT extends BaseIntegrationTest {

  private static final String QUERY_PARAM_SKIP_SUPPRESSED = "skipSuppressedFromDiscoveryRecords";
  private static final String START_DATE_PARAM = "startDate";
  private static final String END_DATE_PARAM = "endDate";
  private static final String DELETED_RECORD_SUPPORT_PARAM = "deletedRecordSupport";
  private static final String INSTANCE_ID_FIELD = "instanceid";
  private static final String ITEMS_AND_HOLDINGS_FIELDS = "itemsandholdingsfields";
  private static final String DELETED_FIELD = "deleted";
  private static final String ITEMS_FIELD = "items";
  private static final String LOCATION_FIELD = "location";
  private static final String CALL_NUMBER_FIELD = "callNumber";
  private static final String INSTITUTION_NAME_FIELD = "institutionName";
  private static final String BARCODE_FIELD = "barcode";
  private static final String ELECTRONIC_ACCESS_FIELD = "electronicAccess";
  private static final String URI_FIELD = "uri";
  private static final String NAME_FIELD = "name";
  private static final String CALL_NUMBER_1 = "item effective call number 1";
  private static final String CALL_NUMBER_2 = "item effective call number 2";
  private static final String CALL_NUMBER_3 = "item effective call number 3";

  private static String instanceTypeId;
  private static String materialTypeId;
  private static String secondMaterialTypeId;
  private static String loanTypeId;
  private static String institutionName;
  private static String mainLocationId;
  private static String thirdFloorLocationId;

  private Map<String, String> params;
  private String instanceId;
  private String holdingId;

  @BeforeAll
  static void seedReferenceData() {
    instanceTypeId = createInstanceType(client);
    materialTypeId = createMaterialType(client);
    secondMaterialTypeId = createMaterialType(client);
    loanTypeId = createLoanType(client);

    var institutionId = createInstitution(client);
    var campusId = createCampus(client, institutionId);
    var libraryId = createLibrary(client, campusId);
    var servicePointId = createServicePoint(client);
    mainLocationId = createLocation(client, institutionId, campusId, libraryId, servicePointId);
    thirdFloorLocationId = createLocation(client, institutionId, campusId, libraryId, servicePointId);

    institutionName = get(doGet(client, ResourcePaths.LOCATION_UNITS_INSTITUTIONS + "/" + institutionId))
      .jsonBody().getString(NAME_FIELD);
  }

  @BeforeEach
  void createFixtures() {
    runQuery("TRUNCATE TABLE instance, holdings_record, item, audit_instance, audit_holdings_record, "
      + "audit_item CASCADE");
    params = new HashMap<>();

    instanceId = createInstance(client, "an instance", instanceTypeId);
    holdingId = createHolding(client, instanceId, mainLocationId);

    createItem(itemRequest(holdingId, "item barcode 1", CALL_NUMBER_1, materialTypeId, mainLocationId));
    createItem(itemRequest(holdingId, "item barcode 2", CALL_NUMBER_2, secondMaterialTypeId, thirdFloorLocationId));
  }

  @Test
  @DisplayName("should return the instance with its items and holdings data when requesting without parameters")
  void shouldReturnInstance_whenRequestingWithoutParameters() {
    params.put(QUERY_PARAM_SKIP_SUPPRESSED, "false");

    assertDefaultItemsData(requestOaiPmhViewInstances(params).orElseThrow());
    assertDefaultItemsData(getOaiPmhViewInstance(params).orElseThrow());
  }

  @Test
  @DisplayName("should return no data when the database is empty")
  void shouldReturnNoData_whenDatabaseIsEmpty() {
    runQuery("TRUNCATE TABLE instance, holdings_record, item CASCADE");

    assertThat(requestOaiPmhViewInstances(params)).isEmpty();
    assertThat(getOaiPmhViewInstance(params)).isEmpty();
  }

  @Test
  @DisplayName("should mark an instance as deleted only when deletedRecordSupport is requested")
  void shouldMarkInstanceDeleted_onlyWhenDeletedRecordSupportRequested() {
    // a real per-row DELETE, not TRUNCATE: deletedRecordSupport is served from the audit tables,
    // which only the row-level AFTER DELETE triggers populate - TRUNCATE bypasses those entirely.
    runQuery("DELETE FROM item");
    runQuery("DELETE FROM holdings_record");
    runQuery("DELETE FROM instance");

    params.put(DELETED_RECORD_SUPPORT_PARAM, "true");
    assertThat(Boolean.parseBoolean(requestOaiPmhViewInstances(params).orElseThrow().getString(DELETED_FIELD)))
      .isTrue();
    assertThat(Boolean.parseBoolean(requestUpdatedInstanceIds(params).orElseThrow().getString(DELETED_FIELD)))
      .isTrue();

    params.put(DELETED_RECORD_SUPPORT_PARAM, "false");
    assertThat(requestOaiPmhViewInstances(params)).isEmpty();
    assertThat(requestUpdatedInstanceIds(params)).isEmpty();
  }

  @Test
  @DisplayName("should filter results by the requested date range")
  void shouldFilterByDates() {
    params.put(QUERY_PARAM_SKIP_SUPPRESSED, "false");

    setStartDate(2000);
    assertOaiPmhViewReturnsExpectedData();

    setEndDate(2500);
    assertOaiPmhViewReturnsExpectedData();

    setStartDate(2050);
    assertOaiPmhViewReturnsNoData();

    setEndDate(2000);
    assertOaiPmhViewReturnsNoData();

    setDateRange(2000, 2050);
    assertOaiPmhViewReturnsExpectedData();

    setDateRange(2000, 2001);
    assertOaiPmhViewReturnsNoData();
  }

  @Test
  @DisplayName("should skip or include discovery-suppressed items based on the query parameter")
  void shouldSkipOrIncludeSuppressedItems_basedOnQueryParameter() {
    createItem(itemRequest(holdingId, "item barcode 3", CALL_NUMBER_3, secondMaterialTypeId, thirdFloorLocationId)
      .withDiscoverySuppress(true));

    params.put(QUERY_PARAM_SKIP_SUPPRESSED, "true");
    assertDefaultItemsData(requestOaiPmhViewInstances(params).orElseThrow());
    assertDefaultItemsData(getOaiPmhViewInstance(params).orElseThrow());

    params.put(QUERY_PARAM_SKIP_SUPPRESSED, "false");
    var withSuppressed = itemsAndHoldings(requestOaiPmhViewInstances(params).orElseThrow());
    assertThat(callNumbersOfItems(withSuppressed)).containsExactlyInAnyOrder(
      CALL_NUMBER_1, CALL_NUMBER_2, CALL_NUMBER_3);
    assertThat(items(withSuppressed)).hasSize(3);

    var withSuppressedEnriched = itemsAndHoldings(getOaiPmhViewInstance(params).orElseThrow());
    assertThat(callNumbersOfItems(withSuppressedEnriched)).containsExactlyInAnyOrder(
      CALL_NUMBER_1, CALL_NUMBER_2, CALL_NUMBER_3);
    assertThat(items(withSuppressedEnriched)).hasSize(3);
  }

  @Test
  @DisplayName("should preserve the entered order of a holding's electronic access entries on the merged item")
  void shouldPreserveElectronicAccessOrder() {
    var electronicAccess = new JsonArray()
      .add(new JsonObject().put(URI_FIELD, "http://electronicAccess-c-entered-first"))
      .add(new JsonObject().put(URI_FIELD, "http://electronicAccess-z-entered-second"))
      .add(new JsonObject().put(URI_FIELD, "http://electronicAccess-a-entered-third"));
    var electronicAccessHoldingId = createHolding(client, new HoldingRequestBuilder()
      .forInstance(UUID.fromString(instanceId))
      .withPermanentLocation(UUID.fromString(mainLocationId))
      .withSource(UUID.fromString(createHoldingsRecordsSource(client)))
      .withElectronicAccess(electronicAccess));
    var barcode = "item barcode 3";
    createItem(itemRequest(electronicAccessHoldingId, barcode, CALL_NUMBER_3, secondMaterialTypeId,
      thirdFloorLocationId));

    var data = itemsAndHoldings(requestOaiPmhViewInstances(params).orElseThrow());
    var item = asObjects(items(data)).stream()
      .filter(itemJson -> barcode.equals(itemJson.getString(BARCODE_FIELD)))
      .findFirst().orElseThrow();

    var expected = new JsonArray()
      .add(JsonObject.of(URI_FIELD, "http://electronicAccess-c-entered-first", NAME_FIELD, null))
      .add(JsonObject.of(URI_FIELD, "http://electronicAccess-z-entered-second", NAME_FIELD, null))
      .add(JsonObject.of(URI_FIELD, "http://electronicAccess-a-entered-third", NAME_FIELD, null));
    assertThat(item.getJsonArray(ELECTRONIC_ACCESS_FIELD)).isEqualTo(expected);
  }

  @Test
  @DisplayName("should return 400 when the start date is invalid")
  void shouldReturn400_whenStartDateIsInvalid() {
    params.put(START_DATE_PARAM, "invalidDate");

    assertThat(requestOaiPmhViewInstancesRaw(params).status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(requestUpdatedInstanceIdsRaw(params).status()).isEqualTo(SC_BAD_REQUEST);
  }

  @Test
  @DisplayName("should return 400 when the end date is invalid")
  void shouldReturn400_whenEndDateIsInvalid() {
    params.put(END_DATE_PARAM, "invalidDate");

    assertThat(requestOaiPmhViewInstancesRaw(params).status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(requestUpdatedInstanceIdsRaw(params).status()).isEqualTo(SC_BAD_REQUEST);
  }

  private void assertOaiPmhViewReturnsExpectedData() {
    assertDefaultItemsData(requestOaiPmhViewInstances(params).orElseThrow());
    assertDefaultItemsData(getOaiPmhViewInstance(params).orElseThrow());
  }

  private void assertOaiPmhViewReturnsNoData() {
    assertThat(requestOaiPmhViewInstances(params)).isEmpty();
    assertThat(getOaiPmhViewInstance(params)).isEmpty();
  }

  private void setStartDate(int year) {
    params.put(START_DATE_PARAM, offsetDateTime(year).toString());
  }

  private void setEndDate(int year) {
    params.put(END_DATE_PARAM, offsetDateTime(year).toString());
  }

  private void setDateRange(int startYear, int endYear) {
    setStartDate(startYear);
    setEndDate(endYear);
  }

  private static OffsetDateTime offsetDateTime(int year) {
    return OffsetDateTime.of(LocalDateTime.of(year, 1, 1, 0, 0, 0), ZoneOffset.UTC);
  }

  private static void assertDefaultItemsData(JsonObject data) {
    var itemsAndHoldingsData = itemsAndHoldings(data);
    assertThat(callNumbersOfItems(itemsAndHoldingsData)).containsExactlyInAnyOrder(CALL_NUMBER_1, CALL_NUMBER_2);
    assertThat(items(itemsAndHoldingsData)).hasSize(2);
    assertThat(institutionNamesOfItems(itemsAndHoldingsData)).containsOnly(institutionName);
  }

  private static ItemRequestBuilder itemRequest(String holdingId, String barcode, String callNumber,
                                                 String materialTypeId, String temporaryLocationId) {
    return new ItemRequestBuilder()
      .forHolding(UUID.fromString(holdingId))
      .withPermanentLoanType(UUID.fromString(loanTypeId))
      .withTemporaryLocation(UUID.fromString(temporaryLocationId))
      .withBarcode(barcode)
      .withItemLevelCallNumber(callNumber)
      .withMaterialType(UUID.fromString(materialTypeId));
  }

  private static void createItem(ItemRequestBuilder builder) {
    var response = get(doPost(client, ResourcePaths.ITEMS, builder.create()));
    assertThat(response.status()).isEqualTo(SC_CREATED);
  }

  private static Optional<JsonObject> getOaiPmhViewInstance(Map<String, String> queryParams) {
    return requestUpdatedInstanceIds(queryParams)
      .flatMap(idsJson -> requestEnrichedInstances(
        List.of(idsJson.getString(INSTANCE_ID_FIELD)),
        Boolean.parseBoolean(queryParams.get(QUERY_PARAM_SKIP_SUPPRESSED))));
  }

  private static Optional<JsonObject> requestOaiPmhViewInstances(Map<String, String> queryParams) {
    var response = requestOaiPmhViewInstancesRaw(queryParams);
    assertThat(response.status()).isEqualTo(SC_OK);
    return parseSingleObject(response);
  }

  private static TestResponse requestOaiPmhViewInstancesRaw(Map<String, String> queryParams) {
    return get(doGet(client, ResourcePaths.OAI_PMH_VIEW_INSTANCES + "?" + toQueryString(queryParams)));
  }

  private static Optional<JsonObject> requestUpdatedInstanceIds(Map<String, String> queryParams) {
    var response = requestUpdatedInstanceIdsRaw(queryParams);
    assertThat(response.status()).isEqualTo(SC_OK);
    return parseSingleObject(response);
  }

  private static TestResponse requestUpdatedInstanceIdsRaw(Map<String, String> queryParams) {
    return get(doGet(client, ResourcePaths.OAI_PMH_VIEW_UPDATED_INSTANCE_IDS + "?" + toQueryString(queryParams)));
  }

  private static Optional<JsonObject> requestEnrichedInstances(List<String> instanceIds, boolean skipSuppressed) {
    var payload = new OaiPmhInstanceIds()
      .withInstanceIds(instanceIds)
      .withSkipSuppressedFromDiscoveryRecords(skipSuppressed);
    var response = get(doPost(client, ResourcePaths.OAI_PMH_VIEW_ENRICHED_INSTANCES, pojo2JsonObject(payload)));
    assertThat(response.status()).isEqualTo(SC_OK);
    return parseSingleObject(response);
  }

  private static String toQueryString(Map<String, String> queryParams) {
    return queryParams.entrySet().stream()
      .map(e -> e.getKey() + "=" + e.getValue())
      .collect(Collectors.joining("&"));
  }

  private static Optional<JsonObject> parseSingleObject(TestResponse response) {
    var body = response.body().toString();
    return body.isBlank() ? Optional.empty() : Optional.of(new JsonObject(body));
  }

  private static JsonObject itemsAndHoldings(JsonObject data) {
    return data.getJsonObject(ITEMS_AND_HOLDINGS_FIELDS);
  }

  private static JsonArray items(JsonObject itemsAndHoldingsData) {
    return itemsAndHoldingsData.getJsonArray(ITEMS_FIELD);
  }

  private static List<JsonObject> asObjects(JsonArray array) {
    return array.stream().map(JsonObject.class::cast).toList();
  }

  private static List<String> callNumbersOfItems(JsonObject itemsAndHoldingsData) {
    return asObjects(items(itemsAndHoldingsData)).stream()
      .map(item -> item.getJsonObject(CALL_NUMBER_FIELD).getString(CALL_NUMBER_FIELD))
      .toList();
  }

  private static List<String> institutionNamesOfItems(JsonObject itemsAndHoldingsData) {
    return asObjects(items(itemsAndHoldingsData)).stream()
      .map(item -> item.getJsonObject(LOCATION_FIELD).getJsonObject(LOCATION_FIELD)
        .getString(INSTITUTION_NAME_FIELD))
      .toList();
  }
}
