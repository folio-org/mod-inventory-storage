package org.folio.inventory.storage.external.failure

import org.folio.inventory.domain.CollectionProvider
import org.folio.inventory.domain.Instance
import org.folio.inventory.domain.InstanceCollection
import org.folio.inventory.storage.external.ExternalStorageCollections
import org.folio.metadata.common.api.request.PagingParameters
import org.folio.metadata.common.domain.Failure
import org.folio.metadata.common.domain.Success
import org.junit.Test

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

import static junit.framework.TestCase.fail
import static org.hamcrest.CoreMatchers.is
import static org.junit.Assert.assertThat

class ExternalInstanceCollectionServerErrorExamples {

  private final CollectionProvider collectionProvider =
    ExternalStorageFailureSuite.useVertx(
      { new ExternalStorageCollections(it,
        ExternalStorageFailureSuite.instanceStorageAddress)})

  @Test
  void serverErrorWhenCreatingAnInstanceTriggersFailureCallback() {
    def collection = createCollection()

    def failureCalled = new CompletableFuture<Failure>()

    collection.add(new Instance(UUID.randomUUID().toString(), "Nod", []),
      { Success success -> fail("Completion callback should not be called") },
      { Failure failure -> failureCalled.complete(failure) })

    def failure = failureCalled.get(1000, TimeUnit.MILLISECONDS)

    assertThat(failure.reason, is("Server Error"))
  }

  @Test
  void serverErrorWhenUpdatingAnInstanceTriggersFailureCallback() {
    def collection = createCollection()

    def failureCalled = new CompletableFuture<Failure>()

    collection.update(new Instance(UUID.randomUUID().toString(), "Nod", []),
      { Success success -> fail("Completion callback should not be called") },
      { Failure failure -> failureCalled.complete(failure) })

    def failure = failureCalled.get(1000, TimeUnit.MILLISECONDS)

    assertThat(failure.reason, is("Server Error"))
  }

  @Test
  void serverErrorWhenGettingAllInstancesTriggersFailureCallback() {
    def collection = createCollection()

    def failureCalled = new CompletableFuture<Failure>()

    collection.findAll(PagingParameters.defaults(),
      { Success success -> fail("Results callback should not be called") },
      { Failure failure -> failureCalled.complete(failure) })

    def failure = failureCalled.get(1000, TimeUnit.MILLISECONDS)

    assertThat(failure.reason, is("Server Error"))
  }

  @Test
  void serverErrorWhenGettingInstanceByIdTriggersFailureCallback() {
    def collection = createCollection()

    def failureCalled = new CompletableFuture<Failure>()

    collection.findById(UUID.randomUUID().toString(),
      { Success success -> fail("Results callback should not be called") },
      { Failure failure -> failureCalled.complete(failure) })

    def failure = failureCalled.get(1000, TimeUnit.MILLISECONDS)

    assertThat(failure.reason, is("Server Error"))
  }

  private InstanceCollection createCollection() {
    collectionProvider.getInstanceCollection("test_tenant")
  }
}
