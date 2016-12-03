package org.folio.inventory.storage.memory

import org.folio.inventory.domain.CollectionProvider
import org.folio.inventory.domain.InstanceCollection
import org.folio.inventory.domain.ItemCollection
import org.folio.inventory.resources.ingest.IngestJobCollection

class InMemoryCollections implements CollectionProvider {
  private final Map<String, ItemCollection> itemCollections = [:]

  private final InMemoryInstanceCollection instanceCollection
  private final InMemoryIngestJobCollection ingestJobCollection

  def InMemoryCollections() {
    instanceCollection = new InMemoryInstanceCollection()
    ingestJobCollection = new InMemoryIngestJobCollection()
  }

  @Override
  ItemCollection getItemCollection(String tenantId) {
    def itemCollectionForTenant = itemCollections.get(tenantId, null)

    if(itemCollectionForTenant == null) {
      itemCollectionForTenant = new InMemoryItemCollection()
      itemCollections.put(tenantId, itemCollectionForTenant)
    }

    itemCollectionForTenant
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
