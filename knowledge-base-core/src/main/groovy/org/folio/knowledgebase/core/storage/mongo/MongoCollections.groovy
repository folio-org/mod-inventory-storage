package org.folio.knowledgebase.core.storage.mongo

import org.folio.knowledgebase.core.domain.InstanceCollection
import org.folio.knowledgebase.core.domain.CollectionProvider

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
