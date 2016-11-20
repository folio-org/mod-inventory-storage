package org.folio.inventory.storage

import org.folio.inventory.domain.Instance
import org.folio.inventory.domain.InstanceCollection
import org.folio.inventory.domain.Item
import org.folio.metadata.common.WaitForAllFutures
import org.junit.Before
import org.junit.Test

import java.util.concurrent.CompletableFuture

import static org.folio.metadata.common.FutureAssistance.*

abstract class InstanceCollectionExamples {
  private final InstanceCollection collection

  private final Instance smallAngryPlanet = new Instance("Long Way to a Small Angry Planet")
  private final Instance nod = new Instance("Nod")
  private final Instance uprooted = new Instance("Uprooted")

  public InstanceCollectionExamples(InstanceCollection collection) {
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

    def allInstances = getOnCompletion(findFuture)

    assert allInstances.size() == 0
  }

  @Test
  void instancesCanBeAdded() {
    addAllExamples()

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

  private void addAllExamples() {
    def allAdded = new WaitForAllFutures()

    collection.add(smallAngryPlanet, allAdded.notifyComplete())
    collection.add(nod, allAdded.notifyComplete())
    collection.add(uprooted, allAdded.notifyComplete())

    allAdded.waitForCompletion()
  }
}
