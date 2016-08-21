package knowledgebase.core.storage

import knowledgebase.core.domain.CollectionProvider
import knowledgebase.core.storage.memory.InMemoryCollections
import knowledgebase.core.storage.mongo.MongoCollections

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
