package org.folio.inventory.domain

import org.folio.inventory.domain.ingest.IngestJobCollection

interface CollectionProvider {
  ItemCollection getItemCollection(String tenantId, String token)
  InstanceCollection getInstanceCollection(String tenantId, String token)
  IngestJobCollection getIngestJobCollection(String tenantId, String token)
}
