package org.folio.inventory.storage.external

import io.vertx.groovy.core.Vertx
import org.folio.inventory.domain.CollectionProvider
import org.folio.inventory.domain.InstanceCollection
import org.folio.inventory.domain.ItemCollection

class ExternalStorageCollections implements CollectionProvider {
  private final Vertx vertx
  private final String storageAddress

  def ExternalStorageCollections(Vertx vertx, String storageAddress) {

    this.vertx = vertx
    this.storageAddress = storageAddress
  }

  @Override
  ItemCollection getItemCollection(String tenantId) {
    new ExternalStorageModuleItemCollection(vertx, storageAddress, tenantId)
  }

  @Override
  InstanceCollection getInstanceCollection(String tenantId) {
    new ExternalStorageModuleInstanceCollection(vertx, storageAddress, tenantId)
  }
}
