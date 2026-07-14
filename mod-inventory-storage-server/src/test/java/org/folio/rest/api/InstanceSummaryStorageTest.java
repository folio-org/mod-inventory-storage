package org.folio.rest.api;

import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.util.UUID.randomUUID;
import static org.folio.rest.support.ResponseHandler.json;
import static org.folio.rest.support.ResponseHandler.text;
import static org.folio.rest.support.http.InterfaceUrls.instanceFormatsUrl;
import static org.folio.rest.support.http.InterfaceUrls.instancesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.natureOfContentTermsUrl;
import static org.folio.services.CallNumberConstants.LC_CN_TYPE_ID;
import static org.folio.utility.ModuleUtility.getClient;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.folio.rest.jaxrs.model.ElectronicAccessItem;
import org.folio.rest.jaxrs.model.InstanceFormat;
import org.folio.rest.jaxrs.model.IssuanceMode;
import org.folio.rest.jaxrs.model.NatureOfContentTerm;
import org.folio.rest.support.Response;
import org.folio.rest.support.builders.BoundWithPartBuilder;
import org.folio.rest.support.builders.HoldingRequestBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class InstanceSummaryStorageTest extends TestBaseWithInventoryUtil {

  @Before
  public void beforeEach() {
    deleteAllById(boundWithClient);
    clearData();

    setupMaterialTypes();
    setupLoanTypes();
    setupLocations();

    removeAllEvents();
    WireMock.reset();
    mockUserTenantsForNonConsortiumMember();
  }

  @After
  public void afterEach() {
    deleteAllById(boundWithClient);
  }

  @Test
  public void shouldReturnInstanceSummary() {
    UUID instanceId = randomUUID();
    createInstanceRecord(instance(instanceId));

    UUID holdingId = createHolding(instanceId, MAIN_LIBRARY_LOCATION_ID, null);
    createItem(buildItem(holdingId, MAIN_LIBRARY_LOCATION_ID, null));

    JsonObject summary = getSummary(instanceId);

    assertThat(summary.getJsonObject("instance").getString("id"), is(instanceId.toString()));
    assertThat(summary.getBoolean("isBoundWith"), is(false));

    JsonObject recordCounts = summary.getJsonObject("recordCounts");
    assertThat(recordCounts.getJsonObject("instance").getBoolean("suppressedFromDiscovery"), is(false));
    assertCounts(recordCounts.getJsonObject("holdings"), 1, 0, 1);
    assertCounts(recordCounts.getJsonObject("items"), 1, 0, 0, 0, 1);

    JsonObject referenceValues = summary.getJsonObject("referenceValues");
    assertThat(referenceValues.getJsonObject("instanceType").getString("id"), is(UUID_INSTANCE_TYPE.toString()));
    assertThat(referenceValues.getJsonObject("instanceType").containsKey("name"), is(true));
  }

  @Test
  public void shouldReturnVisibilityAwareRecordCountsAndAggregates() {
    UUID instanceId = randomUUID();
    createInstanceRecord(instance(instanceId));
    createMixedVisibilityInventoryHoldingAndItem(instanceId);

    JsonObject summary = getSummary(instanceId);
    JsonObject recordCounts = summary.getJsonObject("recordCounts");

    assertCounts(recordCounts.getJsonObject("holdings"), 2, 1, 1);
    assertCounts(recordCounts.getJsonObject("items"), 4, 1, 1, 2, 2);

    JsonObject aggregates = summary.getJsonObject("aggregates");
    assertThat(aggregates
      .getJsonObject("allRecords")
      .getJsonObject("itemDerivedFields")
      .getString("effectiveShelvingOrder"), is("PN 12 A6 41999"));
    assertThat(aggregates
      .getJsonObject("notSuppressedFromDiscoveryRecords")
      .getJsonObject("itemDerivedFields")
      .getString("effectiveShelvingOrder"), is("PN 12 A6 41999"));

    assertMaterialTypeAggregates(aggregates);
  }

  @Test
  public void shouldSummarizeBoundWithItems() {
    UUID instanceId = randomUUID();
    UUID boundItemInstanceId = randomUUID();
    createInstanceRecord(instance(instanceId));
    createInstanceRecord(instance(boundItemInstanceId));

    UUID holdingId = createHolding(instanceId, MAIN_LIBRARY_LOCATION_ID, null);
    UUID boundItemHoldingId = createHolding(boundItemInstanceId, SECOND_FLOOR_LOCATION_ID, null);
    UUID boundItemId = randomUUID();
    createItem(buildItem(boundItemHoldingId, SECOND_FLOOR_LOCATION_ID, null)
      .withId(boundItemId.toString())
      .withMaterialTypeId(bookMaterialTypeID));
    createBoundWithPart(new BoundWithPartBuilder(holdingId, boundItemId).create());

    JsonObject summary = getSummary(instanceId);

    assertThat(summary.getBoolean("isBoundWith"), is(true));
    assertCounts(summary.getJsonObject("recordCounts").getJsonObject("items"), 1, 0, 0, 0, 1);
    assertThat(names(materialTypes(summary, "allRecords")), contains("book"));
  }

  @Test
  public void shouldAggregateElectronicAccessByVisibilityScope() {
    UUID instanceId = randomUUID();
    createInstanceRecord(withElectronicAccess(instance(instanceId),
      "https://example.org/instance", "https://example.org/duplicate"));
    createElectronicAccessInventory(instanceId);

    JsonObject aggregates = getSummary(instanceId).getJsonObject("aggregates");

    assertThat(uris(electronicAccess(aggregates, "allRecords")), containsInAnyOrder(
      "https://example.org/instance", "https://example.org/duplicate",
      "https://example.org/item-visible", "https://example.org/item-suppressed",
      "https://example.org/item-suppressed-by-holding", "https://example.org/holding-visible",
      "https://example.org/holding-suppressed"));
    assertThat(uris(electronicAccess(aggregates, "notSuppressedFromDiscoveryRecords")),
      containsInAnyOrder("https://example.org/instance", "https://example.org/duplicate",
        "https://example.org/item-visible", "https://example.org/holding-visible"));
  }

  @Test
  public void shouldReturnInstanceReferenceValues() {
    String suffix = randomUUID().toString();
    UUID audioFormatId = createInstanceFormat("Audio carrier " + suffix);
    UUID textFormatId = createInstanceFormat("Text carrier " + suffix);
    UUID modeOfIssuanceId = createModeOfIssuance("Monographic unit " + suffix);
    UUID bibliographyTermId = createNatureOfContentTerm("Bibliography " + suffix);
    UUID thesisTermId = createNatureOfContentTerm("Thesis " + suffix);
    UUID instanceId = randomUUID();

    createInstanceRecord(instance(instanceId)
      .put("instanceFormatIds", JsonArray.of(textFormatId.toString(), audioFormatId.toString()))
      .put("modeOfIssuanceId", modeOfIssuanceId.toString())
      .put("natureOfContentTermIds", JsonArray.of(thesisTermId.toString(), bibliographyTermId.toString())));

    JsonObject referenceValues = getSummary(instanceId).getJsonObject("referenceValues");

    assertNamedValue(referenceValues.getJsonObject("modeOfIssuance"), modeOfIssuanceId,
      "Monographic unit " + suffix);
    assertThat(names(referenceValues.getJsonArray("instanceFormats")),
      contains("Audio carrier " + suffix, "Text carrier " + suffix));
    assertThat(names(referenceValues.getJsonArray("natureOfContentTerms")),
      contains("Bibliography " + suffix, "Thesis " + suffix));
  }

  @Test
  public void shouldReturnSummaryForInstanceWithoutHoldingsOrItems() {
    UUID instanceId = randomUUID();
    createInstanceRecord(instance(instanceId));

    JsonObject summary = getSummary(instanceId);
    JsonObject recordCounts = summary.getJsonObject("recordCounts");

    assertThat(summary.getBoolean("isBoundWith"), is(false));
    assertCounts(recordCounts.getJsonObject("holdings"), 0, 0, 0);
    assertCounts(recordCounts.getJsonObject("items"), 0, 0, 0, 0, 0);
    assertEmptyAggregateScope(summary, "allRecords");
    assertEmptyAggregateScope(summary, "notSuppressedFromDiscoveryRecords");
    assertThat(summary.getJsonObject("referenceValues").containsKey("modeOfIssuance"), is(false));
  }

  @Test
  public void shouldReturnNotFoundWhenInstanceDoesNotExist() {
    Response response = getSummaryText(randomUUID());

    assertThat(response.getStatusCode(), is(HTTP_NOT_FOUND));
  }

  private static void assertCounts(JsonObject counts, int total, int suppressed, int notSuppressed) {
    assertThat(counts.getInteger("total"), is(total));
    assertThat(counts.getInteger("suppressedFromDiscovery"), is(suppressed));
    assertThat(counts.getInteger("notSuppressedFromDiscovery"), is(notSuppressed));
  }

  private static void assertCounts(JsonObject counts, int total, int suppressedFromDiscovery,
                                   int suppressedByHoldings, int suppressedFromDiscoveryOrByHoldings,
                                   int notSuppressedFromDiscovery) {
    assertThat(counts.getInteger("total"), is(total));
    assertThat(counts.getInteger("suppressedFromDiscovery"), is(suppressedFromDiscovery));
    assertThat(counts.getInteger("suppressedByHoldings"), is(suppressedByHoldings));
    assertThat(counts.getInteger("suppressedFromDiscoveryOrByHoldings"),
      is(suppressedFromDiscoveryOrByHoldings));
    assertThat(counts.getInteger("notSuppressedFromDiscovery"), is(notSuppressedFromDiscovery));
  }

  private void createMixedVisibilityInventoryHoldingAndItem(UUID instanceId) {
    UUID visibleHoldingId = createHolding(instanceId, MAIN_LIBRARY_LOCATION_ID, null);

    createItem(buildItem(visibleHoldingId, MAIN_LIBRARY_LOCATION_ID, null)
      .withId("00000000-0000-4000-8000-000000000001")
      .withMaterialTypeId(journalMaterialTypeID)
      .withItemLevelCallNumber("PN2 .A69")
      .withItemLevelCallNumberTypeId(LC_CN_TYPE_ID)
      .withVolume("v.1")
      .withEnumeration("no. 1"));
    createItem(buildItem(visibleHoldingId, MAIN_LIBRARY_LOCATION_ID, null)
      .withId("ffffffff-ffff-4fff-bfff-ffffffffffff")
      .withMaterialTypeId(journalMaterialTypeID)
      .withItemLevelCallNumber("PN2 .A6 1999")
      .withItemLevelCallNumberTypeId(LC_CN_TYPE_ID));
    createItem(buildItem(visibleHoldingId, MAIN_LIBRARY_LOCATION_ID, null)
      .withMaterialTypeId(bookMaterialTypeID)
      .withDiscoverySuppress(true));

    UUID suppressedHoldingId = createSuppressedHolding(instanceId);
    createItem(buildItem(suppressedHoldingId, SECOND_FLOOR_LOCATION_ID, null)
      .withMaterialTypeId(bookMaterialTypeID));
  }

  private void createElectronicAccessInventory(UUID instanceId) {
    UUID visibleHoldingId = createHolding(instanceId, MAIN_LIBRARY_LOCATION_ID, null,
      List.of("https://example.org/holding-visible", "https://example.org/duplicate"));
    UUID suppressedHoldingId = createSuppressedHolding(instanceId,
      List.of("https://example.org/holding-suppressed"));

    createItem(buildItem(visibleHoldingId, MAIN_LIBRARY_LOCATION_ID, null)
      .withElectronicAccess(electronicAccessItems("https://example.org/item-visible",
        "https://example.org/duplicate")));
    createItem(buildItem(visibleHoldingId, MAIN_LIBRARY_LOCATION_ID, null)
      .withDiscoverySuppress(true)
      .withElectronicAccess(electronicAccessItems("https://example.org/item-suppressed")));
    createItem(buildItem(suppressedHoldingId, SECOND_FLOOR_LOCATION_ID, null)
      .withElectronicAccess(electronicAccessItems("https://example.org/item-suppressed-by-holding")));
  }

  private static UUID createSuppressedHolding(UUID instanceId) {
    return createSuppressedHolding(instanceId, null);
  }

  private static UUID createSuppressedHolding(UUID instanceId, List<String> electronicAccessUrls) {
    return createHoldingRecord(new HoldingRequestBuilder()
      .withId(randomUUID())
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(SECOND_FLOOR_LOCATION_ID)
      .withDiscoverySuppress(true)
      .withElectronicAccess(electronicAccess(electronicAccessUrls))
      .create()).getId();
  }

  private static UUID createInstanceFormat(String name) {
    UUID id = randomUUID();
    InstanceFormat instanceFormat = new InstanceFormat()
      .withId(id.toString())
      .withName(name)
      .withCode("code-" + id)
      .withSource("test");

    assertCreated(get(getClient().post(instanceFormatsUrl(""), pojo2JsonObject(instanceFormat), TENANT_ID)));
    return id;
  }

  private static UUID createModeOfIssuance(String name) {
    UUID id = randomUUID();
    modesOfIssuanceClient.create(pojo2JsonObject(new IssuanceMode()
      .withId(id.toString())
      .withName(name)
      .withSource("test")));

    return id;
  }

  private static UUID createNatureOfContentTerm(String name) {
    UUID id = randomUUID();
    NatureOfContentTerm natureOfContentTerm = new NatureOfContentTerm()
      .withId(id.toString())
      .withName(name)
      .withSource("test");

    assertCreated(get(getClient().post(natureOfContentTermsUrl(""),
      pojo2JsonObject(natureOfContentTerm), TENANT_ID)));
    return id;
  }

  private static void assertCreated(Response response) {
    assertThat(response.getStatusCode(), is(HTTP_CREATED));
  }

  private static List<String> names(JsonArray referenceValues) {
    return referenceValues.stream()
      .map(JsonObject.class::cast)
      .map(value -> value.getString("name"))
      .toList();
  }

  private static List<String> uris(JsonArray electronicAccess) {
    return electronicAccess.stream()
      .map(JsonObject.class::cast)
      .map(value -> value.getString("uri"))
      .toList();
  }

  private static JsonObject withElectronicAccess(JsonObject record, String... uris) {
    return record.put("electronicAccess", electronicAccess(Arrays.asList(uris)));
  }

  private static JsonArray electronicAccess(List<String> uris) {
    return uris == null ? null : uris.stream()
      .map(uri -> new JsonObject().put("uri", uri))
      .collect(JsonArray::new, JsonArray::add, JsonArray::addAll);
  }

  private static JsonArray electronicAccess(JsonObject aggregates, String scope) {
    return aggregates.getJsonObject(scope).getJsonArray("electronicAccess");
  }

  private static List<ElectronicAccessItem> electronicAccessItems(String... uris) {
    return Arrays.stream(uris)
      .map(uri -> new ElectronicAccessItem().withUri(uri))
      .toList();
  }

  private static JsonArray materialTypes(JsonObject summary, String scope) {
    return summary.getJsonObject("aggregates")
      .getJsonObject(scope)
      .getJsonObject("referenceValues")
      .getJsonArray("itemMaterialTypes");
  }

  private static void assertNamedValue(JsonObject value, UUID id, String name) {
    assertThat(value.getString("id"), is(id.toString()));
    assertThat(value.getString("name"), is(name));
  }

  private static void assertEmptyAggregateScope(JsonObject summary, String scope) {
    JsonObject aggregateScope = summary.getJsonObject("aggregates").getJsonObject(scope);

    assertThat(aggregateScope.getJsonArray("electronicAccess").isEmpty(), is(true));
    assertThat(aggregateScope
      .getJsonObject("referenceValues")
      .getJsonArray("itemMaterialTypes")
      .isEmpty(), is(true));
    assertThat(aggregateScope
      .getJsonObject("itemDerivedFields")
      .getString("effectiveShelvingOrder"), is((String) null));
  }

  private static void assertMaterialTypeAggregates(JsonObject aggregates) {
    JsonArray allMaterialTypes = aggregates
      .getJsonObject("allRecords")
      .getJsonObject("referenceValues")
      .getJsonArray("itemMaterialTypes");
    JsonArray visibleMaterialTypes = aggregates
      .getJsonObject("notSuppressedFromDiscoveryRecords")
      .getJsonObject("referenceValues")
      .getJsonArray("itemMaterialTypes");

    assertThat(names(allMaterialTypes), containsInAnyOrder("book", "journal"));
    assertThat(names(visibleMaterialTypes), contains("journal"));
  }

  private static JsonObject getSummary(UUID instanceId) {
    Response response = getSummaryJson(instanceId);
    assertThat(response.getStatusCode(), is(HTTP_OK));
    return response.getJson();
  }

  private static Response getSummaryJson(UUID instanceId) {
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    getClient().get(summaryUrl(instanceId), TENANT_ID, json(getCompleted));
    return get(getCompleted);
  }

  private static Response getSummaryText(UUID instanceId) {
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    getClient().get(summaryUrl(instanceId), TENANT_ID, text(getCompleted));
    return get(getCompleted);
  }

  private static java.net.URL summaryUrl(UUID instanceId) {
    return instancesStorageUrl("/" + instanceId + "/summary");
  }
}
