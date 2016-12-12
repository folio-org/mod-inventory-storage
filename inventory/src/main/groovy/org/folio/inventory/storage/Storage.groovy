package org.folio.inventory.storage

import org.folio.inventory.domain.CollectionProvider
import org.folio.inventory.domain.InstanceCollection
import org.folio.inventory.domain.ItemCollection
import org.folio.inventory.resources.ingest.IngestJobCollection
import org.folio.metadata.common.Context

class Storage {
  private final CollectionProvider collectionProvider

  Storage(final CollectionProvider collectionProvider) {
    this.collectionProvider = collectionProvider
  }

  ItemCollection getItemCollection(Context context) {
    collectionProvider.getItemCollection(context.tenantId)
  }

  InstanceCollection getInstanceCollection(Context context) {
    collectionProvider.getInstanceCollection(context.tenantId)
  }

  IngestJobCollection getIngestJobCollection(Context context) {
    collectionProvider.getIngestJobCollection(context.tenantId)
  }
}
