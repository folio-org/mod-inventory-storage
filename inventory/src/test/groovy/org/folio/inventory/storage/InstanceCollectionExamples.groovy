package org.folio.inventory.storage

import org.folio.inventory.domain.CollectionProvider
import org.folio.inventory.domain.Instance
import org.folio.inventory.domain.InstanceCollection
import org.folio.inventory.domain.Item
import org.folio.metadata.common.WaitForAllFutures
import org.folio.metadata.common.api.request.PagingParameters
import org.junit.Before
import org.junit.Test

import java.util.concurrent.CompletableFuture

import static org.folio.metadata.common.FutureAssistance.*

abstract class InstanceCollectionExamples {
  private static final String firstTenantId = "test_tenant_1"
  private static final String secondTenantId = "test_tenant_2"

  private final CollectionProvider collectionProvider

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
    addSomeExamples(collection)

    def emptied = new CompletableFuture()

    collection.empty(complete(emptied))

    waitForCompletion(emptied)

    def findFuture = new CompletableFuture<List<Item>>()

    collection.findAll(complete(findFuture))

    def allInstances = getOnCompletion(findFuture)

    assert allInstances.size() == 0
  }

  @Test
  void anInstanceCanBeAdded() {
    def collection = collectionProvider.getInstanceCollection(firstTenantId)

    addSomeExamples(collection)

    def findFuture = new CompletableFuture<List<Instance>>()

    collection.findAll(complete(findFuture))

    def allInstances = getOnCompletion(findFuture)

    assert allInstances.size() == 3

    assert allInstances.every { it.id != null }
    assert allInstances.every { it.title != null }

    assert allInstances.any { it.title == "Long Way to a Small Angry Planet" }
    assert allInstances.any { it.title == "Nod" }
    assert allInstances.any { it.title == "Uprooted" }

    def createdAngryPlanet = allInstances.find {
      it.title == "Long Way to a Small Angry Planet"
    }

    def createdNod = allInstances.find {
      it.title == "Nod"
    }

    def createdUprooted = allInstances.find {
      it.title == "Uprooted"
    }

    assert createdAngryPlanet.identifiers.any {
      it.namespace == 'isbn' && it.value == '9781473619777' }

    assert createdNod.identifiers.any {
      it.namespace == 'asin' && it.value == 'B01D1PLMDO' }

    assert createdUprooted.identifiers.any {
      it.namespace == 'isbn' && it.value == '1447294149' }

    assert createdUprooted.identifiers.any {
      it.namespace == 'isbn' && it.value == '9781447294146' }
  }

  @Test
  void anInstanceCanBeAddedWithAnId() {
    def collection = collectionProvider.getInstanceCollection(firstTenantId)

    def addFinished = new CompletableFuture<Item>()

    def instanceId = UUID.randomUUID().toString()

    def instanceWithId = smallAngryPlanet().copyWithNewId(instanceId)

    collection.add(instanceWithId, complete(addFinished))

    def added = getOnCompletion(addFinished)

    assert added.id == instanceId
  }

  @Test
  void anInstanceCanBeFoundById() {
    def collection = collectionProvider.getInstanceCollection(firstTenantId)

    def firstAddFuture = new CompletableFuture<Instance>()
    def secondAddFuture = new CompletableFuture<Instance>()

    collection.add(smallAngryPlanet(), complete(firstAddFuture))
    collection.add(nod(), complete(secondAddFuture))

    def addedInstance = getOnCompletion(firstAddFuture)
    def otherAddedInstance = getOnCompletion(secondAddFuture)

    def findFuture = new CompletableFuture<Instance>()
    def otherFindFuture = new CompletableFuture<Instance>()

    collection.findById(addedInstance.id, complete(findFuture))
    collection.findById(otherAddedInstance.id, complete(otherFindFuture))

    def foundSmallAngry = getOnCompletion(findFuture)
    def foundNod = getOnCompletion(otherFindFuture)

    assert foundSmallAngry.title == "Long Way to a Small Angry Planet"

    assert foundNod.title == "Nod"

    assert foundSmallAngry.identifiers.any {
      it.namespace == 'isbn' && it.value == '9781473619777' }

    assert foundNod.identifiers.any {
      it.namespace == 'asin' && it.value == 'B01D1PLMDO' }
  }

  @Test
  void allInstancesCanBePaged() {
    def collection = collectionProvider.getInstanceCollection(firstTenantId)

    def allAdded = new WaitForAllFutures()

    collection.add(smallAngryPlanet(), allAdded.notifyComplete())
    collection.add(nod(), allAdded.notifyComplete())
    collection.add(uprooted(), allAdded.notifyComplete())
    collection.add(temeraire(), allAdded.notifyComplete())
    collection.add(interestingTimes(), allAdded.notifyComplete())

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
  void anInstanceCanBeDeleted() {
    def collection = collectionProvider.getInstanceCollection(firstTenantId)

    addSomeExamples(collection)

    def instanceToBeDeletedFuture = new CompletableFuture<Instance>()

    collection.add(temeraire(), complete(instanceToBeDeletedFuture))

    def instanceToBeDeleted = instanceToBeDeletedFuture.get()

    def deleted = new CompletableFuture()

    collection.delete(instanceToBeDeleted.id, complete(deleted))

    waitForCompletion(deleted)

    def findFuture = new CompletableFuture<Item>()

    collection.findById(instanceToBeDeleted.id, complete(findFuture))

    assert findFuture.get() == null

    def findAllFuture = new CompletableFuture<List<Item>>()

    collection.findAll(complete(findAllFuture))

    def allInstances = getOnCompletion(findAllFuture)

    assert allInstances.size() == 3
  }

  @Test
  void anInstanceCanBeUpdated() {
    def collection = collectionProvider.getInstanceCollection(firstTenantId)

    def addFinished = new CompletableFuture<Instance>()

    collection.add(smallAngryPlanet(), complete(addFinished))

    def added = getOnCompletion(addFinished)

    def updateFinished = new CompletableFuture<Instance>()

    def changed = added.removeIdentifier('isbn', '9781473619777')

    collection.update(changed, complete(updateFinished),
      { println("Update instance failed: ${it}") })

    waitForCompletion(updateFinished)

    def gotUpdated = new CompletableFuture<Item>()

    collection.findById(added.id, complete(gotUpdated))

    def updated = getOnCompletion(gotUpdated)

    assert updated.id == added.id
    assert updated.title == added.title
    assert updated.identifiers.size() == 0
  }

  @Test
  void instancesCanBeFoundByByPartialName() {
    def collection = collectionProvider.getInstanceCollection(firstTenantId)

    def firstAddFuture = new CompletableFuture<Instance>()
    def secondAddFuture = new CompletableFuture<Instance>()
    def thirdAddFuture = new CompletableFuture<Instance>()

    collection.add(smallAngryPlanet(), complete(firstAddFuture))
    collection.add(nod(), complete(secondAddFuture))
    collection.add(uprooted(), complete(thirdAddFuture))

    def allAddsFuture = CompletableFuture.allOf(secondAddFuture, thirdAddFuture)

    getOnCompletion(allAddsFuture)

    def addedSmallAngryPlanet = getOnCompletion(firstAddFuture)

    def findFuture = new CompletableFuture<List<Instance>>()

    collection.findByCql("title=\"*Small Angry*\"",
      new PagingParameters(10, 0), complete(findFuture))

    def findByNameResults = getOnCompletion(findFuture)

    assert findByNameResults.size() == 1
    assert findByNameResults[0].id == addedSmallAngryPlanet.id
  }

  @Test
  void anInstanceCanBeFoundByIdWithinATenant() {
    def firstTenantCollection = collectionProvider
      .getInstanceCollection(firstTenantId)

    def secondTenantCollection = collectionProvider
      .getInstanceCollection(secondTenantId)

    def addFuture = new CompletableFuture<Item>()

    firstTenantCollection.add(smallAngryPlanet(), complete(addFuture))

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

  private void addSomeExamples(InstanceCollection instanceCollection) {
    def allAdded = new WaitForAllFutures()

    instanceCollection.add(smallAngryPlanet(), allAdded.notifyComplete())
    instanceCollection.add(nod(), allAdded.notifyComplete())
    instanceCollection.add(uprooted(), allAdded.notifyComplete())

    allAdded.waitForCompletion()
  }

  private Instance nod() {
    new Instance("Nod")
      .addIdentifier('asin', 'B01D1PLMDO')
  }

  private Instance uprooted() {
    new Instance("Uprooted")
      .addIdentifier('isbn', '1447294149')
      .addIdentifier('isbn', '9781447294146')
  }

  private Instance smallAngryPlanet() {
    new Instance("Long Way to a Small Angry Planet")
      .addIdentifier('isbn', '9781473619777')
  }

  private Instance temeraire() {
    new Instance("Temeraire")
      .addIdentifier('isbn', '0007258712')
      .addIdentifier('isbn', '9780007258710')
  }

  private Instance interestingTimes() {
    new Instance("Interesting Times")
      .addIdentifier('isbn', '0552167541')
      .addIdentifier('isbn', '9780552167543')
  }
}
