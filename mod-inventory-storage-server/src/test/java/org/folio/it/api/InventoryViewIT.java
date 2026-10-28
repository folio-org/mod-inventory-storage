package org.folio.it.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.HttpStatus.SC_OK;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.it.HoldingsStorageFixtures.createHolding;
import static org.folio.it.HoldingsStorageFixtures.createItem;
import static org.folio.it.HoldingsStorageFixtures.createLoanType;
import static org.folio.it.HoldingsStorageFixtures.createMaterialType;
import static org.folio.it.InstanceStorageFixtures.createInstance;
import static org.folio.it.InstanceStorageFixtures.createInstanceType;
import static org.folio.it.LocationStorageFixtures.createLocation;

import io.vertx.core.json.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.folio.it.BaseIntegrationTest;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.InstanceView;
import org.folio.rest.jaxrs.model.Item;
import org.folio.support.ResourcePaths;
import org.folio.support.builders.InstanceRequestBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Covers the {@code /inventory-view/instances} rollup endpoint, which returns each matching
 * instance together with its holdings and items (and, with {@code withBoundedItems=true},
 * items bound to its holdings from other instances too).
 */
class InventoryViewIT extends BaseIntegrationTest {

  private static final String INSTANCES_FIELD = "instances";
  private static final String HOLDINGS_RECORD_ID_FIELD = "holdingsRecordId";
  private static final String ITEM_ID_FIELD = "itemId";

  private static String instanceTypeId;
  private static String materialTypeId;
  private static String loanTypeId;
  private static String locationId;

  @BeforeAll
  static void seedReferenceData() {
    instanceTypeId = createInstanceType(client);
    materialTypeId = createMaterialType(client);
    loanTypeId = createLoanType(client);
    locationId = createLocation(client);
  }

  @BeforeEach
  void clearRecords() {
    runQuery("TRUNCATE TABLE bound_with_part, item, holdings_record, instance CASCADE");
  }

  @Test
  @DisplayName("should return instances with their holdings and items")
  void shouldReturnInstancesWithHoldingsAndItems() {
    var instanceOneId = createInstance(client, "Instance One", instanceTypeId);
    var holdingsForOne = createHoldings(instanceOneId, 2);
    var itemsForOne = createItemsForHoldings(holdingsForOne);

    var instanceTwoId = createInstance(client, "Instance Two", instanceTypeId);
    var holdingsForTwo = createHoldings(instanceTwoId, 3);
    var itemsForTwo = createItemsForHoldings(holdingsForTwo);

    var instances = getInstanceViews(cqlForIds(instanceOneId, instanceTwoId), false);

    assertThat(instances).hasSize(2);

    var firstInstance = getInstanceById(instances, instanceOneId);
    var secondInstance = getInstanceById(instances, instanceTwoId);

    assertThat(getHoldingIds(firstInstance)).containsExactlyInAnyOrderElementsOf(holdingsForOne);
    assertThat(getItemIds(firstInstance)).containsExactlyInAnyOrderElementsOf(itemsForOne);

    assertThat(getHoldingIds(secondInstance)).containsExactlyInAnyOrderElementsOf(holdingsForTwo);
    assertThat(getItemIds(secondInstance)).containsExactlyInAnyOrderElementsOf(itemsForTwo);
  }

  @Test
  @DisplayName("should return an instance with empty items when it has holdings but no items")
  void shouldReturnInstanceWithEmptyItems_whenInstanceHasNoItems() {
    var instanceOneId = createInstance(client, "Instance One", instanceTypeId);
    var holdingForOne = createHolding(client, instanceOneId, locationId);

    var instanceTwoId = createInstance(client, "Instance Two", instanceTypeId);
    var holdingsForTwo = createHoldings(instanceTwoId, 3);

    var instances = getInstanceViews(cqlForIds(instanceOneId, instanceTwoId), false);

    assertThat(instances).hasSize(2);

    var firstInstance = getInstanceById(instances, instanceOneId);
    var secondInstance = getInstanceById(instances, instanceTwoId);

    assertThat(firstInstance.getHoldingsRecords()).extracting(HoldingsRecord::getId)
      .containsExactly(holdingForOne);
    assertThat(getHoldingIds(secondInstance)).containsExactlyInAnyOrderElementsOf(holdingsForTwo);

    assertThat(firstInstance.getItems()).isEmpty();
    assertThat(secondInstance.getItems()).isEmpty();
  }

  @Test
  @DisplayName("should return instances with empty holdings and items when they have no holdings")
  void shouldReturnInstancesWithEmptyHoldingsAndItems_whenInstancesHaveNoHoldings() {
    var instanceOneId = createInstance(client, "Instance One", instanceTypeId);
    var instanceTwoId = createInstance(client, "Instance Two", instanceTypeId);

    var instances = getInstanceViews(cqlForIds(instanceOneId, instanceTwoId), false);

    assertThat(instances).hasSize(2);

    for (var instance : instances) {
      assertThat(instance.getHoldingsRecords()).isEmpty();
      assertThat(instance.getItems()).isEmpty();
      assertThat(instance.getInstanceId()).isIn(instanceOneId, instanceTwoId);
    }
  }

  @Test
  @DisplayName("should return items bound to a holding from another instance when withBoundedItems is true")
  void shouldReturnBoundItems_whenWithBoundedItemsTrue() {
    var instanceOneId = createInstance(client, "Instance One", instanceTypeId);
    var holdingsForOne = createHoldings(instanceOneId, 2);
    var itemsForOne = createItemsForHoldings(holdingsForOne);

    var instanceTwoId = createInstance(client, "Instance Two", instanceTypeId);
    var holdingForTwo = createHolding(client, instanceTwoId, locationId);
    final var ownItemForTwo = createItem(client, holdingForTwo, materialTypeId, loanTypeId);
    // an item created under instance one's first holding, bound instead to instance two's holding
    createBoundWithPart(holdingForTwo, itemsForOne.get(1));

    var instances = getInstanceViews(cqlForIds(instanceOneId, instanceTwoId), true);

    assertThat(instances).hasSize(2);

    var firstInstance = getInstanceById(instances, instanceOneId);
    var secondInstance = getInstanceById(instances, instanceTwoId);

    assertThat(getHoldingIds(firstInstance)).containsExactlyInAnyOrderElementsOf(holdingsForOne);
    assertThat(getItemIds(firstInstance)).containsExactlyInAnyOrderElementsOf(itemsForOne);

    assertThat(getHoldingIds(secondInstance)).containsExactly(holdingForTwo);
    assertThat(getItemIds(secondInstance)).containsExactlyInAnyOrder(ownItemForTwo, itemsForOne.get(1));
  }

  @Test
  @DisplayName("should return an instance with empty items when withBoundedItems is true and it has no items")
  void shouldReturnInstanceWithEmptyItems_whenWithBoundedItemsTrueAndNoItems() {
    var instanceOneId = createInstance(client, "Instance One", instanceTypeId);
    var holdingForOne = createHolding(client, instanceOneId, locationId);

    var instanceTwoId = createInstance(client, "Instance Two", instanceTypeId);
    var holdingsForTwo = createHoldings(instanceTwoId, 3);

    var instances = getInstanceViews(cqlForIds(instanceOneId, instanceTwoId), true);

    assertThat(instances).hasSize(2);

    var firstInstance = getInstanceById(instances, instanceOneId);
    var secondInstance = getInstanceById(instances, instanceTwoId);

    assertThat(firstInstance.getHoldingsRecords()).extracting(HoldingsRecord::getId)
      .containsExactly(holdingForOne);
    assertThat(getHoldingIds(secondInstance)).containsExactlyInAnyOrderElementsOf(holdingsForTwo);

    assertThat(firstInstance.getItems()).isEmpty();
    assertThat(secondInstance.getItems()).isEmpty();
  }

  @Test
  @DisplayName("should return instances with empty holdings and items when withBoundedItems is true and "
    + "they have no holdings")
  void shouldReturnInstancesWithEmptyHoldingsAndItems_whenWithBoundedItemsTrueAndNoHoldings() {
    var instanceOneId = createInstance(client, "Instance One", instanceTypeId);
    var instanceTwoId = createInstance(client, "Instance Two", instanceTypeId);

    var instances = getInstanceViews(cqlForIds(instanceOneId, instanceTwoId), true);

    assertThat(instances).hasSize(2);

    for (var instance : instances) {
      assertThat(instance.getHoldingsRecords()).isEmpty();
      assertThat(instance.getItems()).isEmpty();
      assertThat(instance.getInstanceId()).isIn(instanceOneId, instanceTwoId);
    }
  }

  @Test
  @DisplayName("should return non-suppressed instances when withBoundedItems is true and they have no holdings")
  void shouldReturnNotSuppressedInstances_whenWithBoundedItemsTrueAndNoHoldings() {
    var instanceOneId = createInstance(client, "Instance One", instanceTypeId);
    var instanceTwoId = createInstance(client, "Instance Two", instanceTypeId);

    var query = "instance.discoverySuppress<>true and " + cqlForIds(instanceOneId, instanceTwoId);
    var instances = getInstanceViews(query, true);

    assertThat(instances).hasSize(2);

    for (var instance : instances) {
      assertThat(instance.getHoldingsRecords()).isEmpty();
      assertThat(instance.getItems()).isEmpty();
      assertThat(instance.getInstanceId()).isIn(instanceOneId, instanceTwoId);
    }
  }

  @Test
  @DisplayName("should expose the indexed lower-cased hrid in the instance_hrid view column")
  void shouldExposeInstanceHridColumn() {
    createInstanceWithHrid("ABC-1");

    var rows = runQuery("SELECT instance_hrid FROM " + TENANT_ID
      + "_mod_inventory_storage.instance_holdings_item_view");

    assertThat(rows.iterator().next().getString("instance_hrid")).isEqualTo("abc-1");
  }

  @ParameterizedTest
  @ValueSource(strings = {"instance.hrid==in2", "instance.hrid=in2", "instanceHrid==in2",
    "(instance.hrid=in2)", "instance.hrid==IN2"})
  @DisplayName("should return the instance with its holdings and items when queried by hrid")
  void shouldReturnInstance_whenQueriedByHrid(String query) {
    var one = createInstanceWithHrid("in1");
    var two = createInstanceWithHrid("in2");
    var holdings = createHoldings(two, 2);
    var items = createItemsForHoldings(holdings);

    for (var withBoundedItems : List.of(false, true)) {
      var instances = getInstanceViews(query, withBoundedItems);

      assertThat(instances).hasSize(1);
      var instance = instances.get(0);
      assertThat(instance.getInstanceId()).isEqualTo(two).isNotEqualTo(one);
      assertThat(getHoldingIds(instance)).containsExactlyInAnyOrderElementsOf(holdings);
      assertThat(getItemIds(instance)).containsExactlyInAnyOrderElementsOf(items);
    }
  }

  @Test
  @DisplayName("should return no instances when no instance has the hrid")
  void shouldReturnNoInstances_whenHridDoesNotExist() {
    createInstanceWithHrid("in1");

    assertThat(getInstanceViews("instance.hrid==in999", false)).isEmpty();
    assertThat(getInstanceViews("instance.hrid==in999", true)).isEmpty();
  }

  @Test
  @DisplayName("should ignore case and accents when queried by hrid")
  void shouldIgnoreCaseAndAccents_whenQueriedByHrid() {
    var id = createInstanceWithHrid("Éclair-1");

    assertThat(getInstanceViews("instance.hrid==eclair-1", false))
      .extracting(InstanceView::getInstanceId).containsExactly(id);
  }

  @Test
  @DisplayName("should not match a partial hrid when queried by hrid with the word match operator")
  void shouldNotMatchPartialHrid_whenQueriedWithWordMatchOperator() {
    createInstanceWithHrid("ab cd");

    assertThat(getInstanceViews("instance.hrid=ab", false)).isEmpty();
    assertThat(getInstanceViews("instance.hrid==\"ab cd\"", false)).hasSize(1);
  }

  @Test
  @DisplayName("should honour offset and limit when queried by hrid")
  void shouldHonourOffsetAndLimit_whenQueriedByHrid() {
    createInstanceWithHrid("in1");

    assertThat(getInstanceViews("instance.hrid==in1", false, "&offset=1")).isEmpty();
    assertThat(getInstanceViews("instance.hrid==in1", false, "&limit=0")).isEmpty();
    assertThat(getInstanceViews("instance.hrid==in1", false, "&limit=1")).hasSize(1);
  }

  @Test
  @DisplayName("should keep the generic behaviour when the query is not a single hrid term")
  void shouldKeepGenericBehaviour_whenQueryIsNotSingleHridTerm() {
    createInstanceWithHrid("in10");
    createInstanceWithHrid("in11");
    createInstanceWithHrid("in2");

    assertThat(getInstanceViews("instance.hrid==in1*", false)).hasSize(2);
    assertThat(getInstanceViews("instance.hrid==in10 or instance.hrid==in2", false)).hasSize(2);
  }

  private static String createInstanceWithHrid(String hrid) {
    var id = UUID.randomUUID();
    var request = new InstanceRequestBuilder()
      .withId(id)
      .withTitle("Instance " + hrid)
      .withSource("TEST")
      .withInstanceTypeId(UUID.fromString(instanceTypeId))
      .create()
      .put("hrid", hrid);

    await(doPost(client, ResourcePaths.INSTANCES, request));
    return id.toString();
  }

  private static List<String> createHoldings(String instanceId, int count) {
    return IntStream.range(0, count)
      .mapToObj(i -> createHolding(client, instanceId, locationId))
      .toList();
  }

  private static List<String> createItemsForHoldings(List<String> holdingIds) {
    return holdingIds.stream()
      .map(holdingId -> createItem(client, holdingId, materialTypeId, loanTypeId))
      .toList();
  }

  private static void createBoundWithPart(String holdingsRecordId, String itemId) {
    var request = new JsonObject()
      .put(HOLDINGS_RECORD_ID_FIELD, holdingsRecordId)
      .put(ITEM_ID_FIELD, itemId);

    await(doPost(client, ResourcePaths.BOUND_WITH_PARTS, request));
  }

  private static String cqlForIds(String... ids) {
    return "id==(" + String.join(" or ", ids) + ")";
  }

  private static List<InstanceView> getInstanceViews(String cqlQuery, boolean withBoundedItems) {
    return getInstanceViews(cqlQuery, withBoundedItems, "");
  }

  private static List<InstanceView> getInstanceViews(String cqlQuery, boolean withBoundedItems,
                                                     String extraParams) {
    var queryString = "?query=" + urlEncode(cqlQuery) + (withBoundedItems ? "&withBoundedItems=true" : "")
      + extraParams;
    var response = await(doGet(client, ResourcePaths.INVENTORY_VIEW_INSTANCES + queryString));

    assertThat(response.status()).isEqualTo(SC_OK);

    return response.jsonBody().getJsonArray(INSTANCES_FIELD).stream()
      .map(json -> ((JsonObject) json).mapTo(InstanceView.class))
      .toList();
  }

  private static InstanceView getInstanceById(List<InstanceView> instances, String instanceId) {
    return instances.stream()
      .filter(instance -> instance.getInstanceId().equals(instanceId))
      .findFirst()
      .orElseThrow(() -> new AssertionError("Instance not found: " + instanceId));
  }

  private static List<String> getHoldingIds(InstanceView instance) {
    return instance.getHoldingsRecords().stream().map(HoldingsRecord::getId).toList();
  }

  private static List<String> getItemIds(InstanceView instance) {
    return instance.getItems().stream().map(Item::getId).toList();
  }

  private static String urlEncode(String value) {
    return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
