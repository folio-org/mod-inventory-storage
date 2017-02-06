package org.folio.inventory.storage.memory

import org.folio.inventory.domain.Item
import org.folio.inventory.domain.ItemCollection
import org.folio.metadata.common.api.request.PagingParameters
import org.folio.metadata.common.storage.memory.InMemoryCollection

class InMemoryItemCollection
  implements ItemCollection {

  private final collection = new InMemoryCollection<Item>()

  @Override
  void add(Item item, Closure resultCallback) {
    def id = item.id ?: UUID.randomUUID().toString()

    collection.add(item.copyWithNewId(id), resultCallback)
  }

  @Override
  void findById(String id, Closure resultCallback) {
    collection.findOne({ it.id == id }, resultCallback)
  }

  @Override
  void findAll(Closure resultCallback) {
    collection.all(resultCallback)
  }

  @Override
  void findAll(PagingParameters pagingParameters, Closure resultCallback) {
    collection.some(pagingParameters, resultCallback)
  }

  @Override
  void empty(Closure completionCallback) {
    collection.empty(completionCallback)
  }

  @Override
  void findByCql(String cqlQuery, PagingParameters pagingParameters,
                 Closure resultCallback) {

    collection.find(cqlQuery, pagingParameters, resultCallback)
  }
}
