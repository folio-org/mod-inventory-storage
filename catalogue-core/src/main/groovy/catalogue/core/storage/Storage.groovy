package catalogue.core.storage

import catalogue.core.domain.CollectionProvider
import catalogue.core.domain.ItemCollection
import catalogue.core.storage.memory.InMemoryCollections

class Storage {
  private static CollectionProvider collectionProvider = new InMemoryCollections()

  static void useInMemory() {
    collectionProvider = new InMemoryCollections()
  }

  static void clear() {
    collectionProvider.itemCollection.empty()
  }

  static ItemCollection getItemCollection() {
    collectionProvider.itemCollection
  }
}
