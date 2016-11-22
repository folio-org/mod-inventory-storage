package org.folio.inventory.storage

import org.folio.inventory.storage.external.ExternalStorageModuleItemCollection

class ExternalItemCollectionExamples extends ItemCollectionExamples {

  ExternalItemCollectionExamples() {
    super(ExternalStorageSuite.useVertx {
      new ExternalStorageModuleItemCollection(it,
        ExternalStorageSuite.storageAddress) })
  }
}
