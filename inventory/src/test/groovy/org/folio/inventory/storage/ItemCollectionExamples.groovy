package org.folio.inventory.storage

import org.folio.inventory.domain.Item
import org.folio.inventory.domain.ItemCollection
import org.folio.inventory.storage.external.ExternalStorageModuleItemCollection
import org.folio.inventory.storage.memory.InMemoryItemCollection
import org.folio.metadata.common.VertxAssistant
import org.folio.metadata.common.WaitForAllFutures
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import support.FakeInventoryStorageModule

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

import static org.folio.metadata.common.FutureAssistance.*

@RunWith(value = Parameterized.class)
class ItemCollectionExamples {

  private static VertxAssistant vertxAssistant;

  final ItemCollection collection

  private final Item smallAngryPlanet = new Item("Long Way to a Small Angry Planet", "036000291452")
  private final Item nod = new Item("Nod", "565578437802")
  private final Item uprooted = new Item("Uprooted", "657670342075")

  public ItemCollectionExamples(ItemCollection collection) {
    this.collection = collection
  }

  @Parameterized.Parameters(name = "{0}")
  public static Collection data() {
    vertxAssistant = new VertxAssistant()
    vertxAssistant.start()

    [
      new InMemoryItemCollection(),
      new ExternalStorageModuleItemCollection(vertxAssistant.vertx)
    ]
  }

  @BeforeClass()
  public static void beforeAll() {
    // HACK: This is nasty, because one case needs vertx,
    // we need to initialise it at the beginning
    // and parameterised doesn't have a nice cleanup mechanism
    def deployed = new CompletableFuture()

    vertxAssistant.deployGroovyVerticle(FakeInventoryStorageModule.class.name, deployed)

    deployed.get(20000, TimeUnit.MILLISECONDS)
  }

  @AfterClass()
  public static void afterAll() {
    vertxAssistant.stop()
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

    collection.empty( complete(emptied) )

    waitForCompletion(emptied)

    def findFuture = new CompletableFuture<List<Item>>()

    collection.findAll(complete(findFuture))

    def allItems = getOnCompletion(findFuture)

    assert allItems.size() == 0
  }

  @Test
  void itemsCanBeAdded() {
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
