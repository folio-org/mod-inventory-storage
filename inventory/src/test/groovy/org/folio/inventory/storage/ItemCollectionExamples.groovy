package org.folio.inventory.storage


import org.folio.inventory.domain.Item
import org.folio.inventory.domain.ItemCollection
import org.folio.inventory.storage.memory.InMemoryItemCollection
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import java.util.concurrent.CompletableFuture

import static org.folio.metadata.common.FutureAssistance.*

@RunWith(value = Parameterized.class)
class ItemCollectionExamples {

  final ItemCollection collection
  private
  final Item smallAngryPlanet = new Item("Long Way to a Small Angry Planet", "036000291452")
  private final Item nod = new Item("Nod", "565578437802")
  private final Item uprooted = new Item("Uprooted", "657670342075")

  public ItemCollectionExamples(ItemCollection collection) {
    this.collection = collection
  }

  @Parameterized.Parameters(name = "{0}")
  public static Collection data() {
    [new InMemoryItemCollection()]
  }

  @Before
  public void before() {
    collection.empty()
  }

  @Test
  void canBeEmptied() {
    collection.add(smallAngryPlanet)
    collection.add(nod)
    collection.add(uprooted)

    collection.empty()

    assert collection.findAll().size() == 0
  }

  @Test
  void ItemsCanBeAdded() {
    collection.add(smallAngryPlanet)
    collection.add(nod)
    collection.add(uprooted)

    def allItems = collection.findAll()

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
    def addedItem = collection.add(smallAngryPlanet)
    def otherAddedItem = collection.add(this.nod)

    def foundSmallAngryPlant = collection.findById(addedItem.id)

    assert foundSmallAngryPlant.title == "Long Way to a Small Angry Planet"
    assert foundSmallAngryPlant.barcode == "036000291452"

    def foundNod = collection.findById(otherAddedItem.id)

    assert foundNod.title == "Nod"
    assert foundNod.barcode == "565578437802"
  }

  @Test
  void multipleItemsCanBeAddedTogether() {
    collection.add([nod, uprooted])

    def allItems = collection.findAll()

    assert allItems.size() == 2
    assert allItems.every { it.id != null }
    assert allItems.every { it.title != null }
    assert allItems.every { it.barcode != null }

    assert allItems.any { it.title == "Nod" }
    assert allItems.any { it.title == "Uprooted" }
  }

  @Test
  void itemsCanBeAddedAsynchronously() {

    def firstAddFuture = new CompletableFuture<Item>()
    def secondAddFuture = new CompletableFuture<Item>()
    def thirdAddFuture = new CompletableFuture<Item>()

    collection.add(smallAngryPlanet, complete(firstAddFuture))
    collection.add(nod, complete(secondAddFuture))
    collection.add(uprooted, complete(thirdAddFuture))

    def allAddsFuture = CompletableFuture.allOf(firstAddFuture, secondAddFuture, thirdAddFuture)

    getOnCompletion(allAddsFuture)

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
  void itemsCanBeFoundByIdAsynchronously() {
    def firstAddFuture = new CompletableFuture<Item>()
    def secondAddFuture = new CompletableFuture<Item>()

    collection.add(smallAngryPlanet, complete(firstAddFuture))
    collection.add(nod, complete(secondAddFuture))

    def addedItem = getOnCompletion(firstAddFuture)
    def otherAddedItem = getOnCompletion(secondAddFuture)

    def findAllFuture = new CompletableFuture<List<Item>>()

    collection.findAll(complete(findAllFuture))

    def allItems = getOnCompletion(findAllFuture)

    assert allItems.every { it.id != null }
    assert allItems.every { it.title != null }
    assert allItems.every { it.barcode != null }

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
  void itemsCanBeFoundByByPartialNameAsynchronously() {

    def addedSmallAngryPlanet = collection.add(smallAngryPlanet)
    collection.add(nod)
    collection.add(uprooted)

    def findFuture = new CompletableFuture<List<Item>>()

    collection.findByTitle("Small Angry", complete(findFuture))

    def findByNameResults = getOnCompletion(findFuture)

    assert findByNameResults.size() == 1
    assert findByNameResults[0].id == addedSmallAngryPlanet.id
  }
}
