package org.folio.inventory.storage

import org.folio.inventory.domain.Item
import org.folio.inventory.domain.ItemCollection
import org.folio.metadata.common.WaitForAllFutures
import org.junit.Before
import org.junit.Test

import java.util.concurrent.CompletableFuture

import static org.folio.metadata.common.FutureAssistance.*

abstract class ItemCollectionExamples {
  private final ItemCollection collection

  private final Item smallAngryPlanet = new Item("Long Way to a Small Angry Planet", "036000291452")
  private final Item nod = new Item("Nod", "565578437802")
  private final Item uprooted = new Item("Uprooted", "657670342075")

  public ItemCollectionExamples(ItemCollection collection) {
    this.collection = collection
  }

  @Before
  public void before() {
    def emptied = new CompletableFuture()

    collection.empty(complete(emptied))

    waitForCompletion(emptied)
  }

  @Test
  void canBeEmptied() {
    addAllExamples()

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
    addAllExamples()

    def findFuture = new CompletableFuture<List<Item>>()

    collection.findAll(complete(findFuture))

    def allItems = getOnCompletion(findFuture)

    assert allItems.size() == 3

    assert allItems.every { it.id != null }
    assert allItems.every { it.title != null }
    assert allItems.every { it.barcode != null }

    assert allItems.any { it.title == "Long Way to a Small Angry Planet" }
    assert allItems.any { it.title == "Nod" }
    assert allItems.any { it.title == "Uprooted" }
  }

  @Test
  void itemsCanBeFoundById() {
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

    assert foundItem.title == "Long Way to a Small Angry Planet"
    assert foundItem.barcode == "036000291452"

    assert otherFoundItem.title == "Nod"
    assert otherFoundItem.barcode == "565578437802"
  }

  @Test
  void itemsCanBeFoundByByPartialName() {

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

  private void addAllExamples() {
    def allAdded = new WaitForAllFutures()

    collection.add(smallAngryPlanet, allAdded.notifyComplete())
    collection.add(nod, allAdded.notifyComplete())
    collection.add(uprooted, allAdded.notifyComplete())

    allAdded.waitForCompletion()
  }
}
