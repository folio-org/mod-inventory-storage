package org.folio.inventory.storage.memory

import org.folio.inventory.domain.Instance
import org.folio.inventory.domain.InstanceCollection
import org.folio.metadata.common.api.request.PagingParameters
import org.folio.metadata.common.domain.Failure
import org.folio.metadata.common.domain.Success
import org.folio.metadata.common.storage.memory.InMemoryCollection

import java.util.function.Consumer

class InMemoryInstanceCollection
  implements InstanceCollection {

  private final collection = new InMemoryCollection<Instance>()

  @Override
  void add(Instance instance,
           Consumer<Success<Instance>> resultCallback,
           Consumer<Failure> failureCallback) {

    def id = instance.id ?: UUID.randomUUID().toString()

    collection.add(instance.copyWithNewId(id), resultCallback)
  }

  @Override
  void findById(String id,
                Consumer<Success<Instance>> resultCallback,
                Consumer<Failure> failureCallback) {
    collection.findOne({ it.id == id }, resultCallback)
  }

  @Override
  void findAll(PagingParameters pagingParameters,
               Consumer<Success<List<Instance>>> resultCallback,
               Consumer<Failure> failureCallback) {

    collection.some(pagingParameters, resultCallback)
  }

  @Override
  void empty(Consumer<Success> completionCallback,
             Consumer<Failure> failureCallback) {
    collection.empty(completionCallback)
  }

  @Override
  void findByCql(String cqlQuery,
                 PagingParameters pagingParameters,
                 Consumer<Success<List<Instance>>> resultCallback,
                 Consumer<Failure> failureCallback) {

    collection.find(cqlQuery, pagingParameters, resultCallback)
  }

  @Override
  void update(Instance instance,
              Consumer<Success> completionCallback,
              Consumer<Failure> failureCallback) {

    collection.replace(instance, completionCallback)
  }

  @Override
  void delete(String id,
              Consumer<Success> completionCallback,
              Consumer<Failure> failureCallback) {
    collection.remove(id, completionCallback)
  }
}
