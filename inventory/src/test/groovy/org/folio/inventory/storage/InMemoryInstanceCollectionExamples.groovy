package org.folio.inventory.storage

import org.folio.inventory.storage.memory.InMemoryInstanceCollection

class InMemoryInstanceCollectionExamples extends InstanceCollectionExamples {
  InMemoryInstanceCollectionExamples() {
    super(new InMemoryInstanceCollection())
  }
}
