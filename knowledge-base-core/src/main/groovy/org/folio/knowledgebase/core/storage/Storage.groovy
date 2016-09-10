package org.folio.knowledgebase.core.storage

import org.folio.knowledgebase.core.storage.mongo.MongoCollections
import org.folio.knowledgebase.core.domain.CollectionProvider
import org.folio.knowledgebase.core.storage.memory.InMemoryCollections

class Storage {
  private static collectionProvider = new InMemoryCollections()

  static CollectionProvider getCollectionProvider() {
    collectionProvider
  }

  static void useMongoDb(String databaseName) {
    collectionProvider = new MongoCollections(databaseName)
  }

  static void useInMemory() {
    collectionProvider = new InMemoryCollections()
  }

  static void clear() {
    collectionProvider.instanceCollection.empty()
  }
}
