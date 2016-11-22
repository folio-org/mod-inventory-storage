package org.folio.inventory.storage.memory

import org.folio.inventory.storage.ItemCollectionExamples
import org.folio.inventory.storage.memory.InMemoryItemCollection

class InMemoryItemCollectionExamples extends ItemCollectionExamples {
  InMemoryItemCollectionExamples() {
    super(new InMemoryItemCollection())
  }
}
