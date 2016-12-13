package org.folio.inventory.storage.memory

import org.folio.inventory.domain.Instance
import org.folio.inventory.domain.InstanceCollection
import org.folio.metadata.common.storage.memory.InMemoryCollection

import java.util.regex.Pattern

class InMemoryInstanceCollection
  implements InstanceCollection {

  private final collection = new InMemoryCollection<Instance>()

  @Override
  void add(Instance item, Closure resultCallback) {
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
