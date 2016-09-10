package org.folio.knowledgebase.core.storage.memory

import org.folio.knowledgebase.core.domain.InstanceCollection
import org.folio.knowledgebase.core.domain.CollectionProvider

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
