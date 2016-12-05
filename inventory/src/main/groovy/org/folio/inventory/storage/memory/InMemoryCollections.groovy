package org.folio.inventory.storage.memory

import org.folio.inventory.domain.CollectionProvider
import org.folio.inventory.domain.InstanceCollection
import org.folio.inventory.domain.ItemCollection
import org.folio.inventory.resources.ingest.IngestJobCollection

class InMemoryCollections implements CollectionProvider {
  private final Map<String, ItemCollection> itemCollections = [:]
  private final Map<String, InstanceCollection> instanceCollections = [:]

  private final InMemoryIngestJobCollection ingestJobCollection

  def InMemoryCollections() {
    ingestJobCollection = new InMemoryIngestJobCollection()
  }

  @Override
  ItemCollection getItemCollection(String tenantId) {
    getCollectionFormTenant(tenantId, itemCollections,
      { new InMemoryItemCollection() })
  }


  @Override
  InstanceCollection getInstanceCollection(String tenantId) {
    getCollectionFormTenant(tenantId, instanceCollections,
      { new InMemoryInstanceCollection() })
  }

  @Override
  IngestJobCollection getIngestJobCollection(String tenantId) {
    ingestJobCollection
  }

  private <T> T getCollectionFormTenant(
    String tenantId,
    Map<String, T> collections,
    Closure createNew) {

    def collectionForTenant = collections.get(tenantId, null)

    if (collectionForTenant == null) {
      collectionForTenant = createNew()
      collections.put(tenantId, collectionForTenant)
    }

    collectionForTenant
  }
}
