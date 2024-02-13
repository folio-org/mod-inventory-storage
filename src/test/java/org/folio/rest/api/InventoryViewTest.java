package org.folio.rest.api;

import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toList;
import static org.folio.rest.api.ItemStorageTest.nodWithNoBarcode;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.InventoryViewInstance;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.support.IndividualResource;
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

    assertThat(firstInstance.getHoldingsRecords().get(0).getId(), is(holdingForOne.toString()));
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

  private List<UUID> getHoldingIds(InventoryViewInstance instance) {
    return instance.getHoldingsRecords().stream()
      .map(HoldingsRecord::getId)
      .map(UUID::fromString)
      .collect(toList());
  }

  private List<String> getItemIds(InventoryViewInstance instance) {
    return instance.getItems().stream()
      .map(Item::getId)
      .collect(toList());
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
}
