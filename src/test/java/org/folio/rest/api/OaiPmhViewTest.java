package org.folio.rest.api;

import static org.folio.rest.api.StorageTestSuite.*;
import static org.folio.rest.support.http.InterfaceUrls.*;
import static org.folio.rest.support.matchers.OaiPmhResponseMatchers.*;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang.StringUtils;
import org.folio.rest.jaxrs.model.InstanceType;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.builders.ItemRequestBuilder;
import org.folio.rest.tools.utils.TenantTool;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.Handler;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.sqlclient.Row;

@RunWith(VertxUnitRunner.class)
public class OaiPmhViewTest extends TestBaseWithInventoryUtil {
  private static final Logger log = LoggerFactory.getLogger(OaiPmhViewTest.class);

  private static final PostgresClient postgresClient = PostgresClient.getInstance(getVertx(),
      TenantTool.calculateTenantId(TENANT_ID));

  private static final String QUERY_PARAM_NAME_SKIP_SUPPRESSED_FROM_DISCOVERY_RECORDS = "skipSuppressedFromDiscoveryRecords";

  private UUID holdingsRecordId1;
  private Map<String, String> params;
  private UUID instanceId1;

  @Before
  public void setUp() throws InterruptedException, ExecutionException, MalformedURLException, TimeoutException {

    deleteAll(itemsStorageUrl(""));
    deleteAll(holdingsStorageUrl(""));
    deleteAll(instancesStorageUrl(""));
    clearAuditTables();

    params = new HashMap<>();

    holdingsRecordId1 = createInstanceAndHolding(mainLibraryLocationId);
    final JsonObject instanceObj = instancesClient.getAll()
      .get(0);
    instanceId1 = UUID.fromString(instanceObj.getString("id"));

    createItem(mainLibraryLocationId, "item barcode", "item effective call number 1", journalMaterialTypeId);
    createItem(thirdFloorLocationId, "item barcode 2", "item effective call number 2", bookMaterialTypeId);
  }

  @BeforeClass
  public static void beforeClass() throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {
    if (instanceTypesClient.getAll()
      .size() == 0) {
      InstanceType it = new InstanceType();
      it.withId(UUID_INSTANCE_TYPE.toString());
      it.withCode("it code");
      it.withName("it name");
      it.withSource("tests");
      instanceTypesClient.create(JsonObject.mapFrom(it));
    }
    deleteAll(itemsStorageUrl(""));
    deleteAll(holdingsStorageUrl(""));
    deleteAll(instancesStorageUrl(""));
  }

  @Test
  public void canRequestOaiPmhViewWithoutParameters() throws InterruptedException, ExecutionException, TimeoutException {
    // given
    // one instance, 1 holding, 2 items
    // when
    final List<JsonObject> data = requestOaiPmhViewUpdatedInstanceIds(params);
    // then
    assertThat(data.get(0), allOf(hasCallNumber("item effective call number 1", "item effective call number 2"),
        hasAggregatedNumberOfItems(2), hasEffectiveLocationInstitutionName("Primary Institution")));
  }

  @Test
  public void canRequestOaiPmhViewWhenEmptyDB() throws InterruptedException, ExecutionException, TimeoutException {
    // given
    deleteAll(itemsStorageUrl(""));
    deleteAll(holdingsStorageUrl(""));
    deleteAll(instancesStorageUrl(""));
    clearAuditTables();
    // when
    final List<JsonObject> data = requestOaiPmhViewUpdatedInstanceIds(params);
    // then
    assertThat(data.size(), is(0));
  }

  @Test
  public void testDeletedRecordSupport() throws InterruptedException, TimeoutException, ExecutionException, MalformedURLException {
    // given
    itemsClient.deleteAll();
    holdingsClient.deleteAll();
    instancesClient.deleteAll();
    // when
    params.put("deletedRecordSupport", "true");
    List<JsonObject> data = requestOaiPmhViewUpdatedInstanceIds(params);
    // then
    assertThat(data.get(0), isDeleted());

    // when
    params.put("deletedRecordSupport", "false");
    data = requestOaiPmhViewUpdatedInstanceIds(params);
    // then
    assertThat(data.size(), is(0));
  }

  @Test
  public void testFilterByDates() throws InterruptedException, ExecutionException, TimeoutException {
    // given
    // one instance, 1 holding, 2 items
    // when
    LocalDateTime startDate = LocalDateTime.of(2000, 1, 1, 0, 0, 0);
    params.put("startDate", OffsetDateTime.of(startDate, ZoneOffset.UTC)
      .toString());
    List<JsonObject> data = requestOaiPmhViewUpdatedInstanceIds(params);
    // then
    assertThat(data.get(0), allOf(hasCallNumber("item effective call number 1", "item effective call number 2"),
        hasAggregatedNumberOfItems(2), hasEffectiveLocationInstitutionName("Primary Institution")));

    // when
    LocalDateTime endDate = LocalDateTime.of(2500, 1, 1, 0, 0, 0);
    params.put("endDate", OffsetDateTime.of(endDate, ZoneOffset.UTC)
      .toString());
    data = requestOaiPmhViewUpdatedInstanceIds(params);
    // then
    assertThat(data.get(0), allOf(hasCallNumber("item effective call number 1", "item effective call number 2"),
        hasAggregatedNumberOfItems(2), hasEffectiveLocationInstitutionName("Primary Institution")));

    // when
    startDate = LocalDateTime.of(2050, 1, 1, 0, 0, 0);
    params.put("startDate", OffsetDateTime.of(startDate, ZoneOffset.UTC)
      .toString());
    data = requestOaiPmhViewUpdatedInstanceIds(params);
    // then
    assertThat(data.size(), is(0));

    // when
    endDate = LocalDateTime.of(2000, 1, 1, 0, 0, 0);
    params.put("endDate", OffsetDateTime.of(endDate, ZoneOffset.UTC)
      .toString());
    data = requestOaiPmhViewUpdatedInstanceIds(params);
    // then
    assertThat(data.size(), is(0));

    // when
    startDate = LocalDateTime.of(2000, 1, 1, 0, 0, 0);
    endDate = LocalDateTime.of(2050, 1, 1, 0, 0, 0);
    params.put("startDate", OffsetDateTime.of(startDate, ZoneOffset.UTC)
      .toString());
    params.put("endDate", OffsetDateTime.of(endDate, ZoneOffset.UTC)
      .toString());
    data = requestOaiPmhViewUpdatedInstanceIds(params);
    // then
    assertThat(data.get(0), allOf(hasCallNumber("item effective call number 1", "item effective call number 2"),
        hasAggregatedNumberOfItems(2), hasEffectiveLocationInstitutionName("Primary Institution")));

    // when
    startDate = LocalDateTime.of(2000, 1, 1, 0, 0, 0);
    endDate = LocalDateTime.of(2001, 1, 1, 0, 0, 0);
    params.put("startDate", OffsetDateTime.of(startDate, ZoneOffset.UTC)
      .toString());
    params.put("endDate", OffsetDateTime.of(endDate, ZoneOffset.UTC)
      .toString());
    data = requestOaiPmhViewUpdatedInstanceIds(params);
    // then
    assertThat(data.size(), is(0));

  }

  /**
   * By default we skip discovery suppressed records
   */
  @Test
  public void canGetFromOaiPmhViewShowingSuppressedRecords() throws Exception {
    // given
    // one instance, 1 holding, 2 not suppressed items, 1 suppressed item
    super.createItem(createItemRequest(thirdFloorLocationId, "item barcode 2", "item effective call number 3", bookMaterialTypeId)
      .withDiscoverySuppress(true));
    // when
    params.put(QUERY_PARAM_NAME_SKIP_SUPPRESSED_FROM_DISCOVERY_RECORDS, "true");
    List<JsonObject> data = requestOaiPmhViewUpdatedInstanceIds(params);
    // then
    assertThat(data.get(0), allOf(hasCallNumber("item effective call number 1", "item effective call number 2"),
        hasAggregatedNumberOfItems(2), hasEffectiveLocationInstitutionName("Primary Institution")));

    // when
    params.put(QUERY_PARAM_NAME_SKIP_SUPPRESSED_FROM_DISCOVERY_RECORDS, "false");
    data = requestOaiPmhViewUpdatedInstanceIds(params);
    // then
    assertThat(data.get(0),
        allOf(hasCallNumber("item effective call number 1", "item effective call number 2", "item effective call number 3"),
            hasAggregatedNumberOfItems(3), hasEffectiveLocationInstitutionName("Primary Institution")));
  }

  private Predicate<Object> instancePredicate() {
    return jo -> StringUtils.equals(((JsonObject) jo).getString("instanceid"), instanceId1.toString());
  }

  /**
   * The decode exception is thrown when we try to parse the response, but the only relevant thing is the correct response status of
   * 400.
   *
   */
  @Test(expected = DecodeException.class)
  public void testResponseStatus400WhenRequestingWithInvalidDates()
      throws InterruptedException, ExecutionException, TimeoutException {
    // given
    // one instance, 1 holding, 2 items
    // when
    params.put("startDate", "invalidDate");
    // then
    requestOaiPmhViewUpdatedInstanceIds(params, response -> {
      assertThat(response.getStatusCode(), is(400));
    });
  }

  /**
   * The decode exception is thrown when we try to parse the response, but the only relevant thing is the correct response status of
   * 400.
   *
   */
  @Test(expected = DecodeException.class)
  public void testResponseStatus400WhenRequestingWithInvalidUntilDate()
      throws InterruptedException, ExecutionException, TimeoutException {
    // given
    // one instance, 1 holding, 2 items
    // when
    params.put("endDate", "invalidDate");
    // then
    requestOaiPmhViewUpdatedInstanceIds(params, response -> {
      assertThat(response.getStatusCode(), is(400));
    });
  }

  private void createItem(UUID mainLibraryLocationId, String s, String s2, UUID journalMaterialTypeId)
      throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {
    super.createItem(createItemRequest(mainLibraryLocationId, s, s2, journalMaterialTypeId).create());
  }

  private ItemRequestBuilder createItemRequest(UUID locationId, String barcode, String callNumber, UUID materialTypeId) {
    return new ItemRequestBuilder().forHolding(holdingsRecordId1)
      .withPermanentLoanType(canCirculateLoanTypeId)
      .withTemporaryLocation(locationId)
      .withBarcode(barcode)
      .withItemLevelCallNumber(callNumber)
      .withMaterialType(materialTypeId);
  }

  private List<JsonObject> requestOaiPmhViewInstance(List<String> instanceIds, Map<String, String> queryParamsMap)
      throws InterruptedException, ExecutionException, TimeoutException {

    return requestOaiPmhViewInstance(instanceIds, queryParamsMap, response -> {
      assertThat(response.getStatusCode(), is(200));
    });
  }

  private List<JsonObject> requestOaiPmhViewInstance(List<String> instanceIds, Map<String, String> queryParamsMap, Handler<Response> responseMatcher)
    throws InterruptedException, ExecutionException, TimeoutException {

    final String queryParams = queryParamsMap.entrySet()
      .stream()
      .map(e -> e.getKey() + "=" + e.getValue())
      .collect(Collectors.joining("&"));

    CompletableFuture<Response> future = new CompletableFuture<>();
    final List<JsonObject> results = new ArrayList<>();

    client.post(oaiPmhViewInstance("?" + queryParams), instanceIds.toArray(new String[0]), TENANT_ID, ResponseHandler.any(future));

    final Response response = future.get(2, TimeUnit.SECONDS);
    responseMatcher.handle(response);
    log.info("response from oai pmh instance ids view:", response);

    final String body = response.getBody();
    if (StringUtils.isNotEmpty(body)) {
      results.add(new JsonObject(body));
    }

    return results;
  }

  private List<JsonObject> requestOaiPmhViewUpdatedInstanceIds(Map<String, String> queryParamsMap)
    throws InterruptedException, ExecutionException, TimeoutException {

    return requestOaiPmhViewUpdatedInstanceIds(queryParamsMap, response -> {
      assertThat(response.getStatusCode(), is(200));
    });
  }

  private List<JsonObject> requestOaiPmhViewUpdatedInstanceIds(Map<String, String> queryParamsMap, Handler<Response> responseMatcher)
    throws InterruptedException, ExecutionException, TimeoutException {

    final String queryParams = queryParamsMap.entrySet()
      .stream()
      .map(e -> e.getKey() + "=" + e.getValue())
      .collect(Collectors.joining("&"));

    CompletableFuture<Response> future = new CompletableFuture<>();
    final List<JsonObject> results = new ArrayList<>();

    client.get(oaiPmhViewUpdatedInstanceIds("?" + queryParams), TENANT_ID, ResponseHandler.any(future));

    final Response response = future.get(2, TimeUnit.SECONDS);
    responseMatcher.handle(response);
    log.info("response from oai pmh updated instances view:", response);

    final String body = response.getBody();
    if (StringUtils.isNotEmpty(body)) {
      results.add(new JsonObject(body));
    }

    return results;
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

}
