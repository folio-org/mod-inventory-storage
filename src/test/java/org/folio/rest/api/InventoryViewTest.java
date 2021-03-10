package org.folio.rest.api;

import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toList;
import static org.folio.rest.api.ItemStorageTest.nod;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

import java.util.List;
import java.util.UUID;
import org.folio.rest.jaxrs.model.HoldingsItem;
import org.folio.rest.jaxrs.model.HoldingsRecords2;
import org.folio.rest.jaxrs.model.InventoryViewInstance;
import org.hamcrest.Matchers;
import org.junit.Test;

public class InventoryViewTest extends TestBaseWithInventoryUtil {
  @Test
  public void shouldReturnInstanceWithRecords() {
    var instanceOne = instancesClient.create(instance(randomUUID()));
    var holdingForOne = createHolding(instanceOne.getId(), mainLibraryLocationId, null);
    createHolding(instanceOne.getId(), secondFloorLocationId, null);

    createItem(nod(holdingForOne));
    createItem(nod(holdingForOne));

    var instanceTwo = instancesClient.create(instance(randomUUID()));
    var holdings = List.of(
      createHolding(instanceTwo.getId(), mainLibraryLocationId, null),
      createHolding(instanceTwo.getId(), secondFloorLocationId, null),
      createHolding(instanceTwo.getId(), fourthFloorLocationId, null));
    var items = List.of(
      createItem(nod(holdings.get(0))).getString("id"),
      createItem(nod(holdings.get(0))).getString("id"),
      createItem(nod(holdings.get(1))).getString("id"),
      createItem(nod(holdings.get(2))).getString("id"));

    var instances = inventoryViewClient.getMany("id==%s", instanceTwo.getId());

    assertThat(instances.size(), is(1));

    var inventoryViewInstance = instances.get(0).getJson()
      .mapTo(InventoryViewInstance.class);

    assertThat(inventoryViewInstance.getInstanceId(), is(instanceTwo.getId().toString()));
    assertThat(inventoryViewInstance.getInstance().getId(), is(instanceTwo.getId().toString()));

    var actualHoldingIds = inventoryViewInstance.getHoldingsRecords().stream()
      .map(HoldingsRecords2::getId).map(UUID::fromString).collect(toList());
    var actualItemIds = inventoryViewInstance.getItems().stream()
      .map(HoldingsItem::getId).collect(toList());

    assertThat(actualHoldingIds, containsInAnyOrder(holdings.stream().map(Matchers::is)
      .collect(toList())));
    assertThat(actualItemIds, containsInAnyOrder(items.stream().map(Matchers::is)
      .collect(toList())));
  }
}
