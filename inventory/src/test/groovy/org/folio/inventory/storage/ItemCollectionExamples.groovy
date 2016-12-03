package org.folio.inventory.storage

import org.folio.inventory.domain.CollectionProvider
import org.folio.inventory.domain.Item
import org.folio.inventory.domain.ItemCollection
import org.folio.metadata.common.WaitForAllFutures
import org.junit.Before
import org.junit.Test

import java.util.concurrent.CompletableFuture

import static org.folio.metadata.common.FutureAssistance.*

abstract class ItemCollectionExamples {
  private static final String tenantId = "test-tenant-1"

  private final CollectionProvider collectionProvider

  private final Item smallAngryPlanet =
    new Item("Long Way to a Small Angry Planet", "036000291452",
      UUID.randomUUID().toString())

  private final Item nod = new Item("Nod", "565578437802",
      UUID.randomUUID().toString())

  private final Item uprooted = new Item("Uprooted", "657670342075",
      UUID.randomUUID().toString())

  public ItemCollectionExamples(CollectionProvider collectionProvider) {

    this.collectionProvider = collectionProvider
  }

  @Before
  public void before() {
    def collection = collectionProvider.getItemCollection(tenantId)

    def emptied = new CompletableFuture()

    collection.empty(complete(emptied))

    waitForCompletion(emptied)
  }

  @Test
  void canBeEmptied() {
    def collection = collectionProvider.getItemCollection(tenantId)

    addAllExamples(collection)

    def emptied = new CompletableFuture()

    collection.empty(complete(emptied))

    waitForCompletion(emptied)

    def findFuture = new CompletableFuture<List<Item>>()

    collection.findAll(complete(findFuture))

    def allItems = getOnCompletion(findFuture)

    assert allItems.size() == 0
  }

  @Test
  void itemsCanBeAdded() {
    def collection = collectionProvider.getItemCollection(tenantId)

    addAllExamples(collection)

    def findFuture = new CompletableFuture<List<Item>>()

    collection.findAll(complete(findFuture))

    def allItems = getOnCompletion(findFuture)

    assert allItems.size() == 3

    assert allItems.every { it.id != null }
    assert allItems.every { it.title != null }
    assert allItems.every { it.barcode != null }
    assert allItems.every { it.instanceId != null }

    assert allItems.any { it.title == "Long Way to a Small Angry Planet" &&
      it.barcode == "036000291452" }

    assert allItems.any { it.title == "Nod" &&
      it.barcode == "565578437802" }

    assert allItems.any { it.title == "Uprooted" &&
      it.barcode == "657670342075" }
  }

  @Test
  void itemsCanBeFoundById() {
    def collection = collectionProvider.getItemCollection(tenantId)

    def firstAddFuture = new CompletableFuture<Item>()
    def secondAddFuture = new CompletableFuture<Item>()

    collection.add(smallAngryPlanet, complete(firstAddFuture))
    collection.add(nod, complete(secondAddFuture))

    def addedItem = getOnCompletion(firstAddFuture)
    def otherAddedItem = getOnCompletion(secondAddFuture)

    def findFuture = new CompletableFuture<Item>()
    def otherFindFuture = new CompletableFuture<Item>()

    collection.findById(addedItem.id, complete(findFuture))
    collection.findById(otherAddedItem.id, complete(otherFindFuture))

    def foundItem = getOnCompletion(findFuture)
    def otherFoundItem = getOnCompletion(otherFindFuture)

    assert foundItem.title == smallAngryPlanet.title
    assert foundItem.barcode == smallAngryPlanet.barcode
    assert foundItem.instanceId == smallAngryPlanet.instanceId

    assert otherFoundItem.title == nod.title
    assert otherFoundItem.barcode == nod.barcode
    assert otherFoundItem.instanceId == nod.instanceId
  }

  @Test
  void itemsCanBeFoundByByPartialName() {
    def collection = collectionProvider.getItemCollection(tenantId)

    def firstAddFuture = new CompletableFuture<Item>()
    def secondAddFuture = new CompletableFuture<Item>()
    def thirdAddFuture = new CompletableFuture<Item>()

    collection.add(smallAngryPlanet, complete(firstAddFuture))
    collection.add(nod, complete(secondAddFuture))
    collection.add(uprooted, complete(thirdAddFuture))

    def allAddsFuture = CompletableFuture.allOf(secondAddFuture, thirdAddFuture)

    getOnCompletion(allAddsFuture)

    def addedSmallAngryPlanet = getOnCompletion(firstAddFuture)

    def findFuture = new CompletableFuture<List<Item>>()

    collection.findByTitle("Small Angry", complete(findFuture))

    def findByNameResults = getOnCompletion(findFuture)

    assert findByNameResults.size() == 1
    assert findByNameResults[0].id == addedSmallAngryPlanet.id
  }

  private void addAllExamples(ItemCollection itemCollection) {
    def allAdded = new WaitForAllFutures()

    itemCollection.add(smallAngryPlanet, allAdded.notifyComplete())
    itemCollection.add(nod, allAdded.notifyComplete())
    itemCollection.add(uprooted, allAdded.notifyComplete())

    allAdded.waitForCompletion()
  }
}
