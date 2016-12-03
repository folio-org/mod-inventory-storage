package org.folio.inventory.storage.memory

import org.folio.inventory.domain.CollectionProvider
import org.folio.inventory.domain.InstanceCollection
import org.folio.inventory.domain.ItemCollection

class InMemoryCollections implements CollectionProvider {
  private final InMemoryItemCollection itemCollection
  private final InMemoryInstanceCollection instanceCollection

  def InMemoryCollections() {
    itemCollection = new InMemoryItemCollection()
    instanceCollection = new InMemoryInstanceCollection()
  }

  @Override
  ItemCollection getItemCollection(String tenantId) {
    itemCollection
  }

  @Override
  InstanceCollection getInstanceCollection(String tenantId) {
    instanceCollection
  }
}
