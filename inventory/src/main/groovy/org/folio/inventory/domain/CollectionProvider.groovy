package org.folio.inventory.domain

import org.folio.inventory.domain.ingest.IngestJobCollection

interface CollectionProvider {
  ItemCollection getItemCollection(String tenantId)
  InstanceCollection getInstanceCollection(String tenantId)
  IngestJobCollection getIngestJobCollection(String tenantId)
}
