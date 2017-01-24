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
  void findByCql(String cqlQuery, PagingParameters pagingParameters,
                 Closure resultCallback) {

    def searchTerm = cqlQuery == null ? null :
      cqlQuery.replace("title=", "").replaceAll("\"", "").replaceAll("\\*", "")

    def filteredItems = collection.all().stream()
      .filter(filterByTitle(searchTerm))
      .collect()

    def pagedItems = filteredItems.stream()
      .skip(pagingParameters.offset)
      .limit(pagingParameters.limit)
      .collect()

    resultCallback(pagedItems)
  }

  private Closure filterByTitle(searchTerm) {
    return {
      if (searchTerm == null) {
        true
      } else {
        it.title.contains(searchTerm)
      }
    }
  }
}
