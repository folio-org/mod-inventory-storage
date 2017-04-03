package org.folio.inventory.storage.memory

import org.folio.inventory.domain.CollectionProvider
import org.folio.inventory.domain.InstanceCollection
import org.folio.inventory.domain.ItemCollection
import org.folio.inventory.domain.ingest.IngestJobCollection

class InMemoryCollections implements CollectionProvider {
  private final Map<String, ItemCollection> itemCollections = [:]
  private final Map<String, InstanceCollection> instanceCollections = [:]
  private final Map<String, IngestJobCollection> ingestJobCollections = [:]

  @Override
  ItemCollection getItemCollection(String tenantId, String token) {
    getCollectionForTenant(tenantId, itemCollections,
      { new InMemoryItemCollection() })
  }

  @Override
  InstanceCollection getInstanceCollection(String tenantId, String token) {
    getCollectionForTenant(tenantId, instanceCollections,
      { new InMemoryInstanceCollection() })
  }

  @Override
  IngestJobCollection getIngestJobCollection(String tenantId, String token) {
    getCollectionForTenant(tenantId, ingestJobCollections,
      { new InMemoryIngestJobCollection() })
  }

  private <T> T getCollectionForTenant(
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
