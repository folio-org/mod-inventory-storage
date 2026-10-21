package org.folio.rest.api;

import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.rest.api.ItemStorageTest.nodWithNoBarcode;
import static org.folio.utility.ModuleUtility.getVertx;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertTrue;

import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.InventoryViewInstance;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.IndividualResource;
import org.folio.util.StringUtil;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Test;

public class InventoryViewTest extends TestBaseWithInventoryUtil {
  @Test
  public void shouldReturnInstanceWithRecords() {
    var instanceOne = instancesClient.create(instance(randomUUID()));
    var holdingsForOne = List.of(
      createHolding(instanceOne.getId(), MAIN_LIBRARY_LOCATION_ID, null),
      createHolding(instanceOne.getId(), SECOND_FLOOR_LOCATION_ID, null)
    );
    var itemsForOne = List.of(
      createItem(nodWithNoBarcode(holdingsForOne.get(0))).getString("id"),
      createItem(nodWithNoBarcode(holdingsForOne.get(1))).getString("id")
    );

    var instanceTwo = instancesClient.create(instance(randomUUID()));
    var holdingsForTwo = List.of(
      createHolding(instanceTwo.getId(), MAIN_LIBRARY_LOCATION_ID, null),
      createHolding(instanceTwo.getId(), SECOND_FLOOR_LOCATION_ID, null),
      createHolding(instanceTwo.getId(), FOURTH_FLOOR_LOCATION_ID, null));
    final var itemsForTwo = List.of(
      createItem(nodWithNoBarcode(holdingsForTwo.get(0))).getString("id"),
      createItem(nodWithNoBarcode(holdingsForTwo.get(0))).getString("id"),
      createItem(nodWithNoBarcode(holdingsForTwo.get(1))).getString("id"),
      createItem(nodWithNoBarcode(holdingsForTwo.get(2))).getString("id"));

    var instances = inventoryViewClient.getMany("id==(%s or %s)",
      instanceTwo.getId(), instanceOne.getId());

    assertThat(instances.size(), is(2));

    var firstInstance = getInstanceById(instances, instanceOne.getId());
    var secondInstance = getInstanceById(instances, instanceTwo.getId());

    assertThat(getHoldingIds(firstInstance), matchesInAnyOrder(holdingsForOne));
    assertThat(getItemIds(firstInstance), matchesInAnyOrder(itemsForOne));

    assertThat(getHoldingIds(secondInstance), matchesInAnyOrder(holdingsForTwo));
    assertThat(getItemIds(secondInstance), matchesInAnyOrder(itemsForTwo));
  }

  @Test
  public void shouldReturnInstanceEvenIfNoItems() {
    var instanceOne = instancesClient.create(instance(randomUUID()));
    var holdingForOne = createHolding(instanceOne.getId(), MAIN_LIBRARY_LOCATION_ID, null);

    var instanceTwo = instancesClient.create(instance(randomUUID()));
    var holdingsForTwo = List.of(
      createHolding(instanceTwo.getId(), MAIN_LIBRARY_LOCATION_ID, null),
      createHolding(instanceTwo.getId(), SECOND_FLOOR_LOCATION_ID, null),
      createHolding(instanceTwo.getId(), FOURTH_FLOOR_LOCATION_ID, null));

    var instances = inventoryViewClient.getMany("id==(%s or %s)",
      instanceTwo.getId(), instanceOne.getId());

    assertThat(instances.size(), is(2));

    var firstInstance = getInstanceById(instances, instanceOne.getId());
    var secondInstance = getInstanceById(instances, instanceTwo.getId());

    assertThat(firstInstance.getHoldingsRecords().getFirst().getId(), is(holdingForOne.toString()));
    assertThat(getHoldingIds(secondInstance), matchesInAnyOrder(holdingsForTwo));

    isNonNullEmpty(firstInstance.getItems());
    isNonNullEmpty(secondInstance.getItems());
  }

  @Test
  public void shouldReturnInstanceEvenIfNoHoldings() {
    var instanceOne = instancesClient.create(instance(randomUUID()));
    var instanceTwo = instancesClient.create(instance(randomUUID()));

    var instances = inventoryViewClient.getMany("id==(%s or %s)",
      instanceTwo.getId(), instanceOne.getId());

    assertThat(instances.size(), is(2));

    var returnedInstances = instances.stream()
      .map(resource -> resource.getJson().mapTo(InventoryViewInstance.class))
      .toList();

    for (InventoryViewInstance returnedInstance : returnedInstances) {
      isNonNullEmpty(returnedInstance.getHoldingsRecords());
      isNonNullEmpty(returnedInstance.getItems());

      assertTrue(returnedInstance.getInstanceId().equals(instanceOne.getId().toString())
                 || returnedInstance.getInstanceId().equals(instanceTwo.getId().toString()));
    }
  }

  @Test
  public void shouldReturnInstanceWithBoundedItemsRecords_whenWithBoundedItemsTrue() {
    //given
    var instanceOne = instancesClient.create(instance(randomUUID()));
    var holdingsForOne = List.of(
      createHolding(instanceOne.getId(), MAIN_LIBRARY_LOCATION_ID, null),
      createHolding(instanceOne.getId(), SECOND_FLOOR_LOCATION_ID, null)
    );
    var itemsForOne = List.of(
      createItem(nodWithNoBarcode(holdingsForOne.get(0))).getString("id"),
      createItem(nodWithNoBarcode(holdingsForOne.get(1))).getString("id")
    );

    var instanceTwo = instancesClient.create(instance(randomUUID()));
    var holdingsForTwo = List.of(
      createHolding(instanceTwo.getId(), MAIN_LIBRARY_LOCATION_ID, null));
    final var itemsForTwo = List.of(
      createItem(nodWithNoBarcode(holdingsForTwo.getFirst())).getString("id"),
      itemsForOne.get(1));

    boundWithClient.create(
      createBoundWithPartJson(holdingsForTwo.getFirst().toString(), itemsForOne.get(1)));

    //when
    String query = getQueryWithBoundedItems(instanceOne, instanceTwo, "id==(%s or %s)");
    var instances = inventoryViewClient.getByQuery(query)
      .stream()
      .map(IndividualResource::new)
      .toList();

    //then
    assertThat(instances.size(), is(2));

    var firstInstance = getInstanceById(instances, instanceOne.getId());
    var secondInstance = getInstanceById(instances, instanceTwo.getId());

    assertThat(getHoldingIds(firstInstance), matchesInAnyOrder(holdingsForOne));
    assertThat(getItemIds(firstInstance), matchesInAnyOrder(itemsForOne));

    assertThat(getHoldingIds(secondInstance), matchesInAnyOrder(holdingsForTwo));
    assertThat(getItemIds(secondInstance), matchesInAnyOrder(itemsForTwo));
  }

  @Test
  public void shouldReturnInstanceEvenIfNoItems_whenWithBoundedItemsTrue() {
    //given
    var instanceOne = instancesClient.create(instance(randomUUID()));
    var holdingForOne = createHolding(instanceOne.getId(), MAIN_LIBRARY_LOCATION_ID, null);

    var instanceTwo = instancesClient.create(instance(randomUUID()));
    var holdingsForTwo = List.of(
      createHolding(instanceTwo.getId(), MAIN_LIBRARY_LOCATION_ID, null),
      createHolding(instanceTwo.getId(), SECOND_FLOOR_LOCATION_ID, null),
      createHolding(instanceTwo.getId(), FOURTH_FLOOR_LOCATION_ID, null));

    //when
    String query = getQueryWithBoundedItems(instanceOne, instanceTwo, "id==(%s or %s)");
    var instances = inventoryViewClient.getByQuery(query)
      .stream()
      .map(IndividualResource::new)
      .toList();

    //then
    assertThat(instances.size(), is(2));

    var firstInstance = getInstanceById(instances, instanceOne.getId());
    var secondInstance = getInstanceById(instances, instanceTwo.getId());

    assertThat(firstInstance.getHoldingsRecords().getFirst().getId(), is(holdingForOne.toString()));
    assertThat(getHoldingIds(secondInstance), matchesInAnyOrder(holdingsForTwo));

    isNonNullEmpty(firstInstance.getItems());
    isNonNullEmpty(secondInstance.getItems());
  }

  @Test
  public void shouldReturnInstanceEvenIfNoHoldings_whenWithBoundedItemsTrue() {
    //given
    var instanceOne = instancesClient.create(instance(randomUUID()));
    var instanceTwo = instancesClient.create(instance(randomUUID()));

    //when
    String query = getQueryWithBoundedItems(instanceOne, instanceTwo, "id==(%s or %s)");
    var instances = inventoryViewClient.getByQuery(query)
      .stream()
      .map(IndividualResource::new)
      .toList();

    //then
    assertThat(instances.size(), is(2));

    var returnedInstances = instances.stream()
      .map(resource -> resource.getJson().mapTo(InventoryViewInstance.class))
      .toList();

    for (InventoryViewInstance returnedInstance : returnedInstances) {
      isNonNullEmpty(returnedInstance.getHoldingsRecords());
      isNonNullEmpty(returnedInstance.getItems());

      assertTrue(returnedInstance.getInstanceId().equals(instanceOne.getId().toString())
                 || returnedInstance.getInstanceId().equals(instanceTwo.getId().toString()));
    }
  }

  @Test
  public void shouldReturnNotSuppressedInstanceEvenIfNoHoldings_whenWithBoundedItemsTrue() {
    //given
    var instanceOne = instancesClient.create(instance(randomUUID()));
    var instanceTwo = instancesClient.create(instance(randomUUID()));

    //when
    String query = getQueryWithBoundedItems(instanceOne, instanceTwo,
      "instance.discoverySuppress<>true and id==(%s or %s)");
    var instances = inventoryViewClient.getByQuery(query)
      .stream()
      .map(IndividualResource::new)
      .toList();

    //then
    assertThat(instances.size(), is(2));

    var returnedInstances = instances.stream()
      .map(resource -> resource.getJson().mapTo(InventoryViewInstance.class))
      .toList();

    for (InventoryViewInstance returnedInstance : returnedInstances) {
      isNonNullEmpty(returnedInstance.getHoldingsRecords());
      isNonNullEmpty(returnedInstance.getItems());

      assertTrue(returnedInstance.getInstanceId().equals(instanceOne.getId().toString())
                 || returnedInstance.getInstanceId().equals(instanceTwo.getId().toString()));
    }
  }

  private IndividualResource createInstanceWithHrid(String hrid) {
    return instancesClient.create(instance(randomUUID()).put("hrid", hrid));
  }

  @Test
  public void shouldExposeLowerCasedUnaccentedHridInViewColumn() throws Exception {
    var suffix = randomUUID().toString().substring(0, 8);
    var instanceId = createInstanceWithHrid("ÉCL-" + suffix).getId();

    var future = new CompletableFuture<String>();
    PostgresClient.getInstance(getVertx()).select(
      "SELECT instance_hrid FROM " + TENANT_ID + "_mod_inventory_storage.instance_holdings_item_view"
        + " WHERE id = '" + instanceId + "'",
      ar -> {
        if (ar.failed()) {
          future.completeExceptionally(ar.cause());
        } else {
          future.complete(ar.result().iterator().next().getString("instance_hrid"));
        }
      });

    assertThat(future.get(10, SECONDS), is("ecl-" + suffix));
  }

  @Test
  public void shouldReturnInstanceWithRecordsWhenQueriedByHrid() {
    var hridOne = uniqueHrid("in1");
    var hridTwo = uniqueHrid("in2");
    var one = createInstanceWithHrid(hridOne);
    var two = createInstanceWithHrid(hridTwo);
    var holdings = List.of(
      createHolding(two.getId(), MAIN_LIBRARY_LOCATION_ID, null),
      createHolding(two.getId(), SECOND_FLOOR_LOCATION_ID, null));
    var items = List.of(
      createItem(nodWithNoBarcode(holdings.get(0))).getString("id"),
      createItem(nodWithNoBarcode(holdings.get(1))).getString("id"));

    for (var query : List.of("instance.hrid==" + hridTwo, "instance.hrid=" + hridTwo,
      "instanceHrid==" + hridTwo, "(instance.hrid=" + hridTwo + ")",
      "instance.hrid==" + hridTwo.toUpperCase())) {
      for (var withBoundedItems : List.of(false, true)) {
        var result = inventoryViewClient.getByQuery(hridQueryString(query, withBoundedItems, ""))
          .stream().map(json -> json.mapTo(InventoryViewInstance.class)).toList();

        assertThat(query, result.size(), is(1));
        assertThat(query, result.getFirst().getInstanceId(), is(two.getId().toString()));
        assertThat(query, result.getFirst().getInstanceId(), not(is(one.getId().toString())));
        assertThat(query, getHoldingIds(result.getFirst()), matchesInAnyOrder(holdings));
        assertThat(query, getItemIds(result.getFirst()), matchesInAnyOrder(items));
      }
    }
  }

  @Test
  public void shouldReturnNothingWhenNoInstanceHasHrid() {
    createInstanceWithHrid(uniqueHrid("in1"));

    assertThat(inventoryViewClient.getByQuery(hridQueryString("instance.hrid==nope-999", false, "")).size(),
      is(0));
    assertThat(inventoryViewClient.getByQuery(hridQueryString("instance.hrid==nope-999", true, "")).size(),
      is(0));
  }

  @Test
  public void shouldIgnoreCaseAndAccentsWhenQueriedByHrid() {
    var suffix = randomUUID().toString().substring(0, 8);
    var id = createInstanceWithHrid("Éclair-" + suffix).getId();

    var result = inventoryViewClient.getByQuery(
      hridQueryString("instance.hrid==eclair-" + suffix, false, ""));

    assertThat(result.size(), is(1));
    assertThat(result.getFirst().getString("instanceId"), is(id.toString()));
  }

  @Test
  public void shouldNotMatchPartialHridWhenQueriedWithWordMatchOperator() {
    var suffix = randomUUID().toString().substring(0, 8);
    createInstanceWithHrid("ab" + suffix + " cd");

    assertThat(inventoryViewClient.getByQuery(
      hridQueryString("instance.hrid=ab" + suffix, false, "")).size(), is(0));
    assertThat(inventoryViewClient.getByQuery(
      hridQueryString("instance.hrid==\"ab" + suffix + " cd\"", false, "")).size(), is(1));
  }

  @Test
  public void shouldHonourOffsetAndLimitWhenQueriedByHrid() {
    var hrid = uniqueHrid("in1");
    createInstanceWithHrid(hrid);

    assertThat(inventoryViewClient.getByQuery(
      hridQueryString("instance.hrid==" + hrid, false, "&offset=1")).size(), is(0));
    assertThat(inventoryViewClient.getByQuery(
      hridQueryString("instance.hrid==" + hrid, false, "&limit=0")).size(), is(0));
    assertThat(inventoryViewClient.getByQuery(
      hridQueryString("instance.hrid==" + hrid, false, "&limit=1")).size(), is(1));
  }

  @Test
  public void shouldKeepGenericBehaviourWhenQueryIsNotSingleHridTerm() {
    var prefix = "gen" + randomUUID().toString().substring(0, 8);
    createInstanceWithHrid(prefix + "10");
    createInstanceWithHrid(prefix + "11");
    var other = uniqueHrid("gen-other");
    createInstanceWithHrid(other);

    assertThat(inventoryViewClient.getByQuery(
      hridQueryString("instance.hrid==" + prefix + "1*", false, "")).size(), is(2));
    assertThat(inventoryViewClient.getByQuery(hridQueryString(
      "instance.hrid==" + prefix + "10 or instance.hrid==" + other, false, "")).size(), is(2));
  }

  private static String uniqueHrid(String prefix) {
    return prefix + "-" + randomUUID().toString().substring(0, 8);
  }

  private static String hridQueryString(String cql, boolean withBoundedItems, String extraParams) {
    return "?query=" + StringUtil.urlEncode(cql) + (withBoundedItems ? "&withBoundedItems=true" : "")
      + extraParams;
  }

  private List<UUID> getHoldingIds(InventoryViewInstance instance) {
    return instance.getHoldingsRecords().stream()
      .map(HoldingsRecord::getId)
      .map(UUID::fromString)
      .toList();
  }

  private List<String> getItemIds(InventoryViewInstance instance) {
    return instance.getItems().stream()
      .map(Item::getId)
      .toList();
  }

  private <T> Matcher<Iterable<? extends T>> matchesInAnyOrder(List<T> records) {
    return containsInAnyOrder(records.stream()
      .map(Matchers::is)
      .collect(Collectors.toList()));
  }

  private void isNonNullEmpty(List<?> list) {
    assertThat(list, notNullValue());
    assertThat(list.size(), is(0));
  }

  private InventoryViewInstance getInstanceById(List<IndividualResource> instances, UUID id) {
    final var instance = instances.stream()
      .map(r -> r.getJson().mapTo(InventoryViewInstance.class))
      .filter(r -> r.getInstanceId().equals(id.toString()))
      .findFirst()
      .orElse(null);

    assertThat("Instance not found", instance, is(notNullValue()));

    return instance;
  }

  private JsonObject createBoundWithPartJson(String holdingsRecordId, String itemId) {
    JsonObject boundWithPart = new JsonObject();
    boundWithPart.put("holdingsRecordId", holdingsRecordId);
    boundWithPart.put("itemId", itemId);
    return boundWithPart;
  }

  private String getQueryWithBoundedItems(IndividualResource instanceOne,
                                          IndividualResource instanceTwo, String cqlParams) {
    var encodedCqlParams = StringUtil.urlEncode(
      String.format(cqlParams, instanceTwo.getId(), instanceOne.getId()));
    return String.format("?withBoundedItems=true&query=%s", encodedCqlParams);
  }

}
