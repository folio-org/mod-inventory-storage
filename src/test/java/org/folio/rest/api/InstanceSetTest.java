package org.folio.rest.api;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.rest.support.ResponseHandler.json;
import static org.folio.rest.support.ResponseHandler.text;
import static org.folio.rest.support.http.InterfaceUrls.instanceSetUrl;
import static org.folio.utility.ModuleUtility.getClient;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.net.URL;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.SneakyThrows;
import org.folio.rest.support.Response;
import org.folio.rest.support.builders.HoldingRequestBuilder;
import org.folio.rest.support.builders.ItemRequestBuilder;
import org.folio.util.PercentCodec;
import org.junit.BeforeClass;
import org.junit.Test;

public class InstanceSetTest extends TestBaseWithInventoryUtil {
  private static final UUID INSTANCE_ID_1 = UUID.fromString("10000000-0000-4000-8000-000000000000");
  private static final UUID INSTANCE_ID_2 = UUID.fromString("20000000-0000-4000-8000-000000000000");
  private static final UUID INSTANCE_ID_3 = UUID.fromString("30000000-0000-4000-8000-000000000000");
  private static final UUID INSTANCE_ID_4 = UUID.fromString("40000000-0000-4000-8000-000000000000");
  private static final UUID INSTANCE_ID_5 = UUID.fromString("50000000-0000-4000-8000-000000000000");
  private static final UUID INSTANCE_ID_6 = UUID.fromString("60000000-0000-4000-8000-000000000000");
  private static final UUID INSTANCE_ID_7 = UUID.fromString("70000000-0000-4000-8000-000000000000");
  private static final UUID HOLDING_ID_11 = UUID.fromString("11000000-0000-4000-8000-000000000000");
  private static final UUID HOLDING_ID_61 = UUID.fromString("61000000-0000-4000-8000-000000000000");
  private static final UUID HOLDING_ID_62 = UUID.fromString("62000000-0000-4000-8000-000000000000");
  private static final UUID ITEM_ID_111 = UUID.fromString("11100000-0000-4000-8000-000000000000");
  private static final UUID ITEM_ID_611 = UUID.fromString("61100000-0000-4000-8000-000000000000");
  private static final UUID ITEM_ID_612 = UUID.fromString("61200000-0000-4000-8000-000000000000");
  private static final UUID ITEM_ID_621 = UUID.fromString("62100000-0000-4000-8000-000000000000");

  @BeforeClass
  public static void beforeAll() {
    TestBase.beforeAll();

    createInstance(INSTANCE_ID_1);
    createInstance(INSTANCE_ID_2);
    createInstance(INSTANCE_ID_3);
    createInstance(INSTANCE_ID_4);
    createInstance(INSTANCE_ID_5);
    createInstance(INSTANCE_ID_6);
    createInstance(INSTANCE_ID_7);
    createHolding(INSTANCE_ID_1, HOLDING_ID_11);
    createHolding(INSTANCE_ID_6, HOLDING_ID_61);
    createHolding(INSTANCE_ID_6, HOLDING_ID_62);
    createItem(HOLDING_ID_11, ITEM_ID_111);
    createItem(HOLDING_ID_61, ITEM_ID_611);
    createItem(HOLDING_ID_61, ITEM_ID_612);
    createItem(HOLDING_ID_62, ITEM_ID_621);
    createPrecedingSucceeding(INSTANCE_ID_1, INSTANCE_ID_6);
    createPrecedingSucceeding(INSTANCE_ID_2, INSTANCE_ID_6);
    createPrecedingSucceeding(INSTANCE_ID_6, INSTANCE_ID_3);
    createPrecedingSucceeding(INSTANCE_ID_6, INSTANCE_ID_4);
    createRelationship(INSTANCE_ID_1, INSTANCE_ID_6);
    createRelationship(INSTANCE_ID_4, INSTANCE_ID_6);
    createRelationship(INSTANCE_ID_6, INSTANCE_ID_3);
    createRelationship(INSTANCE_ID_6, INSTANCE_ID_5);
  }

  private static void createHolding(UUID instanceId, UUID holdingId) {
    holdingsClient.create(
      new HoldingRequestBuilder()
        .withId(holdingId)
        .forInstance(instanceId)
        .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID));
  }

  private static void createItem(UUID holdingId, UUID itemId) {
    itemsClient.create(
      new ItemRequestBuilder()
        .forHolding(holdingId)
        .withId(itemId)
        .withBarcode(itemId.toString().substring(0, 3))
        .withMaterialType(bookMaterialTypeId)
        .withPermanentLoanTypeId(canCirculateLoanTypeId));
  }

  private static void createInstance(UUID instanceId) {
    var hrid = "in" + instanceId.toString().charAt(0);
    instancesClient.create(instance(instanceId).put("hrid", hrid));
  }

  private static void createPrecedingSucceeding(UUID precedingInstanceId, UUID succeedingInstanceId) {
    precedingSucceedingTitleClient.create(new JsonObject()
      .put("precedingInstanceId", precedingInstanceId.toString())
      .put("succeedingInstanceId", succeedingInstanceId.toString()));
  }

  private static void createRelationship(UUID superInstanceId, UUID subInstanceId) {
    instanceRelationshipsClient.create(new JsonObject()
      .put("superInstanceId", superInstanceId.toString())
      .put("subInstanceId", subInstanceId.toString())
      .put("instanceRelationshipTypeId", InstanceRelationshipsTest.INSTANCE_RELATIONSHIP_TYPE_ID_BOUNDWITH));
  }

  @Test
  public void shouldReturnAllInstancesWhenNoCql() {
    assertThat(getInstanceSets(null).size(), is(7));
  }

  @Test
  public void canQueryTwoInstances() {
    var sets = getInstanceSets("id==" + INSTANCE_ID_3 + " OR id==" + INSTANCE_ID_5 + " sortBy id");
    assertThat(ids(sets), contains(INSTANCE_ID_3, INSTANCE_ID_5));
  }

  @Test
  public void canQueryByHrid() {
    var sets = getInstanceSets("hrid==in2");
    assertThat(ids(sets), contains(INSTANCE_ID_2));
  }

  @Test
  public void canQueryByItemBarcode() {
    var sets = getInstanceSets("item.barcode==111");
    assertThat(ids(sets), contains(INSTANCE_ID_1));
  }

  @Test
  @SneakyThrows
  public void invalidCqlReturns400() {
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    getClient().get(instanceSetUrl("?limit=1&query=id=="), TENANT_ID, text(getCompleted));
    assertThat(getCompleted.get(10, SECONDS).getStatusCode(), is(400));
  }

  @Test
  public void canQueryWithLimitAndOffset() {
    var sets = getInstanceSets("cql.allRecords=1 sortBy id/sort.ascending", "", 2, 3);
    assertThat(ids(sets), contains(INSTANCE_ID_4, INSTANCE_ID_5));
    sets = getInstanceSets("cql.allRecords=1 sortBy id/sort.descending", "", 2, 3);
    assertThat(ids(sets), contains(INSTANCE_ID_4, INSTANCE_ID_3));
  }

  @Test
  public void canGetEmptyArrays() {
    var parameters = "&holdingsRecords=true&items=true"
      + "&precedingTitles=true&succeedingTitles=true"
      + "&superInstanceRelationships=true&subInstanceRelationships=true";
    var sets = getInstanceSets("id==" + INSTANCE_ID_7, parameters, 1, 0);
    var set = sets.getJsonObject(0);
    // .size() fails if the JsonArray is null
    assertThat(set.getJsonArray("holdingsRecords").size(), is(0));
    assertThat(set.getJsonArray("items").size(), is(0));
    assertThat(set.getJsonArray("precedingTitles").size(), is(0));
    assertThat(set.getJsonArray("succeedingTitles").size(), is(0));
    assertThat(set.getJsonArray("superInstanceRelationships").size(), is(0));
    assertThat(set.getJsonArray("subInstanceRelationships").size(), is(0));
  }

  @Test
  public void canGetInstance() {
    var set = getInstance6("instance=true");
    assertThat(set.fieldNames(), containsInAnyOrder("id", "instance"));
    assertThat(set.getJsonObject("instance").getString("id"), is(INSTANCE_ID_6.toString()));
  }

  @Test
  public void canGetHoldingsRecords() {
    var set = getInstance6("holdingsRecords=true");
    assertThat(set.fieldNames(), containsInAnyOrder("id", "holdingsRecords"));
    var holdingsRecords = set.getJsonArray("holdingsRecords");
    assertThat(ids(holdingsRecords), containsInAnyOrder(HOLDING_ID_61, HOLDING_ID_62));
  }

  @Test
  public void canGetItems() {
    var set = getInstance6("items=true");
    assertThat(set.fieldNames(), containsInAnyOrder("id", "items"));
    var items = set.getJsonArray("items");
    assertThat(ids(items), containsInAnyOrder(ITEM_ID_611, ITEM_ID_612, ITEM_ID_621));
  }

  @Test
  public void canGetPrecedingTitles() {
    var set = getInstance6("precedingTitles=true");
    assertThat(set.fieldNames(), containsInAnyOrder("id", "precedingTitles"));
    var ids = ids(set, "precedingTitles", "precedingInstanceId");
    assertThat(ids, containsInAnyOrder(INSTANCE_ID_1, INSTANCE_ID_2));
  }

  @Test
  public void canGetSucceedingTitles() {
    var set = getInstance6("succeedingTitles=true");
    assertThat(set.fieldNames(), containsInAnyOrder("id", "succeedingTitles"));
    var ids = ids(set, "succeedingTitles", "succeedingInstanceId");
    assertThat(ids, containsInAnyOrder(INSTANCE_ID_3, INSTANCE_ID_4));
  }

  @Test
  public void canGetSuperInstanceRelationships() {
    var set = getInstance6("superInstanceRelationships=true");
    assertThat(set.fieldNames(), containsInAnyOrder("id", "superInstanceRelationships"));
    var ids = ids(set, "superInstanceRelationships", "superInstanceId");
    assertThat(ids, containsInAnyOrder(INSTANCE_ID_1, INSTANCE_ID_4));
  }

  @Test
  public void canGetSubInstanceRelationships() {
    var set = getInstance6("subInstanceRelationships=true");
    assertThat(set.fieldNames(), containsInAnyOrder("id", "subInstanceRelationships"));
    var ids = ids(set, "subInstanceRelationships", "subInstanceId");
    assertThat(ids, containsInAnyOrder(INSTANCE_ID_3, INSTANCE_ID_5));
  }

  private JsonArray getInstanceSets(String cql) {
    return getInstanceSets(cql, "", 10, 0);
  }

  @SneakyThrows
  private JsonArray getInstanceSets(String cql, String parameters, int limit, int offset) {
    String query = cql == null ? "" : "&query=" + PercentCodec.encode(cql);
    URL url = instanceSetUrl("?limit=" + limit + "&offset=" + offset + query + parameters);
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    getClient().get(url, TENANT_ID, json(getCompleted));
    Response response = getCompleted.get(10, SECONDS);
    return response.getJson().getJsonArray("instanceSets");
  }

  @SneakyThrows
  private JsonObject getInstance6(String parameters) {
    URL url = instanceSetUrl("?limit=10&query=id==" + INSTANCE_ID_6 + "&" + parameters);
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    getClient().get(url, TENANT_ID, json(getCompleted));
    var sets = getCompleted.get(10, SECONDS).getJson().getJsonArray("instanceSets");
    assertThat(ids(sets), contains(INSTANCE_ID_6));
    assertThat(sets.size(), is(1));
    return sets.getJsonObject(0);
  }

  private List<UUID> ids(JsonArray sets) {
    return sets
      .stream()
      .map(o -> ((JsonObject) o).getString("id"))
      .map(UUID::fromString)
      .toList();
  }

  private List<UUID> ids(JsonObject set, String arrayName, String stringName) {
    return set.getJsonArray(arrayName)
      .stream()
      .map(o -> ((JsonObject) o).getString(stringName))
      .map(UUID::fromString)
      .toList();
  }
}
