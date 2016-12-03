package org.folio.inventory.storage.memory

import org.folio.inventory.domain.CollectionProvider
import org.folio.inventory.domain.InstanceCollection
import org.folio.inventory.domain.ItemCollection
import org.folio.inventory.resources.ingest.IngestJobCollection

class InMemoryCollections implements CollectionProvider {
  private final InMemoryItemCollection itemCollection
  private final InMemoryInstanceCollection instanceCollection
  private final InMemoryIngestJobCollection ingestJobCollection

  def InMemoryCollections() {
    itemCollection = new InMemoryItemCollection()
    instanceCollection = new InMemoryInstanceCollection()
    ingestJobCollection = new InMemoryIngestJobCollection()
  }

  @Override
  ItemCollection getItemCollection(String tenantId) {
    itemCollection
  }

  @Override
  InstanceCollection getInstanceCollection(String tenantId) {
    instanceCollection
  }

  @Override
  IngestJobCollection getIngestJobCollection(String tenantId) {
    ingestJobCollection
  }
}
