package catalogue.core.storage.memory

import catalogue.core.domain.CollectionProvider
import catalogue.core.domain.ItemCollection

class InMemoryCollections implements CollectionProvider {
  private final InMemoryItemCollection instanceCollection

  def InMemoryCollections() {
    instanceCollection = new InMemoryItemCollection()
  }

  @Override
  ItemCollection getItemCollection() {
    instanceCollection
  }
}
