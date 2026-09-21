package org.folio.rest.api;

import static java.util.UUID.randomUUID;
import static org.folio.rest.api.ItemStorageTest.nodWithNoBarcode;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertTrue;

import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.InventoryViewInstance;
import org.folio.rest.jaxrs.model.Item;
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

  @Test
  public void shouldReturnInstanceByExactHridMatch() {
    // an unrelated instance to make sure the match is exact, not "any"
    instancesClient.create(instance(randomUUID()));

    var instanceOne = instancesClient.create(instance(randomUUID()));
    var holdingsForOne = List.of(
      createHolding(instanceOne.getId(), MAIN_LIBRARY_LOCATION_ID, null),
      createHolding(instanceOne.getId(), SECOND_FLOOR_LOCATION_ID, null)
    );
    var itemsForOne = List.of(
      createItem(nodWithNoBarcode(holdingsForOne.get(0))).getString("id"),
      createItem(nodWithNoBarcode(holdingsForOne.get(1))).getString("id")
    );

    var instances = inventoryViewClient.getMany(
      "instanceHrid==%s", instanceOne.getJson().getString("hrid"));

    assertThat(instances.size(), is(1));

    var foundInstance = instances.getFirst().getJson().mapTo(InventoryViewInstance.class);
    assertThat(getItemIds(foundInstance), matchesInAnyOrder(itemsForOne));
    assertThat(foundInstance.getInstanceId(), is(instanceOne.getId().toString()));
    assertThat(getHoldingIds(foundInstance), matchesInAnyOrder(holdingsForOne));
  }

  @Test
  public void shouldReturnEmpty_whenHridMatchesNoInstance() {
    instancesClient.create(instance(randomUUID()));

    var instances = inventoryViewClient.getMany("instanceHrid==%s", "does-not-exist");

    assertThat(instances.size(), is(0));
  }

  @Test
  public void shouldIncludeBoundedItems_whenHridMatchAndWithBoundedItemsTrue() {
    var instanceOne = instancesClient.create(instance(randomUUID()));
    var holdingsForOne = createHolding(instanceOne.getId(), MAIN_LIBRARY_LOCATION_ID, null);
    var ownItem = createItem(nodWithNoBarcode(holdingsForOne)).getString("id");

    var instanceTwo = instancesClient.create(instance(randomUUID()));
    var holdingsForTwo = createHolding(instanceTwo.getId(), MAIN_LIBRARY_LOCATION_ID, null);
    var boundItem = createItem(nodWithNoBarcode(holdingsForTwo)).getString("id");

    boundWithClient.create(createBoundWithPartJson(holdingsForOne.toString(), boundItem));

    var hrid = instanceOne.getJson().getString("hrid");
    var encodedQuery = StringUtil.urlEncode(String.format("instanceHrid==%s", hrid));
    var instances = inventoryViewClient.getByQuery("?withBoundedItems=true&query=" + encodedQuery)
      .stream()
      .map(IndividualResource::new)
      .toList();

    assertThat(instances.size(), is(1));

    var foundInstance = instances.getFirst().getJson().mapTo(InventoryViewInstance.class);
    assertThat(getItemIds(foundInstance), matchesInAnyOrder(List.of(ownItem, boundItem)));
  }

  @Test
  public void shouldNotIncludeBoundedItems_whenHridMatchAndWithBoundedItemsFalse() {
    var instanceOne = instancesClient.create(instance(randomUUID()));
    var holdingsForOne = createHolding(instanceOne.getId(), MAIN_LIBRARY_LOCATION_ID, null);
    var ownItem = createItem(nodWithNoBarcode(holdingsForOne)).getString("id");

    var instanceTwo = instancesClient.create(instance(randomUUID()));
    var holdingsForTwo = createHolding(instanceTwo.getId(), MAIN_LIBRARY_LOCATION_ID, null);
    var boundItem = createItem(nodWithNoBarcode(holdingsForTwo)).getString("id");

    boundWithClient.create(createBoundWithPartJson(holdingsForOne.toString(), boundItem));

    var hrid = instanceOne.getJson().getString("hrid");
    var instances = inventoryViewClient.getMany("instanceHrid==%s", hrid);

    assertThat(instances.size(), is(1));

    var foundInstance = instances.getFirst().getJson().mapTo(InventoryViewInstance.class);
    assertThat(getItemIds(foundInstance), matchesInAnyOrder(List.of(ownItem)));
  }

  @Test
  public void shouldReturnEmpty_whenHridCombinedWithOtherTermInCompoundQuery() {
    // Known limitation: exactMatchTerm() only recognizes a bare single-term query, so a compound
    // query combining instanceHrid with anything else falls through to the generic query path.
    // Since instanceHrid is not part of the returned JSON payload, the generic path can't find it
    // at all, so the compound query returns zero rows -- even though instanceOne genuinely matches
    // both sub-terms.
    var instanceOne = instancesClient.create(instance(randomUUID()));

    var hrid = instanceOne.getJson().getString("hrid");
    var instances = inventoryViewClient.getMany(
      "instanceHrid==%s and id==%s", hrid, instanceOne.getId());

    assertThat(instances.size(), is(0));
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
