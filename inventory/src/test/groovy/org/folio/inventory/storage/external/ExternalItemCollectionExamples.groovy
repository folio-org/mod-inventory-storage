package org.folio.inventory.storage.external

import org.folio.inventory.storage.ItemCollectionExamples

class ExternalItemCollectionExamples extends ItemCollectionExamples {

  ExternalItemCollectionExamples() {
    super(ExternalStorageSuite.useVertx {
      new ExternalStorageModuleItemCollection(it,
        ExternalStorageSuite.storageAddress,
        ExternalStorageSuite.expectedTenant) })
  }
}
