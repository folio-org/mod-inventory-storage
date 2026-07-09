package org.folio.rest.api;

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.util.UUID.randomUUID;
import static org.folio.rest.support.ResponseHandler.json;
import static org.folio.rest.support.ResponseHandler.text;
import static org.folio.rest.support.http.InterfaceUrls.instancesStorageUrl;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.folio.rest.support.Response;
import org.folio.rest.support.builders.HoldingRequestBuilder;
import org.junit.Before;
import org.junit.Test;

public class InstanceSummaryStorageTest extends TestBaseWithInventoryUtil {

  @Before
  public void beforeEach() {
    clearData();

    setupMaterialTypes();
    setupLoanTypes();
    setupLocations();

    removeAllEvents();
    WireMock.reset();
    mockUserTenantsForNonConsortiumMember();
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

  private static UUID createSuppressedHolding(UUID instanceId) {
    return createHoldingRecord(new HoldingRequestBuilder()
      .withId(randomUUID())
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(SECOND_FLOOR_LOCATION_ID)
      .withDiscoverySuppress(true)
      .create()).getId();
  }

  private static List<String> names(JsonArray referenceValues) {
    return referenceValues.stream()
      .map(JsonObject.class::cast)
      .map(value -> value.getString("name"))
      .toList();
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
