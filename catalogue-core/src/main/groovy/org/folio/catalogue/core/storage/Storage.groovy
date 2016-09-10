package org.folio.catalogue.core.storage

import org.folio.catalogue.core.storage.memory.InMemoryCollections
import org.folio.catalogue.core.domain.CollectionProvider
import org.folio.catalogue.core.domain.ItemCollection

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
