package org.folio.inventory.storage.external.failure

import org.folio.inventory.domain.CollectionProvider
import org.folio.inventory.domain.Instance
import org.folio.inventory.domain.InstanceCollection
import org.folio.metadata.common.api.request.PagingParameters
import org.folio.metadata.common.domain.Failure
import org.folio.metadata.common.domain.Success
import org.junit.Test

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

import static org.junit.Assert.fail

abstract class ExternalInstanceCollectionFailureExamples {

  protected final CollectionProvider collectionProvider

  def ExternalInstanceCollectionFailureExamples(CollectionProvider collectionProvider) {
    this.collectionProvider = collectionProvider
  }

  @Test
  void serverErrorWhenCreatingAnInstanceTriggersFailureCallback() {
    def collection = createCollection()

    def failureCalled = new CompletableFuture<Failure>()

    collection.add(new Instance(UUID.randomUUID().toString(), "Nod", []),
      { Success success -> fail("Completion callback should not be called") },
      { Failure failure -> failureCalled.complete(failure) })

    def failure = failureCalled.get(1000, TimeUnit.MILLISECONDS)

    check(failure)
  }

  @Test
  void serverErrorWhenUpdatingAnInstanceTriggersFailureCallback() {
    def collection = createCollection()

    def failureCalled = new CompletableFuture<Failure>()

    collection.update(new Instance(UUID.randomUUID().toString(), "Nod", []),
      { Success success -> fail("Completion callback should not be called") },
      { Failure failure -> failureCalled.complete(failure) })

    def failure = failureCalled.get(1000, TimeUnit.MILLISECONDS)

    check(failure)
  }

  @Test
  void serverErrorWhenGettingAllInstancesTriggersFailureCallback() {
    def collection = createCollection()

    def failureCalled = new CompletableFuture<Failure>()

    collection.findAll(PagingParameters.defaults(),
      { Success success -> fail("Results callback should not be called") },
      { Failure failure -> failureCalled.complete(failure) })

    def failure = failureCalled.get(1000, TimeUnit.MILLISECONDS)

    check(failure)
  }

  @Test
  void serverErrorWhenGettingAnInstanceByIdTriggersFailureCallback() {
    def collection = createCollection()

    def failureCalled = new CompletableFuture<Failure>()

    collection.findById(UUID.randomUUID().toString(),
      { Success success -> fail("Results callback should not be called") },
      { Failure failure -> failureCalled.complete(failure) })

    def failure = failureCalled.get(1000, TimeUnit.MILLISECONDS)

    check(failure)
  }

  @Test
  void serverErrorWhenDeletingAnInstanceByIdTriggersFailureCallback() {
    def collection = createCollection()

    def failureCalled = new CompletableFuture<Failure>()

    collection.delete(UUID.randomUUID().toString(),
      { Success success -> fail("Completion callback should not be called") },
      { Failure failure -> failureCalled.complete(failure) })

    def failure = failureCalled.get(1000, TimeUnit.MILLISECONDS)

    check(failure)
  }

  @Test
  void serverErrorWhenDeletingAllInstancesTriggersFailureCallback() {
    def collection = createCollection()

    def failureCalled = new CompletableFuture<Failure>()

    collection.empty(
      { Success success -> fail("Completion callback should not be called") },
      { Failure failure -> failureCalled.complete(failure) })

    def failure = failureCalled.get(1000, TimeUnit.MILLISECONDS)

    check(failure)
  }

  @Test
  void serverErrorWhenFindingItemsTriggersFailureCallback() {
    def collection = createCollection()

    def failureCalled = new CompletableFuture<Failure>()

    collection.findByCql("title=\"*Small Angry*\"",
      new PagingParameters(10, 0),
      { Success success -> fail("Success callback should not be called") },
      { Failure failure -> failureCalled.complete(failure) })

    def failure = failureCalled.get(1000, TimeUnit.MILLISECONDS)

    check(failure)
  }

  protected abstract check(Failure failure)

  private InstanceCollection createCollection() {
    collectionProvider.getInstanceCollection("test_tenant", "")
  }

}
