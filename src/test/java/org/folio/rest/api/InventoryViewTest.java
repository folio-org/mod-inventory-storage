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
    var holdingsForOne = createTwoHoldingsForInstance(instanceOne.getId());
    var itemsForOne = createItemsForHoldings(holdingsForOne);

    var instanceTwo = instancesClient.create(instance(randomUUID()));
    final var holdingsForTwo = createThreeHoldingsForInstance(instanceTwo.getId());
    final var itemsForTwo = createItemsForHoldings(holdingsForTwo);

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

  private List<UUID> createTwoHoldingsForInstance(UUID instanceId) {
    return List.of(
      createHolding(instanceId, MAIN_LIBRARY_LOCATION_ID, null),
      createHolding(instanceId, SECOND_FLOOR_LOCATION_ID, null)
    );
  }

  private List<UUID> createThreeHoldingsForInstance(UUID instanceId) {
    return List.of(
      createHolding(instanceId, MAIN_LIBRARY_LOCATION_ID, null),
      createHolding(instanceId, SECOND_FLOOR_LOCATION_ID, null),
      createHolding(instanceId, FOURTH_FLOOR_LOCATION_ID, null)
    );
  }

  private List<String> createItemsForHoldings(List<UUID> holdingIds) {
    return holdingIds.stream()
      .map(holdingId -> createItem(nodWithNoBarcode(holdingId)).getString("id"))
      .toList();
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
    var testData = createTestDataWithBoundedItems();

    //when
    var query = getQueryWithBoundedItems(testData.instanceOne(), testData.instanceTwo(), "id==(%s or %s)");
    var instances = inventoryViewClient.getByQuery(query)
      .stream()
      .map(IndividualResource::new)
      .toList();

    //then
    assertThat(instances.size(), is(2));

    var firstInstance = getInstanceById(instances, testData.instanceOne().getId());
    var secondInstance = getInstanceById(instances, testData.instanceTwo().getId());

    assertThat(getHoldingIds(firstInstance), matchesInAnyOrder(testData.holdingsForOne()));
    assertThat(getItemIds(firstInstance), matchesInAnyOrder(testData.itemsForOne()));

    assertThat(getHoldingIds(secondInstance), matchesInAnyOrder(testData.holdingsForTwo()));
    assertThat(getItemIds(secondInstance), matchesInAnyOrder(testData.itemsForTwo()));
  }

  private TestDataWithBoundedItems createTestDataWithBoundedItems() {
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

    return new TestDataWithBoundedItems(instanceOne, instanceTwo,
      holdingsForOne, holdingsForTwo, itemsForOne, itemsForTwo);
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

  private record TestDataWithBoundedItems(IndividualResource instanceOne, IndividualResource instanceTwo,
                                          List<UUID> holdingsForOne, List<UUID> holdingsForTwo,
                                          List<String> itemsForOne, List<String> itemsForTwo) {
  }
}
