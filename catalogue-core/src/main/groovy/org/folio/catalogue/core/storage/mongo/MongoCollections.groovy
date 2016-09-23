package org.folio.catalogue.core.storage.mongo

import org.folio.catalogue.core.domain.CollectionProvider
import org.folio.catalogue.core.domain.ItemCollection

class MongoCollections implements CollectionProvider {

  final String databaseName

  def MongoCollections(String databaseName) {
    this.databaseName = databaseName
  }

  @Override
  ItemCollection getItemCollection() {
    new MongoItemCollection(databaseName)
  }
}
