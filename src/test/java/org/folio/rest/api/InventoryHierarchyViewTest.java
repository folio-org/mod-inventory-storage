package org.folio.rest.api;

import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.api.StorageTestSuite.deleteAll;
import static org.folio.rest.api.StorageTestSuite.getVertx;
import static org.folio.rest.support.http.InterfaceUrls.holdingsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.instancesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.inventoryHierarchyItemsAndHoldings;
import static org.folio.rest.support.http.InterfaceUrls.inventoryHierarchyUpdatedInstanceIds;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.folio.rest.support.matchers.InventoryHierarchyResponseMatchers.hasEffectiveLocationCodeForHoldings;
import static org.folio.rest.support.matchers.InventoryHierarchyResponseMatchers.hasEffectiveLocationForHoldings;
import static org.folio.rest.support.matchers.InventoryHierarchyResponseMatchers.hasTemporaryLocationCodeForHoldings;
import static org.folio.rest.support.matchers.InventoryHierarchyResponseMatchers.hasTemporaryLocationForHoldings;
import static org.folio.rest.support.matchers.InventoryHierarchyResponseMatchers.hasAggregatedNumberOfHoldings;
import static org.folio.rest.support.matchers.InventoryHierarchyResponseMatchers.hasAggregatedNumberOfItems;
import static org.folio.rest.support.matchers.InventoryHierarchyResponseMatchers.hasCallNumberForItems;
import static org.folio.rest.support.matchers.InventoryHierarchyResponseMatchers.hasEffectiveLocationInstitutionNameForItems;
import static org.folio.rest.support.matchers.InventoryHierarchyResponseMatchers.hasIdForHoldings;
import static org.folio.rest.support.matchers.InventoryHierarchyResponseMatchers.hasIdForInstance;
import static org.folio.rest.support.matchers.InventoryHierarchyResponseMatchers.hasLocationCodeForItems;
import static org.folio.rest.support.matchers.InventoryHierarchyResponseMatchers.hasPermanentLocationCodeForHoldings;
import static org.folio.rest.support.matchers.InventoryHierarchyResponseMatchers.hasPermanentLocationForHoldings;
import static org.folio.rest.support.matchers.InventoryHierarchyResponseMatchers.hasSourceForInstance;
import static org.folio.rest.support.matchers.InventoryHierarchyResponseMatchers.isDeleted;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.InventoryInstanceIds;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.builders.ItemRequestBuilder;
import org.folio.rest.tools.utils.TenantTool;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.Handler;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.sqlclient.Row;

@RunWith(VertxUnitRunner.class)
public class InventoryHierarchyViewTest extends TestBaseWithInventoryUtil {
  private static final Logger log = LogManager.getLogger();

  private static final PostgresClient postgresClient = PostgresClient.getInstance(getVertx(),
    TenantTool.calculateTenantId(TENANT_ID));

  private static final String QUERY_PARAM_NAME_SKIP_SUPPRESSED_FROM_DISCOVERY_RECORDS = "skipSuppressedFromDiscoveryRecords";

  private UUID holdingsRecordIdPredefined;
  private Map<String, String> params;
  private JsonObject predefinedInstance;
  private JsonObject predefinedHoldings;

  @Before
  public void setUp() {
    deleteAll(itemsStorageUrl(""));
    deleteAll(holdingsStorageUrl(""));
    deleteAll(instancesStorageUrl(""));
    clearAuditTables();

    params = new HashMap<>();

    holdingsRecordIdPredefined = createInstanceAndHolding(mainLibraryLocationId);
    predefinedHoldings = holdingsClient.getById(holdingsRecordIdPredefined).getJson();

    predefinedInstance = instancesClient.getAll().get(0);

    createItem(mainLibraryLocationId, "item barcode", "item effective call number 1", journalMaterialTypeId);
    createItem(thirdFloorLocationId, "item barcode 2", "item effective call number 2", bookMaterialTypeId);
  }

  @Test
  public void ServerErrorWrittenOutOnDatabaseError() throws InterruptedException, ExecutionException, TimeoutException {

    setFaultyStatisticalCode();    

    params.put(QUERY_PARAM_NAME_SKIP_SUPPRESSED_FROM_DISCOVERY_RECORDS, "false");

    List<JsonObject> instances = requestInventoryHierarchyViewUpdatedInstanceIds(params, response -> assertThat(response.getStatusCode(), is(200)));
    UUID[] instanceIds = instances.stream()
      .map(json -> UUID.fromString(json.getString("instanceId")))
      .toArray(UUID[]::new);
    
    List<JsonObject> instancesData = requestInventoryHierarchyItemsAndHoldingsViewInstance(
      instanceIds, false, response -> assertThat(response.getStatusCode(), is(500)));

    JsonObject error = instancesData.get(0);
    assertThat(error.getString("message"), is("invalid input syntax for type uuid: \"5t632 ytbg vnc\""));
    removeFaultyStatisticalCode();

  }

  @Test
  public void canRequestInventoryHierarchyInstanceWithoutParameters() throws InterruptedException, ExecutionException, TimeoutException {
    // given
    // one instance, 1 holding, 2 items
    // when
    params.put(QUERY_PARAM_NAME_SKIP_SUPPRESSED_FROM_DISCOVERY_RECORDS, "false");
    final List<JsonObject> instancesData = getInventoryHierarchyInstances(params);
    // then
    assertThat(
      instancesData.get(0),
      allOf(
        hasIdForInstance(predefinedInstance.getString("id")),
        hasSourceForInstance(predefinedInstance.getString("source"))
      )
    );
  }

  @Test
  public void canRequestInventoryHierarchyHoldingsWithoutParameters() throws InterruptedException, ExecutionException, TimeoutException {
    // given
    // one instance, 1 holding, 2 items
    // when
    params.put(QUERY_PARAM_NAME_SKIP_SUPPRESSED_FROM_DISCOVERY_RECORDS, "false");
    final List<JsonObject> instancesData = getInventoryHierarchyInstances(params);
    // then
    assertThat(
      instancesData.get(0),
      allOf(
        hasIdForHoldings(predefinedHoldings.getString("id")),
        hasEffectiveLocationForHoldings("d:Main Library"),
        hasEffectiveLocationCodeForHoldings("TestBaseWI/M"),
        hasPermanentLocationForHoldings("d:Main Library"),
        hasPermanentLocationCodeForHoldings("TestBaseWI/M"),
        hasAggregatedNumberOfHoldings(1)
      )
    );
  }

  @Test
  public void holdingsEffectiveLocationIsTemporaryLocationWhenTempLocationSet() 
    throws InterruptedException, ExecutionException, TimeoutException {

    JsonObject record = holdingsClient.getById(holdingsRecordIdPredefined).getJson();
    record.put("temporaryLocationId", annexLibraryLocationId.toString());
    holdingsClient.replace(holdingsRecordIdPredefined, record);

    params.put(QUERY_PARAM_NAME_SKIP_SUPPRESSED_FROM_DISCOVERY_RECORDS, "false");
    final List<JsonObject> instancesData = getInventoryHierarchyInstances(params);
    // then
    assertThat(
      instancesData.get(0),
      allOf(
        hasIdForHoldings(predefinedHoldings.getString("id")),
        hasTemporaryLocationForHoldings("d:Annex Library"),
        hasTemporaryLocationCodeForHoldings("TestBaseWI/A"),
        hasEffectiveLocationForHoldings("d:Annex Library"),
        hasEffectiveLocationCodeForHoldings("TestBaseWI/A"),
        hasPermanentLocationForHoldings("d:Main Library"),
        hasPermanentLocationCodeForHoldings("TestBaseWI/M"),
        hasAggregatedNumberOfHoldings(1)
      )
    );
  }
  @Test
  public void canRequestInventoryHierarchyItemsWithoutParameters() throws InterruptedException, ExecutionException, TimeoutException {
    // given
    // one instance, 1 holding, 2 items
    // when
    params.put(QUERY_PARAM_NAME_SKIP_SUPPRESSED_FROM_DISCOVERY_RECORDS, "false");
    final List<JsonObject> instancesData = getInventoryHierarchyInstances(params);
    // then
    assertThat(
      instancesData.get(0),
      allOf(
        hasCallNumberForItems("item effective call number 1", "item effective call number 2"),
        hasEffectiveLocationInstitutionNameForItems("Primary Institution"),
        hasLocationCodeForItems("TestBaseWI/M", "TestBaseWI/TF"),
        hasAggregatedNumberOfItems(2)
      )
    );
  }

  @Test
  public void canRequestInventoryHierarchyViewWhenEmptyDB() throws InterruptedException, ExecutionException, TimeoutException {
    // given
    deleteAll(itemsStorageUrl(""));
    deleteAll(holdingsStorageUrl(""));
    deleteAll(instancesStorageUrl(""));
    clearAuditTables();

    // when
    final List<JsonObject> instancesData = getInventoryHierarchyInstances(params);
    // then
    assertThat(instancesData.size(), is(0));
  }

  @Test
  public void testDeletedRecordSupport() throws InterruptedException, TimeoutException, ExecutionException {
    // given
    itemsClient.deleteAll();
    holdingsClient.deleteAll();
    instancesClient.deleteAll();

    // when (just need to retrieve "updated instances" - only the have "deleted" field)
    params.put("deletedRecordSupport", "true");
    List<JsonObject> data = requestInventoryHierarchyViewUpdatedInstanceIds(params);
    // then
    assertThat(data.get(0), isDeleted());

    // when
    params.put("deletedRecordSupport", "false");
    data = requestInventoryHierarchyViewUpdatedInstanceIds(params);
    // then
    assertThat(data.size(), is(0));
  }

  private List<JsonObject> getInventoryHierarchyInstances(Map<String, String> queryParams)
      throws InterruptedException, ExecutionException, TimeoutException {
    return getInventoryHierarchyInstances(queryParams, response -> assertThat(response.getStatusCode(), is(200)));
  }

  private List<JsonObject> getInventoryHierarchyInstances(Map<String, String> queryParams, Handler<Response> responseMatcher)
      throws InterruptedException, ExecutionException, TimeoutException {

    // Get updated instances ids
    List<JsonObject> updatedInstanceData = requestInventoryHierarchyViewUpdatedInstanceIds(queryParams, responseMatcher);

    // Extract instances ids
    UUID[] instanceIds = updatedInstanceData.stream()
      .map(json -> UUID.fromString(json.getString("instanceId")))
      .toArray(UUID[]::new);

    // Retrieves instances with items and holdings data
    List<JsonObject> instancesWithItemsAndHoldings = new ArrayList<>();
    if (ArrayUtils.isNotEmpty(instanceIds)) {
      instancesWithItemsAndHoldings = requestInventoryHierarchyItemsAndHoldingsViewInstance(instanceIds,
        Boolean.parseBoolean(queryParams.get(QUERY_PARAM_NAME_SKIP_SUPPRESSED_FROM_DISCOVERY_RECORDS)), responseMatcher);
    }
    return instancesWithItemsAndHoldings;
  }

  @Test
  public void testFilterByDates() throws InterruptedException, ExecutionException, TimeoutException {
    params.put(QUERY_PARAM_NAME_SKIP_SUPPRESSED_FROM_DISCOVERY_RECORDS, "false");
    // given
    // one instance, 1 holding, 2 items
    // when
    LocalDateTime startDate = LocalDateTime.of(2000, 1, 1, 0, 0, 0);
    params.put("startDate", OffsetDateTime.of(startDate, ZoneOffset.UTC)
      .toString());
    List<JsonObject> instancesData = getInventoryHierarchyInstances(params);
    // then
    assertThat(instancesData.get(0), allOf(hasCallNumberForItems("item effective call number 1", "item effective call number 2"),
      hasAggregatedNumberOfItems(2), hasEffectiveLocationInstitutionNameForItems("Primary Institution")));

    // when
    LocalDateTime endDate = LocalDateTime.of(2500, 1, 1, 0, 0, 0);
    params.put("endDate", OffsetDateTime.of(endDate, ZoneOffset.UTC)
      .toString());
    instancesData = getInventoryHierarchyInstances(params);
    // then
    assertThat(instancesData.get(0), allOf(hasCallNumberForItems("item effective call number 1", "item effective call number 2"),
      hasAggregatedNumberOfItems(2), hasEffectiveLocationInstitutionNameForItems("Primary Institution")));

    // when
    startDate = LocalDateTime.of(2050, 1, 1, 0, 0, 0);
    params.put("startDate", OffsetDateTime.of(startDate, ZoneOffset.UTC)
      .toString());
    instancesData = getInventoryHierarchyInstances(params);
    // then
    assertThat(instancesData.size(), is(0));

    // when
    endDate = LocalDateTime.of(2000, 1, 1, 0, 0, 0);
    params.put("endDate", OffsetDateTime.of(endDate, ZoneOffset.UTC)
      .toString());
    instancesData = getInventoryHierarchyInstances(params);
    // then
    assertThat(instancesData.size(), is(0));

    // when
    startDate = LocalDateTime.of(2000, 1, 1, 0, 0, 0);
    endDate = LocalDateTime.of(2050, 1, 1, 0, 0, 0);
    params.put("startDate", OffsetDateTime.of(startDate, ZoneOffset.UTC)
      .toString());
    params.put("endDate", OffsetDateTime.of(endDate, ZoneOffset.UTC)
      .toString());
    instancesData = getInventoryHierarchyInstances(params);
    // then
    assertThat(instancesData.get(0), allOf(hasCallNumberForItems("item effective call number 1", "item effective call number 2"),
      hasAggregatedNumberOfItems(2), hasEffectiveLocationInstitutionNameForItems("Primary Institution")));

    // when
    startDate = LocalDateTime.of(2000, 1, 1, 0, 0, 0);
    endDate = LocalDateTime.of(2001, 1, 1, 0, 0, 0);
    params.put("startDate", OffsetDateTime.of(startDate, ZoneOffset.UTC)
      .toString());
    params.put("endDate", OffsetDateTime.of(endDate, ZoneOffset.UTC)
      .toString());
    instancesData = getInventoryHierarchyInstances(params);
    // then
    assertThat(instancesData.size(), is(0));
  }

  /**
   * By default we skip discovery suppressed records
   */
  @Test
  public void canGetFromInventoryHierarchyViewShowingSuppressedRecords() throws Exception {
    // given
    // one instance, 1 holding, 2 not suppressed items, 1 suppressed item
    super.createItem(createItemRequest(thirdFloorLocationId, "item barcode 2", "item effective call number 3", bookMaterialTypeId)
      .withDiscoverySuppress(true));
    // when
    params.put(QUERY_PARAM_NAME_SKIP_SUPPRESSED_FROM_DISCOVERY_RECORDS, "true");
    List<JsonObject> data = getInventoryHierarchyInstances(params);
    // then
    assertThat(data.get(0), allOf(
      hasCallNumberForItems("item effective call number 1", "item effective call number 2"),
      hasAggregatedNumberOfItems(2),
      hasEffectiveLocationInstitutionNameForItems("Primary Institution"))
    );

    // when
    params.put(QUERY_PARAM_NAME_SKIP_SUPPRESSED_FROM_DISCOVERY_RECORDS, "false");
    data = getInventoryHierarchyInstances(params);
    log.info("Inventory hierarchy instances data: " + data);
    // then
    assertThat(data.get(0),
      allOf(
        hasCallNumberForItems("item effective call number 1", "item effective call number 3", "item effective call number 2"),
        hasAggregatedNumberOfItems(3),
        hasEffectiveLocationInstitutionNameForItems("Primary Institution")
      ));
  }

  /**
   * The decode exception is thrown when we try to parse the response, but the only relevant thing is the correct response status of
   * 400.
   */
  @Test(expected = DecodeException.class)
  public void testResponseStatus400WhenRequestingWithInvalidDates()
      throws InterruptedException, ExecutionException, TimeoutException {
    // given
    // one instance, 1 holding, 2 items
    // when
    params.put("startDate", "invalidDate");
    // then
    getInventoryHierarchyInstances(params, response -> assertThat(response.getStatusCode(), is(400)));
  }

  /**
   * The decode exception is thrown when we try to parse the response, but the only relevant thing is the correct response status of
   * 400.
   */
  @Test(expected = DecodeException.class)
  public void testResponseStatus400WhenRequestingWithInvalidUntilDate()
      throws InterruptedException, ExecutionException, TimeoutException {
    // given
    // one instance, 1 holding, 2 items
    // when
    params.put("endDate", "invalidDate");
    // then
    getInventoryHierarchyInstances(params, response -> assertThat(response.getStatusCode(), is(400)));
  }

  private void createItem(UUID mainLibraryLocationId, String s, String s2, UUID journalMaterialTypeId) {
    super.createItem(createItemRequest(mainLibraryLocationId, s, s2, journalMaterialTypeId).create());
  }

  private ItemRequestBuilder createItemRequest(UUID locationId, String barcode, String callNumber, UUID materialTypeId) {
    return new ItemRequestBuilder().forHolding(holdingsRecordIdPredefined)
      .withPermanentLoanType(canCirculateLoanTypeId)
      .withTemporaryLocation(locationId)
      .withBarcode(barcode)
      .withItemLevelCallNumber(callNumber)
      .withMaterialType(materialTypeId);
  }

  void clearAuditTables() {
    CompletableFuture<Row> future = new CompletableFuture<>();
    final String sql = Stream.of("audit_instance", "audit_holdings_record", "audit_item")
      .map(s -> "DELETE FROM " + s)
      .collect(Collectors.joining(";"));

    postgresClient.selectSingle(sql, handler -> {
      if (handler.failed()) {
        future.completeExceptionally(handler.cause());
        return;
      }
      future.complete(handler.result());
    });
  }

  private List<JsonObject> requestInventoryHierarchyItemsAndHoldingsViewInstance(UUID[] instanceIds, boolean skipSuppressedFromDiscoveryRecords,
      Handler<Response> responseMatcher) throws InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<Response> future = new CompletableFuture<>();
    final List<JsonObject> results = new ArrayList<>();

    InventoryInstanceIds instanceIdsPayload = new InventoryInstanceIds();
    instanceIdsPayload.setInstanceIds(Arrays.stream(instanceIds).map(UUID::toString).collect(Collectors.toList()));
    instanceIdsPayload.setSkipSuppressedFromDiscoveryRecords(skipSuppressedFromDiscoveryRecords);

    client.post(inventoryHierarchyItemsAndHoldings(), instanceIdsPayload, TENANT_ID, ResponseHandler.any(future));

    final Response response = future.get(2, TimeUnit.SECONDS);
    responseMatcher.handle(response);
    log.info("\nResponse from inventory instance ids view: " + response);

    final String body = response.getBody();
    if (StringUtils.isNotEmpty(body)) {
      results.add(new JsonObject(body));
    }

    return results;
  }

  private void setFaultyStatisticalCode() {
    String sql = "UPDATE item SET jsonb=jsonb_set(jsonb, '{statisticalCodeIds}', '[\"5t632 ytbg vnc\"]', true);";
    
    postgresClient.execute(sql, handler -> {
      if (handler.failed()) {
        log.error("Error updating database: " + handler.cause().getMessage());
      }
    });
  }

  private void removeFaultyStatisticalCode() {
    String sql = "UPDATE item SET jsonb=jsonb_set(jsonb, '{statisticalCodeIds}', '[]', true);";
    
    postgresClient.selectSingle(sql, handler -> {
      if (handler.failed()) {
        log.error("Error updating database: " + handler.cause().getMessage());
      }
    });

  }

  private List<JsonObject> requestInventoryHierarchyViewUpdatedInstanceIds(Map<String, String> queryParamsMap)
      throws InterruptedException, ExecutionException, TimeoutException {

    return requestInventoryHierarchyViewUpdatedInstanceIds(queryParamsMap, response -> assertThat(response.getStatusCode(), is(200)));
  }

  private List<JsonObject> requestInventoryHierarchyViewUpdatedInstanceIds(Map<String, String> queryParamsMap, Handler<Response> responseMatcher)
      throws InterruptedException, ExecutionException, TimeoutException {

    final String queryParams = queryParamsMap.entrySet()
      .stream()
      .map(e -> e.getKey() + "=" + e.getValue())
      .collect(Collectors.joining("&"));

    CompletableFuture<Response> future = new CompletableFuture<>();
    final List<JsonObject> results = new ArrayList<>();

    client.get(inventoryHierarchyUpdatedInstanceIds("?" + queryParams), TENANT_ID, ResponseHandler.any(future));

    final Response response = future.get(2, TimeUnit.SECONDS);
    responseMatcher.handle(response);
    log.info("response from oai pmh updated instances view:", response);

    final String body = response.getBody();
    if (StringUtils.isNotEmpty(body)) {
      results.add(new JsonObject(body));
    }

    return results;
  }
}
