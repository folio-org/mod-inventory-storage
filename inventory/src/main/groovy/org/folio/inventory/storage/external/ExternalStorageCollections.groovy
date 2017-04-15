package org.folio.inventory.storage.external

import io.vertx.groovy.core.Vertx
import org.folio.inventory.domain.CollectionProvider
import org.folio.inventory.domain.InstanceCollection
import org.folio.inventory.domain.ItemCollection
import org.folio.inventory.domain.ingest.IngestJobCollection
import org.folio.inventory.storage.memory.InMemoryIngestJobCollection

class ExternalStorageCollections implements CollectionProvider {
  private final Vertx vertx
  private final String baseAddress

  private static final InMemoryIngestJobCollection ingestJobCollection =
    new InMemoryIngestJobCollection()

  def ExternalStorageCollections(Vertx vertx, String baseAddress) {
    this.vertx = vertx
    this.baseAddress = baseAddress
  }

  @Override
  ItemCollection getItemCollection(String tenantId, String token) {
    new ExternalStorageModuleItemCollection(vertx, baseAddress, tenantId, token)
  }

  @Override
  InstanceCollection getInstanceCollection(String tenantId, String token) {
    new ExternalStorageModuleInstanceCollection(vertx, baseAddress, tenantId, token)
  }

  @Override
  IngestJobCollection getIngestJobCollection(String tenantId, String token) {
    //There is no external storage implementation for Jobs yet
    ingestJobCollection
  }
}
