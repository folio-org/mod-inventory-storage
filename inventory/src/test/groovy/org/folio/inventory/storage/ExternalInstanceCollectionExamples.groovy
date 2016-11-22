package org.folio.inventory.storage

import org.folio.inventory.storage.external.ExternalStorageModuleInstanceCollection

class ExternalInstanceCollectionExamples extends InstanceCollectionExamples {
  ExternalInstanceCollectionExamples() {
    super(ExternalStorageSuite.useVertx {
      new ExternalStorageModuleInstanceCollection(it,
        ExternalStorageSuite.storageAddress)
    })
  }
}
