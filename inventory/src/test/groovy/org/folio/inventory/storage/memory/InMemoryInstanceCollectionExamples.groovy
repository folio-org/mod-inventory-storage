package org.folio.inventory.storage.memory

import org.folio.inventory.storage.InstanceCollectionExamples
import org.folio.inventory.storage.memory.InMemoryInstanceCollection

class InMemoryInstanceCollectionExamples extends InstanceCollectionExamples {
  InMemoryInstanceCollectionExamples() {
    super(new InMemoryInstanceCollection())
  }
}
