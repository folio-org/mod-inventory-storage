package org.folio.catalogue.core.storage.memory

import org.folio.catalogue.core.domain.CollectionProvider
import org.folio.catalogue.core.domain.ItemCollection

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
