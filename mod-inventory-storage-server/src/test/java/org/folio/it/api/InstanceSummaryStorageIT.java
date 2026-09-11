package org.folio.it.api;

import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.it.HoldingsStorageFixtures.createCallNumberType;
import static org.folio.it.HoldingsStorageFixtures.createHolding;
import static org.folio.it.HoldingsStorageFixtures.createLoanType;
import static org.folio.it.HoldingsStorageFixtures.createMaterialType;
import static org.folio.it.InstanceStorageFixtures.createInstanceType;
import static org.folio.it.LocationStorageFixtures.createLocation;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.UUID;
import org.folio.it.BaseIntegrationTest;
import org.folio.it.HoldingsStorageFixtures;
import org.folio.rest.jaxrs.model.InstanceFormat;
import org.folio.rest.jaxrs.model.IssuanceMode;
import org.folio.rest.jaxrs.model.NatureOfContentTerm;
import org.folio.support.ResourcePaths;
import org.folio.support.builders.HoldingRequestBuilder;
import org.folio.support.builders.ItemRequestBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InstanceSummaryStorageIT extends BaseIntegrationTest {

  private static final String LC_CALL_NUMBER_TYPE_ID = "95467209-6d7b-468b-94df-0f5d7ad2747d";
  private static final String ID_FIELD = "id";
  private static final String NAME_FIELD = "name";
  private static final String URI_FIELD = "uri";
  private static final String TITLE_FIELD = "title";
  private static final String SOURCE_FIELD = "source";
  private static final String INSTANCE_TYPE_ID_FIELD = "instanceTypeId";
  private static final String ELECTRONIC_ACCESS_FIELD = "electronicAccess";
  private static final String INSTANCE_FORMAT_IDS_FIELD = "instanceFormatIds";
  private static final String NATURE_OF_CONTENT_TERM_IDS_FIELD = "natureOfContentTermIds";
  private static final String MODE_OF_ISSUANCE_ID_FIELD = "modeOfIssuanceId";
  private static final String INSTANCE_FIELD = "instance";
  private static final String IS_BOUND_WITH_FIELD = "isBoundWith";
  private static final String RECORD_COUNTS_FIELD = "recordCounts";
  private static final String HOLDINGS_FIELD = "holdings";
  private static final String ITEMS_FIELD = "items";
  private static final String TOTAL_FIELD = "total";
  private static final String SUPPRESSED_FROM_DISCOVERY_FIELD = "suppressedFromDiscovery";
  private static final String SUPPRESSED_BY_HOLDINGS_FIELD = "suppressedByHoldings";
  private static final String SUPPRESSED_FROM_DISCOVERY_OR_BY_HOLDINGS_FIELD = "suppressedFromDiscoveryOrByHoldings";
  private static final String NOT_SUPPRESSED_FROM_DISCOVERY_FIELD = "notSuppressedFromDiscovery";
  private static final String AGGREGATES_FIELD = "aggregates";
  private static final String ALL_RECORDS_SCOPE = "allRecords";
  private static final String NOT_SUPPRESSED_SCOPE = "notSuppressedFromDiscoveryRecords";
  private static final String ITEM_DERIVED_FIELDS_FIELD = "itemDerivedFields";
  private static final String EFFECTIVE_SHELVING_ORDER_FIELD = "effectiveShelvingOrder";
  private static final String REFERENCE_VALUES_FIELD = "referenceValues";
  private static final String ITEM_MATERIAL_TYPES_FIELD = "itemMaterialTypes";
  private static final String INSTANCE_TYPE_FIELD = "instanceType";
  private static final String MODE_OF_ISSUANCE_FIELD = "modeOfIssuance";
  private static final String INSTANCE_FORMATS_FIELD = "instanceFormats";
  private static final String NATURE_OF_CONTENT_TERMS_FIELD = "natureOfContentTerms";

  private static String instanceTypeId;
  private static String mainLocationId;
  private static String secondLocationId;
  private static String loanTypeId;
  private static String journalMaterialTypeId;
  private static String bookMaterialTypeId;
  private static String lcCallNumberTypeId;

  @BeforeAll
  static void seedReferenceData() {
    instanceTypeId = createInstanceType(client);
    mainLocationId = createLocation(client);
    secondLocationId = createLocation(client);
    loanTypeId = createLoanType(client);
    journalMaterialTypeId = createMaterialType(client, "journal");
    bookMaterialTypeId = createMaterialType(client, "book");
    lcCallNumberTypeId = createCallNumberType(client, LC_CALL_NUMBER_TYPE_ID, "Library of Congress classification");
  }

  @BeforeEach
  void clearData() {
    runQuery("TRUNCATE TABLE bound_with_part, item, holdings_record, instance CASCADE");
  }

  @Test
  @DisplayName("should return an instance summary for an instance with a holding and an item")
  void shouldReturnInstanceSummary_forInstanceWithHoldingAndItem() {
    var instanceId = createInstance("Summary instance");
    var holdingId = createHolding(client, instanceId, mainLocationId);
    createItem(holdingId, journalMaterialTypeId);

    var summary = getSummary(instanceId);

    assertThat(summary.getJsonObject(INSTANCE_FIELD).getString(ID_FIELD)).isEqualTo(instanceId);
    assertThat(summary.getBoolean(IS_BOUND_WITH_FIELD)).isFalse();

    var recordCounts = summary.getJsonObject(RECORD_COUNTS_FIELD);
    assertHoldingsCounts(recordCounts, 1, 0, 1);
    assertItemsCounts(recordCounts, 1, 0, 0, 0, 1);

    var referenceValues = summary.getJsonObject(REFERENCE_VALUES_FIELD);
    assertThat(referenceValues.getJsonObject(INSTANCE_TYPE_FIELD).getString(ID_FIELD)).isEqualTo(instanceTypeId);
    assertThat(referenceValues.getJsonObject(INSTANCE_TYPE_FIELD).containsKey(NAME_FIELD)).isTrue();
  }

  @Test
  @DisplayName("should return visibility-aware record counts and aggregates when some holdings and items are "
    + "suppressed")
  void shouldReturnVisibilityAwareRecordCountsAndAggregates_whenSomeHoldingsAndItemsAreSuppressed() {
    var instanceId = createInstance("Summary instance");
    var visibleHoldingId = createHolding(client, instanceId, mainLocationId);
    createItemWithCallNumber(visibleHoldingId, journalMaterialTypeId, "PN2 .A69");
    createItemWithCallNumber(visibleHoldingId, journalMaterialTypeId, "PN2 .A6 1999");
    createSuppressedItem(visibleHoldingId, bookMaterialTypeId);
    var suppressedHoldingId = createSuppressedHolding(instanceId);
    createItem(suppressedHoldingId, bookMaterialTypeId);

    var summary = getSummary(instanceId);
    var recordCounts = summary.getJsonObject(RECORD_COUNTS_FIELD);

    assertHoldingsCounts(recordCounts, 2, 1, 1);
    assertItemsCounts(recordCounts, 4, 1, 1, 2, 2);

    var aggregates = summary.getJsonObject(AGGREGATES_FIELD);
    assertThat(itemDerivedFields(aggregates, ALL_RECORDS_SCOPE).getString(EFFECTIVE_SHELVING_ORDER_FIELD))
      .isEqualTo("PN 12 A6 41999");
    assertThat(itemDerivedFields(aggregates, NOT_SUPPRESSED_SCOPE).getString(EFFECTIVE_SHELVING_ORDER_FIELD))
      .isEqualTo("PN 12 A6 41999");

    assertThat(names(materialTypes(aggregates, ALL_RECORDS_SCOPE))).containsExactlyInAnyOrder("book", "journal");
    assertThat(names(materialTypes(aggregates, NOT_SUPPRESSED_SCOPE))).containsExactly("journal");
  }

  @Test
  @DisplayName("should summarize a bound-with item shared across holdings")
  void shouldSummarizeBoundWithItem_sharedAcrossHoldings() {
    var instanceId = createInstance("Main instance");
    var boundItemInstanceId = createInstance("Bound item instance");
    var holdingId = createHolding(client, instanceId, mainLocationId);
    var boundItemHoldingId = createHolding(client, boundItemInstanceId, secondLocationId);
    var boundItemId = createItem(boundItemHoldingId, bookMaterialTypeId);
    createBoundWithPart(holdingId, boundItemId);

    var summary = getSummary(instanceId);

    assertThat(summary.getBoolean(IS_BOUND_WITH_FIELD)).isTrue();
    assertItemsCounts(summary.getJsonObject(RECORD_COUNTS_FIELD), 1, 0, 0, 0, 1);
    assertThat(names(materialTypes(summary.getJsonObject(AGGREGATES_FIELD), ALL_RECORDS_SCOPE)))
      .containsExactly("book");
  }

  @Test
  @DisplayName("should aggregate electronic access entries by visibility scope")
  void shouldAggregateElectronicAccess_byVisibilityScope() {
    var instanceId = createInstance("Summary instance", new JsonObject().put(ELECTRONIC_ACCESS_FIELD,
      electronicAccessJson("https://example.org/instance", "https://example.org/duplicate")));
    createElectronicAccessHoldingsAndItems(instanceId);

    var aggregates = getSummary(instanceId).getJsonObject(AGGREGATES_FIELD);

    assertThat(uris(electronicAccess(aggregates, ALL_RECORDS_SCOPE))).containsExactlyInAnyOrder(
      "https://example.org/instance", "https://example.org/duplicate",
      "https://example.org/item-visible", "https://example.org/item-suppressed",
      "https://example.org/item-suppressed-by-holding", "https://example.org/holding-visible",
      "https://example.org/holding-suppressed");
    assertThat(uris(electronicAccess(aggregates, NOT_SUPPRESSED_SCOPE))).containsExactlyInAnyOrder(
      "https://example.org/instance", "https://example.org/duplicate",
      "https://example.org/item-visible", "https://example.org/holding-visible");
  }

  @Test
  @DisplayName("should return instance reference values for formats, mode of issuance, and nature of content terms")
  void shouldReturnInstanceReferenceValues_forFormatsModeOfIssuanceAndNatureOfContentTerms() {
    var suffix = UUID.randomUUID().toString();
    var audioFormatId = createInstanceFormat("Audio carrier " + suffix);
    var textFormatId = createInstanceFormat("Text carrier " + suffix);
    var modeOfIssuanceId = createModeOfIssuance("Monographic unit " + suffix);
    var bibliographyTermId = createNatureOfContentTerm("Bibliography " + suffix);
    var thesisTermId = createNatureOfContentTerm("Thesis " + suffix);

    var instanceId = createInstance("Summary instance", new JsonObject()
      .put(INSTANCE_FORMAT_IDS_FIELD, JsonArray.of(textFormatId, audioFormatId))
      .put(MODE_OF_ISSUANCE_ID_FIELD, modeOfIssuanceId)
      .put(NATURE_OF_CONTENT_TERM_IDS_FIELD, JsonArray.of(thesisTermId, bibliographyTermId)));

    var referenceValues = getSummary(instanceId).getJsonObject(REFERENCE_VALUES_FIELD);

    assertThat(referenceValues.getJsonObject(MODE_OF_ISSUANCE_FIELD).getString(ID_FIELD)).isEqualTo(modeOfIssuanceId);
    assertThat(referenceValues.getJsonObject(MODE_OF_ISSUANCE_FIELD).getString(NAME_FIELD))
      .isEqualTo("Monographic unit " + suffix);
    assertThat(names(referenceValues.getJsonArray(INSTANCE_FORMATS_FIELD)))
      .containsExactly("Audio carrier " + suffix, "Text carrier " + suffix);
    assertThat(names(referenceValues.getJsonArray(NATURE_OF_CONTENT_TERMS_FIELD)))
      .containsExactly("Bibliography " + suffix, "Thesis " + suffix);
  }

  @Test
  @DisplayName("should return an empty summary for an instance without holdings or items")
  void shouldReturnEmptySummary_whenInstanceHasNoHoldingsOrItems() {
    var instanceId = createInstance("Summary instance");

    var summary = getSummary(instanceId);
    var recordCounts = summary.getJsonObject(RECORD_COUNTS_FIELD);

    assertThat(summary.getBoolean(IS_BOUND_WITH_FIELD)).isFalse();
    assertHoldingsCounts(recordCounts, 0, 0, 0);
    assertItemsCounts(recordCounts, 0, 0, 0, 0, 0);
    assertEmptyAggregateScope(summary, ALL_RECORDS_SCOPE);
    assertEmptyAggregateScope(summary, NOT_SUPPRESSED_SCOPE);
    assertThat(summary.getJsonObject(REFERENCE_VALUES_FIELD).containsKey(MODE_OF_ISSUANCE_FIELD)).isFalse();
  }

  @Test
  @DisplayName("should return 404 when the instance does not exist")
  void shouldReturn404_whenInstanceDoesNotExist() {
    var response = await(doGet(client, ResourcePaths.INSTANCES + "/" + UUID.randomUUID() + "/summary"));

    assertThat(response.status()).isEqualTo(SC_NOT_FOUND);
  }

  private static void assertHoldingsCounts(JsonObject recordCounts, int total, int suppressed, int notSuppressed) {
    var counts = recordCounts.getJsonObject(HOLDINGS_FIELD);
    assertThat(counts.getInteger(TOTAL_FIELD)).isEqualTo(total);
    assertThat(counts.getInteger(SUPPRESSED_FROM_DISCOVERY_FIELD)).isEqualTo(suppressed);
    assertThat(counts.getInteger(NOT_SUPPRESSED_FROM_DISCOVERY_FIELD)).isEqualTo(notSuppressed);
  }

  private static void assertItemsCounts(JsonObject recordCounts, int total, int suppressedFromDiscovery,
                                         int suppressedByHoldings, int suppressedFromDiscoveryOrByHoldings,
                                         int notSuppressedFromDiscovery) {
    var counts = recordCounts.getJsonObject(ITEMS_FIELD);
    assertThat(counts.getInteger(TOTAL_FIELD)).isEqualTo(total);
    assertThat(counts.getInteger(SUPPRESSED_FROM_DISCOVERY_FIELD)).isEqualTo(suppressedFromDiscovery);
    assertThat(counts.getInteger(SUPPRESSED_BY_HOLDINGS_FIELD)).isEqualTo(suppressedByHoldings);
    assertThat(counts.getInteger(SUPPRESSED_FROM_DISCOVERY_OR_BY_HOLDINGS_FIELD))
      .isEqualTo(suppressedFromDiscoveryOrByHoldings);
    assertThat(counts.getInteger(NOT_SUPPRESSED_FROM_DISCOVERY_FIELD)).isEqualTo(notSuppressedFromDiscovery);
  }

  private static void assertEmptyAggregateScope(JsonObject summary, String scope) {
    var aggregateScope = summary.getJsonObject(AGGREGATES_FIELD).getJsonObject(scope);

    assertThat(aggregateScope.getJsonArray(ELECTRONIC_ACCESS_FIELD)).isEmpty();
    assertThat(aggregateScope.getJsonObject(REFERENCE_VALUES_FIELD).getJsonArray(ITEM_MATERIAL_TYPES_FIELD)).isEmpty();
    assertThat(aggregateScope.getJsonObject(ITEM_DERIVED_FIELDS_FIELD).getString(EFFECTIVE_SHELVING_ORDER_FIELD))
      .isNull();
  }

  private static JsonObject itemDerivedFields(JsonObject aggregates, String scope) {
    return aggregates.getJsonObject(scope).getJsonObject(ITEM_DERIVED_FIELDS_FIELD);
  }

  private static JsonArray materialTypes(JsonObject aggregates, String scope) {
    return aggregates.getJsonObject(scope)
      .getJsonObject(REFERENCE_VALUES_FIELD)
      .getJsonArray(ITEM_MATERIAL_TYPES_FIELD);
  }

  private static JsonArray electronicAccess(JsonObject aggregates, String scope) {
    return aggregates.getJsonObject(scope).getJsonArray(ELECTRONIC_ACCESS_FIELD);
  }

  private static List<String> names(JsonArray referenceValues) {
    return referenceValues.stream()
      .map(JsonObject.class::cast)
      .map(value -> value.getString(NAME_FIELD))
      .toList();
  }

  private static List<String> uris(JsonArray electronicAccess) {
    return electronicAccess.stream()
      .map(JsonObject.class::cast)
      .map(value -> value.getString(URI_FIELD))
      .toList();
  }

  private static JsonArray electronicAccessJson(String... uris) {
    var array = new JsonArray();
    for (var uri : uris) {
      array.add(new JsonObject().put(URI_FIELD, uri));
    }
    return array;
  }

  private static void createElectronicAccessHoldingsAndItems(String instanceId) {
    var visibleHoldingId = HoldingsStorageFixtures.createHolding(client, new HoldingRequestBuilder()
      .forInstance(UUID.fromString(instanceId))
      .withPermanentLocation(UUID.fromString(mainLocationId))
      .withSource(UUID.fromString(HoldingsStorageFixtures.createHoldingsRecordsSource(client)))
      .withElectronicAccess(
        electronicAccessJson("https://example.org/holding-visible", "https://example.org/duplicate")));
    var suppressedHoldingId = HoldingsStorageFixtures.createHolding(client, new HoldingRequestBuilder()
      .forInstance(UUID.fromString(instanceId))
      .withPermanentLocation(UUID.fromString(secondLocationId))
      .withSource(UUID.fromString(HoldingsStorageFixtures.createHoldingsRecordsSource(client)))
      .withDiscoverySuppress(true)
      .withElectronicAccess(electronicAccessJson("https://example.org/holding-suppressed")));

    createItemWithElectronicAccess(visibleHoldingId, false,
      "https://example.org/item-visible", "https://example.org/duplicate");
    createItemWithElectronicAccess(visibleHoldingId, true, "https://example.org/item-suppressed");
    createItemWithElectronicAccess(suppressedHoldingId, false, "https://example.org/item-suppressed-by-holding");
  }

  private static String createInstance(String title) {
    return createInstance(title, null);
  }

  private static String createInstance(String title, JsonObject extraFields) {
    var request = new JsonObject()
      .put(TITLE_FIELD, title)
      .put(SOURCE_FIELD, "TEST")
      .put(INSTANCE_TYPE_ID_FIELD, instanceTypeId);
    if (extraFields != null) {
      request.mergeIn(extraFields);
    }

    return await(doPost(client, ResourcePaths.INSTANCES, request)).jsonBody().getString(ID_FIELD);
  }

  private static String createSuppressedHolding(String instanceId) {
    return HoldingsStorageFixtures.createHolding(client, new HoldingRequestBuilder()
      .forInstance(UUID.fromString(instanceId))
      .withPermanentLocation(UUID.fromString(secondLocationId))
      .withSource(UUID.fromString(HoldingsStorageFixtures.createHoldingsRecordsSource(client)))
      .withDiscoverySuppress(true));
  }

  private static String createItem(String holdingId, String materialTypeId) {
    return HoldingsStorageFixtures.createItem(client, holdingId, materialTypeId, loanTypeId);
  }

  private static void createItemWithCallNumber(String holdingId, String materialTypeId, String callNumber) {
    var request = new ItemRequestBuilder()
      .forHolding(UUID.fromString(holdingId))
      .withMaterialType(UUID.fromString(materialTypeId))
      .withPermanentLoanType(UUID.fromString(loanTypeId))
      .withItemLevelCallNumber(callNumber)
      .withItemLevelCallNumberTypeId(lcCallNumberTypeId)
      .create();

    await(doPost(client, ResourcePaths.ITEMS, request));
  }

  private static void createSuppressedItem(String holdingId, String materialTypeId) {
    var request = new ItemRequestBuilder()
      .forHolding(UUID.fromString(holdingId))
      .withMaterialType(UUID.fromString(materialTypeId))
      .withPermanentLoanType(UUID.fromString(loanTypeId))
      .withDiscoverySuppress(true)
      .create();

    await(doPost(client, ResourcePaths.ITEMS, request));
  }

  private static void createItemWithElectronicAccess(String holdingId, boolean discoverySuppress, String... uris) {
    var request = new ItemRequestBuilder()
      .forHolding(UUID.fromString(holdingId))
      .withMaterialType(UUID.fromString(journalMaterialTypeId))
      .withPermanentLoanType(UUID.fromString(loanTypeId))
      .withDiscoverySuppress(discoverySuppress)
      .create()
      .put(ELECTRONIC_ACCESS_FIELD, electronicAccessJson(uris));

    await(doPost(client, ResourcePaths.ITEMS, request));
  }

  private static void createBoundWithPart(String holdingsRecordId, String itemId) {
    await(doPost(client, ResourcePaths.BOUND_WITH_PARTS, new JsonObject()
      .put("holdingsRecordId", holdingsRecordId)
      .put("itemId", itemId)));
  }

  private static String createInstanceFormat(String name) {
    var id = UUID.randomUUID().toString();
    var instanceFormat = new InstanceFormat()
      .withId(id)
      .withName(name)
      .withCode("code-" + id)
      .withSource("test");

    await(doPost(client, ResourcePaths.INSTANCE_FORMATS, pojo2JsonObject(instanceFormat)));
    return id;
  }

  private static String createModeOfIssuance(String name) {
    var id = UUID.randomUUID().toString();
    var issuanceMode = new IssuanceMode()
      .withId(id)
      .withName(name)
      .withSource("test");

    await(doPost(client, ResourcePaths.MODES_OF_ISSUANCE, pojo2JsonObject(issuanceMode)));
    return id;
  }

  private static String createNatureOfContentTerm(String name) {
    var id = UUID.randomUUID().toString();
    var natureOfContentTerm = new NatureOfContentTerm()
      .withId(id)
      .withName(name)
      .withSource("test");

    await(doPost(client, ResourcePaths.NATURE_OF_CONTENT_TERMS, pojo2JsonObject(natureOfContentTerm)));
    return id;
  }

  private static JsonObject getSummary(String instanceId) {
    var response = await(doGet(client, ResourcePaths.INSTANCES + "/" + instanceId + "/summary"));
    assertThat(response.status()).isEqualTo(SC_OK);
    return response.jsonBody();
  }
}
