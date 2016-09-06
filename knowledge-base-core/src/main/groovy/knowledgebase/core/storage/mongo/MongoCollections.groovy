package knowledgebase.core.storage.mongo

import knowledgebase.core.domain.CollectionProvider
import knowledgebase.core.domain.InstanceCollection

class MongoCollections implements CollectionProvider {

  final String databaseName

  def MongoCollections(String databaseName) {
    this.databaseName = databaseName
  }

  @Override
  InstanceCollection getInstanceCollection() {
    new MongoInstanceCollection(databaseName)
  }
}
