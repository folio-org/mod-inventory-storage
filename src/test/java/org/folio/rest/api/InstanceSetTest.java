package org.folio.rest.api;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.rest.support.ResponseHandler.json;
import static org.folio.rest.support.ResponseHandler.text;
import static org.folio.rest.support.http.InterfaceUrls.instanceSetUrl;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.folio.utility.VertxUtility.getClient;
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
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.folio.rest.support.Response;
import org.folio.rest.support.builders.HoldingRequestBuilder;
import org.folio.rest.support.builders.ItemRequestBuilder;
import org.folio.util.PercentCodec;
import org.junit.BeforeClass;
import org.junit.Test;

public class InstanceSetTest extends TestBaseWithInventoryUtil {
  private static final UUID instanceId1 = UUID.fromString("10000000-0000-4000-8000-000000000000");
  private static final UUID instanceId2 = UUID.fromString("20000000-0000-4000-8000-000000000000");
  private static final UUID instanceId3 = UUID.fromString("30000000-0000-4000-8000-000000000000");
  private static final UUID instanceId4 = UUID.fromString("40000000-0000-4000-8000-000000000000");
  private static final UUID instanceId5 = UUID.fromString("50000000-0000-4000-8000-000000000000");
  private static final UUID instanceId6 = UUID.fromString("60000000-0000-4000-8000-000000000000");
  private static final UUID instanceId7 = UUID.fromString("70000000-0000-4000-8000-000000000000");
  private static final UUID holdingId11 = UUID.fromString("11000000-0000-4000-8000-000000000000");
  private static final UUID holdingId61 = UUID.fromString("61000000-0000-4000-8000-000000000000");
  private static final UUID holdingId62 = UUID.fromString("62000000-0000-4000-8000-000000000000");
  private static final UUID itemId111   = UUID.fromString("11100000-0000-4000-8000-000000000000");
  private static final UUID itemId611   = UUID.fromString("61100000-0000-4000-8000-000000000000");
  private static final UUID itemId612   = UUID.fromString("61200000-0000-4000-8000-000000000000");
  private static final UUID itemId621   = UUID.fromString("62100000-0000-4000-8000-000000000000");

  @BeforeClass
  public static void beforeAll() {
    TestBase.beforeAll();

    createInstance(instanceId1);
    createInstance(instanceId2);
    createInstance(instanceId3);
    createInstance(instanceId4);
    createInstance(instanceId5);
    createInstance(instanceId6);
    createInstance(instanceId7);
    createHolding(instanceId1, holdingId11);
    createHolding(instanceId6, holdingId61);
    createHolding(instanceId6, holdingId62);
    createItem(holdingId11, itemId111);
    createItem(holdingId61, itemId611);
    createItem(holdingId61, itemId612);
    createItem(holdingId62, itemId621);
    createPrecedingSucceeding(instanceId1, instanceId6);
    createPrecedingSucceeding(instanceId2, instanceId6);
    createPrecedingSucceeding(instanceId6, instanceId3);
    createPrecedingSucceeding(instanceId6, instanceId4);
    createRelationship(instanceId1, instanceId6);
    createRelationship(instanceId4, instanceId6);
    createRelationship(instanceId6, instanceId3);
    createRelationship(instanceId6, instanceId5);
  }

  @Test
  public void shouldReturnAllInstancesWhenNoCql() {
    assertThat(getInstanceSets(null).size(), is(7));
  }

  @Test
  public void canQueryTwoInstances() {
    var sets = getInstanceSets("id==" + instanceId3 + " OR id==" + instanceId5 + " sortBy id");
    assertThat(ids(sets), contains(instanceId3, instanceId5));
  }

  @Test
  public void canQueryByHrid() {
    var sets = getInstanceSets("hrid==in2");
    assertThat(ids(sets), contains(instanceId2));
  }

  @Test
  public void canQueryByItemBarcode() {
    var sets = getInstanceSets("item.barcode==111");
    assertThat(ids(sets), contains(instanceId1));
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
    assertThat(ids(sets), contains(instanceId4, instanceId5));
    sets = getInstanceSets("cql.allRecords=1 sortBy id/sort.descending", "", 2, 3);
    assertThat(ids(sets), contains(instanceId4, instanceId3));
  }

  @Test
  public void canGetEmptyArrays() {
    var parameters = "&holdingsRecords=true&items=true"
        + "&precedingTitles=true&succeedingTitles=true"
        + "&superInstanceRelationships=true&subInstanceRelationships=true";
    var sets = getInstanceSets("id==" + instanceId7, parameters, 1, 0);
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
    assertThat(set.getJsonObject("instance").getString("id"), is(instanceId6.toString()));
  }

  @Test
  public void canGetHoldingsRecords() {
    var set = getInstance6("holdingsRecords=true");
    assertThat(set.fieldNames(), containsInAnyOrder("id", "holdingsRecords"));
    var holdingsRecords = set.getJsonArray("holdingsRecords");
    assertThat(ids(holdingsRecords), containsInAnyOrder(holdingId61, holdingId62));
  }

  @Test
  public void canGetItems() {
    var set = getInstance6("items=true");
    assertThat(set.fieldNames(), containsInAnyOrder("id", "items"));
    var items = set.getJsonArray("items");
    assertThat(ids(items), containsInAnyOrder(itemId611, itemId612, itemId621));
  }

  @Test
  public void canGetPrecedingTitles() {
    var set = getInstance6("precedingTitles=true");
    assertThat(set.fieldNames(), containsInAnyOrder("id", "precedingTitles"));
    var ids = ids(set, "precedingTitles", "precedingInstanceId");
    assertThat(ids, containsInAnyOrder(instanceId1, instanceId2));
  }

  @Test
  public void canGetSucceedingTitles() {
    var set = getInstance6("succeedingTitles=true");
    assertThat(set.fieldNames(), containsInAnyOrder("id", "succeedingTitles"));
    var ids = ids(set, "succeedingTitles", "succeedingInstanceId");
    assertThat(ids, containsInAnyOrder(instanceId3, instanceId4));
  }

  @Test
  public void canGetSuperInstanceRelationships() {
    var set = getInstance6("superInstanceRelationships=true");
    assertThat(set.fieldNames(), containsInAnyOrder("id", "superInstanceRelationships"));
    var ids = ids(set, "superInstanceRelationships", "superInstanceId");
    assertThat(ids, containsInAnyOrder(instanceId1, instanceId4));
  }

  @Test
  public void canGetSubInstanceRelationships() {
    var set = getInstance6("subInstanceRelationships=true");
    assertThat(set.fieldNames(), containsInAnyOrder("id", "subInstanceRelationships"));
    var ids = ids(set, "subInstanceRelationships", "subInstanceId");
    assertThat(ids, containsInAnyOrder(instanceId3, instanceId5));
  }

  private static void createHolding(UUID instanceId, UUID holdingId) {
    holdingsClient.create(
        new HoldingRequestBuilder()
          .withId(holdingId)
          .forInstance(instanceId)
          .withPermanentLocation(mainLibraryLocationId));
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
    var hrid = "in" + instanceId.toString().substring(0, 1);
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
    URL url = instanceSetUrl("?limit=10&query=id==" + instanceId6.toString() + "&" + parameters);
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    getClient().get(url, TENANT_ID, json(getCompleted));
    var sets = getCompleted.get(10, SECONDS).getJson().getJsonArray("instanceSets");
    assertThat(ids(sets), contains(instanceId6));
    assertThat(sets.size(), is(1));
    return sets.getJsonObject(0);
  }

  private List<UUID> ids(JsonArray sets) {
    return sets
        .stream()
        .map(o -> ((JsonObject)o).getString("id"))
        .map(UUID::fromString)
        .collect(Collectors.toList());
  }

  private List<UUID> ids(JsonObject set, String arrayName, String stringName) {
    return set.getJsonArray(arrayName)
      .stream()
      .map(o -> ((JsonObject)o).getString(stringName))
      .map(UUID::fromString)
      .collect(Collectors.toList());
  }
}
