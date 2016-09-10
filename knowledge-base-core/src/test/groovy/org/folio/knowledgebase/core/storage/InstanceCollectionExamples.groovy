package org.folio.knowledgebase.core.storage

import org.folio.knowledgebase.core.domain.Instance
import org.folio.knowledgebase.core.domain.InstanceCollection
import org.folio.knowledgebase.core.storage.memory.InMemoryInstanceCollection
import org.folio.knowledgebase.core.storage.mongo.MongoInstanceCollection
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeoutException

import static support.World.getOnCompletion
import static support.World.complete

@RunWith(value = Parameterized.class)
class InstanceCollectionExamples {

  final InstanceCollection collection

  public InstanceCollectionExamples(InstanceCollection collection) {
    this.collection = collection
  }

  @Parameterized.Parameters(name = "{0}")
  public static Collection data() {
    [new MongoInstanceCollection("knowledgeBaseCoreTest"), new InMemoryInstanceCollection()]
  }

  @Before
  public void before() {
    try {
      collection.empty()
    }
    catch (TimeoutException ex) {
      println "Timeout occurred whilst preparing the collection."
      println "For out of process persistence mechanisms, this could mean they aren't running or have failed."
      println "Exception details: ${ex.toString()}"
    }
  }

  @Test
  void canBeEmptied() {
    collection.add(new Instance("Long Way to a Small Angry Planet"))
    collection.add(new Instance("Nod"))
    collection.add(new Instance("Uprooted"))

    collection.empty()

    assert collection.findAll().size() == 0
  }

  @Test
  void InstancesCanBeAdded() {
    collection.add(new Instance("Long Way to a Small Angry Planet"))
    collection.add(new Instance("Nod"))
    collection.add(new Instance("Uprooted"))

    def allInstances = collection.findAll()

    assert allInstances.size() == 3

    assert collection.findAll().every { it.id != null }

    assert allInstances.any { it.title == "Long Way to a Small Angry Planet" }
    assert allInstances.any { it.title == "Nod" }
    assert allInstances.any { it.title == "Uprooted" }
  }

  @Test
  void instancesCanBeFoundById() {
    def addedItem = collection.add(new Instance("Long Way to a Small Angry Planet"))
    def otherAddedItem = collection.add(new Instance("Nod"))

    assert collection.findById(addedItem.id).title == "Long Way to a Small Angry Planet"
    assert collection.findById(otherAddedItem.id).title == "Nod"
  }

  @Test
  void multipleInstancesCanBeAddedTogether() {
    collection.add([new Instance("Nod"), new Instance("Uprooted")])

    def allResources = collection.findAll()

    assert allResources.size() == 2
    assert allResources.every { it.id != null }

    assert allResources.any { it.title == "Nod" }
    assert allResources.any { it.title == "Uprooted" }
  }

  @Test
  void resourcesCanBeFoundByAnIdentifierWithinANamespace() {
    def firstInstance = new Instance("A Long Way to a Small Angry Planet")
      .addIdentifier('isbn', '9781473619777')

    def secondInstance = new Instance("Nod")
      .addIdentifier('asin', 'B01D1PLMDO')

    collection.add(firstInstance)
    collection.add(secondInstance)

    assert collection.findByIdentifier('isbn', '9781473619777').size() == 1
    assert collection.findByIdentifier('asin', 'B01D1PLMDO').size() == 1
    assert collection.findByIdentifier('asin', '9781473619777').size() == 0
    assert collection.findByIdentifier('isbn', 'B01D1PLMDO').size() == 0
  }

  @Test
  void resourcesCanBeFoundByByPartialName() {
    def resource = new Instance("A Long Way to a Small Angry Planet")
      .addIdentifier('isbn', '9781473619777')

    resource = collection.add(resource)

    def findByNameResults = collection.findByPartialTitle("Small Angry")

    assert findByNameResults.size() == 1
    assert findByNameResults[0].id == resource.id
  }

  @Test
  void resourcesCanBeAddedAsynchronously() {

    def firstAddFuture = new CompletableFuture<Instance>()
    def secondAddFuture = new CompletableFuture<Instance>()
    def thirdAddFuture = new CompletableFuture<Instance>()

    collection.add(new Instance("Long Way to a Small Angry Planet"), complete(firstAddFuture))
    collection.add(new Instance("Nod"), complete(secondAddFuture))
    collection.add(new Instance("Uprooted"), complete(thirdAddFuture))

    def allAddsFuture = CompletableFuture.allOf(firstAddFuture, secondAddFuture, thirdAddFuture)

    getOnCompletion(allAddsFuture)

    def findFuture = new CompletableFuture<List<Instance>>()

    collection.findAll(complete(findFuture))

    def allResources = getOnCompletion(findFuture)

    assert allResources.size() == 3

    assert collection.findAll().every { it.id != null }

    assert allResources.any { it.title == "Long Way to a Small Angry Planet" }
    assert allResources.any { it.title == "Nod" }
    assert allResources.any { it.title == "Uprooted" }
  }

  @Test
  void resourcesCanBeFoundByIdAsynchronously() {
    def firstAddFuture = new CompletableFuture<Instance>()
    def secondAddFuture = new CompletableFuture<Instance>()

    collection.add(new Instance("Long Way to a Small Angry Planet"), complete(firstAddFuture))
    collection.add(new Instance("Nod"), complete(secondAddFuture))

    def addedInstance = getOnCompletion(firstAddFuture)
    def otherAddedInstance = getOnCompletion(secondAddFuture)

    def findAllFuture = new CompletableFuture<List<Instance>>()

    collection.findAll(complete(findAllFuture))

    def allResources = getOnCompletion(findAllFuture)

    assert allResources.every { it.id != null }

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
  void resourcesCanBeFoundByAnIdentifierWithinANamespaceAsynchronously() {
    def firstInstance = new Instance("A Long Way to a Small Angry Planet")
      .addIdentifier('isbn', '9781473619777')

    def secondInstance = new Instance("Nod")
      .addIdentifier('asin', 'B01D1PLMDO')

    def firstAddFuture = new CompletableFuture<Instance>()
    def secondAddFuture = new CompletableFuture<Instance>()

    collection.add(firstInstance, complete(firstAddFuture))
    collection.add(secondInstance, complete(secondAddFuture))

    def allAddsFuture = CompletableFuture.allOf(firstAddFuture, secondAddFuture)

    getOnCompletion(allAddsFuture)

    assertFindByIdentifierAsynchronously('isbn', '9781473619777', 1)
    assertFindByIdentifierAsynchronously('asin', 'B01D1PLMDO', 1)
    assertFindByIdentifierAsynchronously('asin', '9781473619777', 0)
    assertFindByIdentifierAsynchronously('isbn', 'B01D1PLMDO', 0)
  }

  @Test
  void resourcesCanBeFoundByByPartialNameAsynchronously() {
    def firstInstance = new Instance("A Long Way to a Small Angry Planet")
      .addIdentifier('isbn', '9781473619777')

    def addFuture = new CompletableFuture<Instance>()

    collection.add(firstInstance, complete(addFuture))

    firstInstance = getOnCompletion(addFuture)

    def findFuture = new CompletableFuture<List<Instance>>()

    collection.findByPartialTitle("Small Angry", complete(findFuture))

    def findByNameResults = getOnCompletion(findFuture)

    assert findByNameResults.size() == 1
    assert findByNameResults[0].id == firstInstance.id
  }

  private void assertFindByIdentifierAsynchronously(
    String namespace,
    String value,
    Integer expectedNumberOfMatches) {
    def findFuture = new CompletableFuture<List<Instance>>()

    collection.findByIdentifier(namespace, value, complete(findFuture))

    def results = getOnCompletion(findFuture)

    assert results.size() == expectedNumberOfMatches
  }
}
