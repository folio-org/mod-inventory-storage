package org.folio.inventory.storage

import org.folio.inventory.domain.CollectionProvider
import org.folio.inventory.domain.Instance
import org.folio.inventory.domain.InstanceCollection
import org.folio.inventory.domain.Item
import org.folio.metadata.common.WaitForAllFutures
import org.junit.Before
import org.junit.Test

import java.util.concurrent.CompletableFuture

import static org.folio.metadata.common.FutureAssistance.*

abstract class InstanceCollectionExamples {
  private static final String firstTenantId = "test-tenant-1"
  private static final String secondTenantId = "test-tenant-2"

  private final CollectionProvider collectionProvider

  private final Instance smallAngryPlanet = new Instance("Long Way to a Small Angry Planet")
  private final Instance nod = new Instance("Nod")
  private final Instance uprooted = new Instance("Uprooted")

  public InstanceCollectionExamples(CollectionProvider collectionProvider) {

    this.collectionProvider = collectionProvider
  }

  @Before
  public void before() {
    def emptied = new CompletableFuture()

    collectionProvider.getInstanceCollection(firstTenantId).empty(complete(emptied))

    waitForCompletion(emptied)
  }

  @Test
  void canBeEmptied() {
    def collection = collectionProvider.getInstanceCollection(firstTenantId)
    addAllExamples(collection)

    def emptied = new CompletableFuture()

    collection.empty(complete(emptied))

    waitForCompletion(emptied)

    def findFuture = new CompletableFuture<List<Item>>()

    collection.findAll(complete(findFuture))

    def allInstances = getOnCompletion(findFuture)

    assert allInstances.size() == 0
  }

  @Test
  void instancesCanBeAdded() {
    def collection = collectionProvider.getInstanceCollection(firstTenantId)
    addAllExamples(collection)

    def findFuture = new CompletableFuture<List<Instance>>()

    collection.findAll(complete(findFuture))

    def allInstances = getOnCompletion(findFuture)

    assert allInstances.size() == 3

    assert allInstances.every { it.id != null }
    assert allInstances.every { it.title != null }

    assert allInstances.any { it.title == "Long Way to a Small Angry Planet" }
    assert allInstances.any { it.title == "Nod" }
    assert allInstances.any { it.title == "Uprooted" }
  }

  @Test
  void instancesCanBeFoundById() {
    def collection = collectionProvider.getInstanceCollection(firstTenantId)

    def firstAddFuture = new CompletableFuture<Instance>()
    def secondAddFuture = new CompletableFuture<Instance>()

    collection.add(smallAngryPlanet, complete(firstAddFuture))
    collection.add(nod, complete(secondAddFuture))

    def addedInstance = getOnCompletion(firstAddFuture)
    def otherAddedInstance = getOnCompletion(secondAddFuture)

    def findFuture = new CompletableFuture<Instance>()
    def otherFindFuture = new CompletableFuture<Instance>()

    collection.findById(addedInstance.id, complete(findFuture))
    collection.findById(otherAddedInstance.id, complete(otherFindFuture))

    def foundInstance = getOnCompletion(findFuture)
    def otherFoundInstance = getOnCompletion(otherFindFuture)

    assert foundInstance.title == "Long Way to a Small Angry Planet"

    assert otherFoundInstance.title == "Nod"
  }

  @Test
  void instancesCanBeFoundByByPartialName() {
    def collection = collectionProvider.getInstanceCollection(firstTenantId)

    def firstAddFuture = new CompletableFuture<Instance>()
    def secondAddFuture = new CompletableFuture<Instance>()
    def thirdAddFuture = new CompletableFuture<Instance>()

    collection.add(smallAngryPlanet, complete(firstAddFuture))
    collection.add(nod, complete(secondAddFuture))
    collection.add(uprooted, complete(thirdAddFuture))

    def allAddsFuture = CompletableFuture.allOf(secondAddFuture, thirdAddFuture)

    getOnCompletion(allAddsFuture)

    def addedSmallAngryPlanet = getOnCompletion(firstAddFuture)

    def findFuture = new CompletableFuture<List<Instance>>()

    collection.findByTitle("Small Angry", complete(findFuture))

    def findByNameResults = getOnCompletion(findFuture)

    assert findByNameResults.size() == 1
    assert findByNameResults[0].id == addedSmallAngryPlanet.id
  }

  @Test
  void instancesCanBeFoundByIdWithinATenant() {
    def firstTenantCollection = collectionProvider
      .getInstanceCollection(firstTenantId)

    def secondTenantCollection = collectionProvider
      .getInstanceCollection(secondTenantId)

    def addFuture = new CompletableFuture<Item>()

    firstTenantCollection.add(smallAngryPlanet, complete(addFuture))

    def addedInstance = getOnCompletion(addFuture)

    def findInstanceForCorrectTenant = new CompletableFuture<Instance>()
    def findInstanceForIncorrectTenant = new CompletableFuture<Instance>()

    firstTenantCollection.findById(addedInstance.id,
      complete(findInstanceForCorrectTenant))

    secondTenantCollection.findById(addedInstance.id,
      complete(findInstanceForIncorrectTenant))

    assert getOnCompletion(findInstanceForCorrectTenant) != null
    assert getOnCompletion(findInstanceForIncorrectTenant) == null
  }

  private void addAllExamples(InstanceCollection instanceCollection) {
    def allAdded = new WaitForAllFutures()

    instanceCollection.add(smallAngryPlanet, allAdded.notifyComplete())
    instanceCollection.add(nod, allAdded.notifyComplete())
    instanceCollection.add(uprooted, allAdded.notifyComplete())

    allAdded.waitForCompletion()
  }
}
