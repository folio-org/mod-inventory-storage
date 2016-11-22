package org.folio.inventory.storage.external

import org.folio.inventory.storage.InstanceCollectionExamples

class ExternalInstanceCollectionExamples extends InstanceCollectionExamples {
  ExternalInstanceCollectionExamples() {
    super(ExternalStorageSuite.useVertx {
      new ExternalStorageModuleInstanceCollection(it,
        ExternalStorageSuite.storageAddress,
        ExternalStorageSuite.expectedTenant)
    })
  }
}
