package org.folio.rest.api;

import static org.folio.rest.api.StorageTestSuite.deleteAll;
import static org.folio.rest.support.http.InterfaceUrls.holdingsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.instancesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.internalHoldingsStreamUrl;
import static org.folio.rest.support.http.InterfaceUrls.internalInstanceStreamUrl;
import static org.folio.rest.support.http.InterfaceUrls.internalItemStreamUrl;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.folio.utility.ModuleUtility.getClient;
import static org.folio.utility.ModuleUtility.getVertx;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.HttpResponse;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import lombok.SneakyThrows;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.builders.ItemRequestBuilder;
import org.folio.rest.tools.utils.TenantTool;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class InternalStreamTest extends TestBaseWithInventoryUtil {

  @SneakyThrows
  @Before
  public void beforeEach() {
    deleteAll(TENANT_ID, "bound_with_part");
    deleteAll(itemsStorageUrl(""));
    deleteAll(holdingsStorageUrl(""));
    deleteAll(instancesStorageUrl(""));
    removeAllEvents();
  }

  @Test
  public void emptyStreamReturnsNoRecords() throws Exception {
    for (var urlFn : streamEndpoints()) {
      HttpResponse<Buffer> response = streamGet(urlFn.apply(""));
      assertThat(response.statusCode(), is(200));
      assertThat(response.getHeader("X-Has-More"), is("false"));
      assertNull(response.getHeader("X-Next-Cursor"));
      assertTrue(bodyString(response).isEmpty());
    }
  }

  @Test
  public void invalidCursorReturns400() throws Exception {
    for (var urlFn : streamEndpoints()) {
      assertThat(streamGet(urlFn.apply("?cursor=invalid")).statusCode(), is(400));
    }
  }

  @Test
  public void limitAboveMaxReturns400() throws Exception {
    for (var urlFn : streamEndpoints()) {
      assertThat(streamGet(urlFn.apply("?limit=200001")).statusCode(), is(400));
    }
  }

  @Test
  public void singleInstanceRecord() throws Exception {
    createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    HttpResponse<Buffer> response = streamGet(internalInstanceStreamUrl(""));
    assertThat(response.statusCode(), is(200));
    assertThat(response.getHeader("X-Has-More"), is("false"));
    assertThat(response.getHeader("Content-Type"), is("application/x-ndjson"));

    List<JsonObject> records = parseNdjson(response);
    assertThat(records.size(), is(1));
    assertNotNull(records.getFirst().getString("id"));
    assertNotNull(records.getFirst().getString("title"));
  }

  @Test
  public void singleHoldingsRecordIncludesInstanceId() throws Exception {
    createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    String instanceId = instancesClient.getAll().getFirst().getString("id");

    List<JsonObject> records = parseNdjson(streamGet(internalHoldingsStreamUrl("")));
    assertThat(records.size(), is(1));
    assertEquals(instanceId, records.getFirst().getString("instanceId"));
  }

  @Test
  public void singleItemRecordIncludesHoldingsAndInstanceId() throws Exception {
    UUID holdingsId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    createItem(holdingsId, MAIN_LIBRARY_LOCATION_ID, "barcode1", "cn1");
    String instanceId = instancesClient.getAll().getFirst().getString("id");

    JsonObject item = parseNdjson(streamGet(internalItemStreamUrl(""))).getFirst();
    assertEquals(holdingsId.toString(), item.getString("holdingsRecordId"));
    assertEquals(instanceId, item.getString("instanceId"));
  }

  @Test
  public void instancePaginationNoGapsNoDuplicates() throws Exception {
    for (int i = 0; i < 5; i++) {
      createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    }
    assertThat(paginateAll(q -> internalInstanceStreamUrl("?limit=2" + q)).size(), is(5));
  }

  @Test
  public void holdingsPaginationNoGapsNoDuplicates() throws Exception {
    for (int i = 0; i < 5; i++) {
      createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    }
    assertThat(paginateAll(q -> internalHoldingsStreamUrl("?limit=2" + q)).size(), is(5));
  }

  @Test
  public void itemPaginationNoGapsNoDuplicates() throws Exception {
    UUID holdingsId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    for (int i = 0; i < 5; i++) {
      createItem(holdingsId, MAIN_LIBRARY_LOCATION_ID, "barcode" + i, "cn" + i);
    }
    assertThat(paginateAll(q -> internalItemStreamUrl("?limit=2" + q)).size(), is(5));
  }

  @Test
  public void hasMoreTrueWhenMoreRecordsExist() throws Exception {
    for (int i = 0; i < 3; i++) {
      createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    }
    HttpResponse<Buffer> response = streamGet(internalInstanceStreamUrl("?limit=2"));
    assertThat(response.getHeader("X-Has-More"), is("true"));
    assertNotNull(response.getHeader("X-Next-Cursor"));
    List<JsonObject> records = parseNdjson(response);
    assertThat(records.size(), is(2));
    assertThat(response.getHeader("X-Next-Cursor"), is(records.getLast().getString("id")));
  }

  @Test
  public void maxLimitIsAccepted() throws Exception {
    createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    HttpResponse<Buffer> response = streamGet(internalInstanceStreamUrl("?limit=200000"));
    assertThat(response.statusCode(), is(200));
    assertThat(response.getHeader("X-Has-More"), is("false"));
    assertThat(parseNdjson(response).size(), is(1));
  }

  @Test
  public void hasMoreFalseWhenExactlyLimitRecords() throws Exception {
    for (int i = 0; i < 2; i++) {
      createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    }
    HttpResponse<Buffer> response = streamGet(internalInstanceStreamUrl("?limit=2"));
    assertThat(response.getHeader("X-Has-More"), is("false"));
    assertNull(response.getHeader("X-Next-Cursor"));
    assertThat(parseNdjson(response).size(), is(2));
  }

  @Test
  public void hasMoreFalseWhenFewerThanLimitRecords() throws Exception {
    createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    HttpResponse<Buffer> response = streamGet(internalInstanceStreamUrl("?limit=5"));
    assertThat(response.getHeader("X-Has-More"), is("false"));
    assertNull(response.getHeader("X-Next-Cursor"));
    assertThat(parseNdjson(response).size(), is(1));
  }

  @Test
  public void orphanItemsExcluded() throws Exception {
    UUID holdingsId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    createItem(holdingsId, MAIN_LIBRARY_LOCATION_ID, "barcode1", "cn1");
    assertThat(parseNdjson(streamGet(internalItemStreamUrl(""))).size(), is(1));

    // Clear items/holdings, then insert orphan bypassing FK constraints
    executeTenantSql("DELETE FROM item; DELETE FROM holdings_record");
    UUID orphanId = UUID.randomUUID();
    UUID fakeHoldingsId = UUID.randomUUID();
    String schema = TenantTool.calculateTenantId(TENANT_ID) + "_mod_inventory_storage";
    executeSuperuserSql(
      "SET session_replication_role = replica; "
        + "INSERT INTO " + schema + ".item (id, jsonb, holdingsrecordid) VALUES "
        + "('" + orphanId + "', "
        + "'{\"id\":\"" + orphanId + "\",\"holdingsRecordId\":\"" + fakeHoldingsId
        + "\",\"status\":{\"name\":\"Available\"}}'::jsonb, "
        + "'" + fakeHoldingsId + "'); "
        + "SET session_replication_role = DEFAULT");

    HttpResponse<Buffer> response = streamGet(internalItemStreamUrl(""));
    assertThat(parseNdjson(response).size(), is(0));
    assertThat(response.getHeader("X-Has-More"), is("false"));
  }

  // --- Helpers ---

  @SuppressWarnings("unchecked")
  private static List<Function<String, URL>> streamEndpoints() {
    return Arrays.asList(
      q -> internalInstanceStreamUrl(q),
      q -> internalHoldingsStreamUrl(q),
      q -> internalItemStreamUrl(q)
    );
  }

  private Set<String> paginateAll(Function<String, URL> urlFn) throws Exception {
    Set<String> allIds = new HashSet<>();
    String cursor = null;
    int pageCount = 0;

    do {
      String cursorParam = cursor != null ? "&cursor=" + cursor : "";
      HttpResponse<Buffer> response = streamGet(urlFn.apply(cursorParam));
      assertThat(response.statusCode(), is(200));

      for (JsonObject record : parseNdjson(response)) {
        assertTrue("Duplicate id: " + record.getString("id"),
          allIds.add(record.getString("id")));
      }

      cursor = response.getHeader("X-Next-Cursor");
      pageCount++;

      if (!"true".equals(response.getHeader("X-Has-More"))) {
        break;
      }
    } while (cursor != null);

    assertTrue("Should take multiple pages", pageCount > 1);
    return allIds;
  }

  private HttpResponse<Buffer> streamGet(URL url) throws Exception {
    CompletableFuture<HttpResponse<Buffer>> future = new CompletableFuture<>();
    getClient().request(HttpMethod.GET, url, TENANT_ID)
      .onSuccess(future::complete)
      .onFailure(future::completeExceptionally);
    return future.get(TIMEOUT, TimeUnit.SECONDS);
  }

  private static String bodyString(HttpResponse<Buffer> response) {
    String body = response.bodyAsString();
    return body != null ? body : "";
  }

  private static List<JsonObject> parseNdjson(HttpResponse<Buffer> response) {
    String body = response.bodyAsString();
    if (body == null || body.isEmpty()) {
      return List.of();
    }
    List<JsonObject> results = new ArrayList<>();
    for (String line : body.split("\n")) {
      if (!line.isBlank()) {
        results.add(new JsonObject(line));
      }
    }
    return results;
  }

  private void executeTenantSql(String sql) throws Exception {
    CompletableFuture<Void> future = new CompletableFuture<>();
    PostgresClient.getInstance(getVertx(), TenantTool.calculateTenantId(TENANT_ID))
      .execute(sql, ar -> {
        if (ar.failed()) {
          future.completeExceptionally(ar.cause());
        } else {
          future.complete(null);
        }
      });
    future.get(TIMEOUT, TimeUnit.SECONDS);
  }

  private void executeSuperuserSql(String sql) throws Exception {
    PostgresClient.getInstance(getVertx()).execute(sql)
      .toCompletionStage().toCompletableFuture().get(TIMEOUT, TimeUnit.SECONDS);
  }

  private void createItem(UUID holdingsId, UUID locationId, String barcode, String callNumber) {
    super.createItem(new ItemRequestBuilder()
      .forHolding(holdingsId)
      .withPermanentLoanType(canCirculateLoanTypeId)
      .withTemporaryLocation(locationId)
      .withBarcode(barcode)
      .withItemLevelCallNumber(callNumber)
      .withMaterialType(journalMaterialTypeId)
      .create());
  }
}
