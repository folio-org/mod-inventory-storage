package org.folio.inventory.storage

import io.vertx.groovy.core.Vertx
import org.folio.inventory.domain.CollectionProvider
import org.folio.inventory.domain.InstanceCollection
import org.folio.inventory.domain.ItemCollection
import org.folio.inventory.resources.ingest.IngestJobCollection
import org.folio.inventory.storage.external.ExternalStorageCollections
import org.folio.inventory.storage.memory.InMemoryCollections
import org.folio.metadata.common.Context

class Storage {
  private final CollectionProvider collectionProvider

  //HACK: Need access to vertx in order to create a HttpClient, maybe move this
  // to the context??
  static Storage basedUpon(Vertx vertx, Map<String, Object> config) {

    def storageType = config.get("storage.type", "memory")

    switch(storageType) {
      case "external":
        def location = config.get("storage.location", null)

        if(location == null) {
          throw new IllegalArgumentException(
            "For external storage, location must be provided.")
        }

        return new Storage(new ExternalStorageCollections(vertx, location))
        break;

      case "okapi":


      case "memory":
      default:
        return new Storage(new InMemoryCollections())
        break;
    }
  }

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
