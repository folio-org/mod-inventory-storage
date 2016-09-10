package org.folio.catalogue.core.storage

import org.folio.catalogue.core.domain.Item
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import org.folio.catalogue.core.domain.ItemCollection
import org.folio.catalogue.core.storage.memory.InMemoryItemCollection
import support.World

import java.util.concurrent.CompletableFuture

@RunWith(value = Parameterized.class)
class ItemCollectionExamples {

  final ItemCollection collection
  private
  final Item smallAngryPlanet = new Item("Long Way to a Small Angry Planet", "http://books.com/small-angry", "036000291452")
  private final Item nod = new Item("Nod", "http://books.com/nod", "565578437802")
  private final Item uprooted = new Item("Uprooted", "http://books.com/uprooted", "657670342075")

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
    assert allItems.every { it.instanceLocation != null }
    assert allItems.every { it.barcode != null }

    assert allItems.any { it.title == "Long Way to a Small Angry Planet" }
    assert allItems.any { it.title == "Nod" }
    assert allItems.any { it.title == "Uprooted" }
  }

  @Test
  void instancesCanBeFoundById() {
    def addedItem = collection.add(smallAngryPlanet)
    def otherAddedItem = collection.add(this.nod)

    def foundSmallAngryPlant = collection.findById(addedItem.id)

    assert foundSmallAngryPlant.title == "Long Way to a Small Angry Planet"
    assert foundSmallAngryPlant.instanceLocation == "http://books.com/small-angry"
    assert foundSmallAngryPlant.barcode == "036000291452"

    def foundNod = collection.findById(otherAddedItem.id)

    assert foundNod.title == "Nod"
    assert foundNod.instanceLocation == "http://books.com/nod"
    assert foundNod.barcode == "565578437802"
  }

  @Test
  void multipleItemsCanBeAddedTogether() {
    collection.add([nod, uprooted])

    def allItems = collection.findAll()

    assert allItems.size() == 2
    assert allItems.every { it.id != null }
    assert allItems.every { it.title != null }
    assert allItems.every { it.instanceLocation != null }
    assert allItems.every { it.barcode != null }

    assert allItems.any { it.title == "Nod" }
    assert allItems.any { it.title == "Uprooted" }
  }

  @Test
  void resourcesCanBeAddedAsynchronously() {

    def firstAddFuture = new CompletableFuture<Item>()
    def secondAddFuture = new CompletableFuture<Item>()
    def thirdAddFuture = new CompletableFuture<Item>()

    collection.add(smallAngryPlanet, World.complete(firstAddFuture))
    collection.add(nod, World.complete(secondAddFuture))
    collection.add(uprooted, World.complete(thirdAddFuture))

    def allAddsFuture = CompletableFuture.allOf(firstAddFuture, secondAddFuture, thirdAddFuture)

    World.getOnCompletion(allAddsFuture)

    def findFuture = new CompletableFuture<List<Item>>()

    collection.findAll(World.complete(findFuture))

    def allItems = World.getOnCompletion(findFuture)

    assert allItems.size() == 3

    assert allItems.every { it.id != null }
    assert allItems.every { it.title != null }
    assert allItems.every { it.instanceLocation != null }
    assert allItems.every { it.barcode != null }

    assert allItems.any { it.title == "Long Way to a Small Angry Planet" }
    assert allItems.any { it.title == "Nod" }
    assert allItems.any { it.title == "Uprooted" }
  }

  @Test
  void resourcesCanBeFoundByIdAsynchronously() {
    def firstAddFuture = new CompletableFuture<Item>()
    def secondAddFuture = new CompletableFuture<Item>()

    collection.add(smallAngryPlanet, World.complete(firstAddFuture))
    collection.add(nod, World.complete(secondAddFuture))

    def addedItem = World.getOnCompletion(firstAddFuture)
    def otherAddedItem = World.getOnCompletion(secondAddFuture)

    def findAllFuture = new CompletableFuture<List<Item>>()

    collection.findAll(World.complete(findAllFuture))

    def allItems = World.getOnCompletion(findAllFuture)

    assert allItems.every { it.id != null }
    assert allItems.every { it.title != null }
    assert allItems.every { it.instanceLocation != null }
    assert allItems.every { it.barcode != null }

    def findFuture = new CompletableFuture<Item>()
    def otherFindFuture = new CompletableFuture<Item>()

    collection.findById(addedItem.id, World.complete(findFuture))
    collection.findById(otherAddedItem.id, World.complete(otherFindFuture))

    def foundItem = World.getOnCompletion(findFuture)
    def otherFoundItem = World.getOnCompletion(otherFindFuture)

    assert foundItem.title == "Long Way to a Small Angry Planet"
    assert foundItem.instanceLocation == "http://books.com/small-angry"
    assert foundItem.barcode == "036000291452"

    assert otherFoundItem.title == "Nod"
    assert otherFoundItem.instanceLocation == "http://books.com/nod"
    assert otherFoundItem.barcode == "565578437802"
  }
}
