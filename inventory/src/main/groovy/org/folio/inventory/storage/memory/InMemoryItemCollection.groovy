package org.folio.inventory.storage.memory

import org.apache.commons.lang.NotImplementedException
import org.folio.inventory.domain.Item
import org.folio.inventory.domain.ItemCollection
import org.folio.metadata.common.api.request.PagingParameters
import org.folio.metadata.common.storage.memory.InMemoryCollection

import java.util.regex.Pattern

class InMemoryItemCollection
  implements ItemCollection {

  private final collection = new InMemoryCollection<Item>()

  @Override
  void add(Item item, Closure resultCallback) {
    collection.add(item.copyWithNewId(UUID.randomUUID().toString()), resultCallback)
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
  def findByTitle(String partialTitle, Closure completionCallback) {
    return collection.find({
      Pattern.compile(
        Pattern.quote(partialTitle),
        Pattern.CASE_INSENSITIVE).matcher(it.title).find()
    }, completionCallback)
  }
}
