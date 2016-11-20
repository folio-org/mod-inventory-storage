package org.folio.knowledgebase.core.storage.memory

import org.folio.knowledgebase.core.domain.InstanceCollection
import org.folio.knowledgebase.core.domain.Instance
import org.folio.metadata.common.storage.memory.InMemoryCollection

import java.util.regex.Pattern

class InMemoryInstanceCollection
  implements InstanceCollection {

  private final collection = new InMemoryCollection<Instance>()

  @Override
  List<Instance> findByTitle(String partialTitle) {
    collection.find {
      Pattern.compile(
        Pattern.quote(partialTitle),
        Pattern.CASE_INSENSITIVE).matcher(it.title).find()
    }
  }

  @Override
  List<Instance> findByIdentifier(String namespace, String identifier) {
    return collection.find { instance ->
      instance.identifiers.any {
        ident -> ident.namespace == namespace && ident.value == identifier
      }
    }
  }

  @Override
  void findByTitle(String partialTitle, Closure resultCallback) {
    def results = findByTitle(partialTitle)
    resultCallback(results)
  }

  @Override
  void findByIdentifier(String namespace, String identifier, Closure resultCallback) {
    def results = findByIdentifier(namespace, identifier)
    resultCallback(results)
  }

  @Override
  List<Instance> add(List<Instance> itemsToAdd) {
    def addedInstances = itemsToAdd.collect() {
      new Instance(UUID.randomUUID().toString(), it.title, it.identifiers)
    }

    collection.add(addedInstances)
  }

  @Override
  void empty() {
    collection.empty()
  }

  @Override
  Instance add(Instance item) {
    def addedInstance = new Instance(UUID.randomUUID().toString(), item.title, item.identifiers)
    collection.add(addedInstance)
  }

  @Override
  List<Instance> findAll() {
    collection.all()
  }

  @Override
  Instance findById(String id) {
    collection.findOne { it.id == id }
  }

  @Override
  void empty(Closure completionCallback) {
    collection.empty(completionCallback)
  }

  @Override
  void add(Instance item, Closure resultCallback) {
    def addedInstance = new Instance(UUID.randomUUID().toString(), item.title, item.identifiers)
    collection.add(addedInstance, resultCallback)
  }

  @Override
  void findById(String id, Closure resultCallback) {
    collection.findOne({ it.id == id }, resultCallback)
  }

  @Override
  void findAll(Closure resultCallback) {
    collection.all(resultCallback)
  }
}
