package org.folio.inventory.storage.memory

import org.folio.inventory.storage.InstanceCollectionExamples

class InMemoryInstanceCollectionExamples extends InstanceCollectionExamples {
  InMemoryInstanceCollectionExamples() {
    super(new InMemoryCollections())
  }
}
