package org.folio.inventory.storage

import org.folio.inventory.storage.memory.InMemoryItemCollection

class InMemoryItemCollectionExamples extends ItemCollectionExamples {
  InMemoryItemCollectionExamples() {
    super(new InMemoryItemCollection())
  }
}
