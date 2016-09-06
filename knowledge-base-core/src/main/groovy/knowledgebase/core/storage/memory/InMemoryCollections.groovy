package knowledgebase.core.storage.memory

import knowledgebase.core.domain.CollectionProvider
import knowledgebase.core.domain.InstanceCollection

class InMemoryCollections implements CollectionProvider {
  private final InMemoryInstanceCollection instanceCollection

  def InMemoryCollections() {
    instanceCollection = new InMemoryInstanceCollection()
  }

  @Override
  InstanceCollection getInstanceCollection() {
    instanceCollection
  }
}
