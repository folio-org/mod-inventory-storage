package org.folio.inventory.storage

import org.folio.inventory.domain.CollectionProvider
import org.folio.inventory.domain.Item
import org.folio.inventory.domain.ItemCollection
import org.folio.metadata.common.WaitForAllFutures
import org.folio.metadata.common.api.request.PagingParameters
import org.junit.Before
import org.junit.Test

import java.util.concurrent.CompletableFuture

import static org.folio.metadata.common.FutureAssistance.*

abstract class ItemCollectionExamples {
  private static final String firstTenantId = "test_tenant_1"
  private static final String secondTenantId = "test_tenant_2"

  private final CollectionProvider collectionProvider

  private final Item smallAngryPlanet = smallAngryPlanet()
  private final Item nod = nod()
  private final Item uprooted = uprooted()
  private final Item temeraire = temeraire()
  private final Item interestingTimes = interestingTimes()

  public ItemCollectionExamples(CollectionProvider collectionProvider) {

    this.collectionProvider = collectionProvider
  }

  @Before
  public void before() {
    emptyCollection(collectionProvider.getItemCollection(firstTenantId))
    emptyCollection(collectionProvider.getItemCollection(secondTenantId))
  }

  @Test
  void canBeEmptied() {
    def collection = collectionProvider.getItemCollection(firstTenantId)

    addSomeExamples(collection)

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
    def collection = collectionProvider.getItemCollection(firstTenantId)

    addSomeExamples(collection)

    def findFuture = new CompletableFuture<List<Item>>()

    collection.findAll(complete(findFuture))

    def allItems = getOnCompletion(findFuture)

    assert allItems.size() == 3

    assert allItems.every { it.id != null }
    assert allItems.every { it.title != null }
    assert allItems.every { it.barcode != null }
    assert allItems.every { it.instanceId != null }

    def smallAngry = allItems.find({ it.title == "Long Way to a Small Angry Planet" })

    assert smallAngry != null
    assert smallAngry.barcode == "036000291452"
    assert smallAngry.status == "available"

    def nod = allItems.find({ it.title == "Nod" })

    assert nod != null
    assert nod.barcode == "565578437802"
    assert nod.status == "available"

    def uprooted = allItems.find({ it.title == "Uprooted"})

    assert uprooted != null
    assert uprooted.barcode == "657670342075"
    assert uprooted.status == "available"
  }

  @Test
  void itemsCanBeFoundByIdWithinATenant() {
    def firstTenantCollection = collectionProvider
      .getItemCollection(firstTenantId)

    def secondTenantCollection = collectionProvider
      .getItemCollection(secondTenantId)

    def addFuture = new CompletableFuture<Item>()

    firstTenantCollection.add(smallAngryPlanet, complete(addFuture))

    def addedItem = getOnCompletion(addFuture)

    def findItemForCorrectTenant = new CompletableFuture<Item>()
    def findItemForIncorrectTenant = new CompletableFuture<Item>()

    firstTenantCollection.findById(addedItem.id,
      complete(findItemForCorrectTenant))

    secondTenantCollection.findById(addedItem.id,
      complete(findItemForIncorrectTenant))

    assert getOnCompletion(findItemForCorrectTenant) != null
    assert getOnCompletion(findItemForIncorrectTenant) == null
  }

  @Test
  void allItemsCanBePaged() {
    def collection = collectionProvider.getItemCollection(firstTenantId)

    def allAdded = new WaitForAllFutures()

    collection.add(smallAngryPlanet, allAdded.notifyComplete())
    collection.add(nod, allAdded.notifyComplete())
    collection.add(uprooted, allAdded.notifyComplete())
    collection.add(temeraire, allAdded.notifyComplete())
    collection.add(interestingTimes, allAdded.notifyComplete())

    allAdded.waitForCompletion()

    def firstPageFuture = new CompletableFuture<Collection>()
    def secondPageFuture = new CompletableFuture<Collection>()

    collection.findAll(new PagingParameters(3, 0), complete(firstPageFuture))
    collection.findAll(new PagingParameters(3, 3), complete(secondPageFuture))

    def firstPage = getOnCompletion(firstPageFuture)
    def secondPage = getOnCompletion(secondPageFuture)

    assert firstPage.size() == 3
    assert secondPage.size() == 2
  }

  @Test
  void itemsCanBeFoundByByPartialName() {
    def collection = collectionProvider.getItemCollection(firstTenantId)

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

    collection.findByCql("title=\"*Small Angry*\"", new PagingParameters(10, 0),
      complete(findFuture))

    def findByNameResults = getOnCompletion(findFuture)

    assert findByNameResults.size() == 1
    assert findByNameResults[0].id == addedSmallAngryPlanet.id
  }

  @Test
  void itemsCanBeFoundById() {
    def collection = collectionProvider.getItemCollection(firstTenantId)

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

    assert foundItem.instanceId == smallAngryPlanet.instanceId
    assert foundItem.title == "Long Way to a Small Angry Planet"
    assert foundItem.barcode == "036000291452"
    assert foundItem.status == "available"

    assert otherFoundItem.title == "Nod"
    assert otherFoundItem.instanceId == nod.instanceId
    assert otherFoundItem.barcode == "565578437802"
    assert otherFoundItem.status == "available"
  }

  private void addSomeExamples(ItemCollection itemCollection) {
    def allAdded = new WaitForAllFutures()

    itemCollection.add(smallAngryPlanet, allAdded.notifyComplete())
    itemCollection.add(nod, allAdded.notifyComplete())
    itemCollection.add(uprooted, allAdded.notifyComplete())

    allAdded.waitForCompletion()
  }

  private void emptyCollection(ItemCollection collection) {
    def emptied = new CompletableFuture()

    collection.empty(complete(emptied))

    waitForCompletion(emptied)
  }

  private Item smallAngryPlanet() {
    new Item("Long Way to a Small Angry Planet", "036000291452",
      UUID.randomUUID().toString(), "available")
  }

  private Item nod() {
    new Item("Nod", "565578437802",
      UUID.randomUUID().toString(), "available")
  }

  private Item uprooted() {
    new Item("Uprooted", "657670342075",
      UUID.randomUUID().toString(), "available")
  }

  private Item temeraire() {
    new Item("Temeraire", "232142443432",
      UUID.randomUUID().toString(), "available")
  }

  private Item interestingTimes() {
    new Item("Interesting Times", "56454543534",
      UUID.randomUUID().toString(), "available")
  }
}
