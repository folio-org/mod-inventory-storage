package org.folio.inventory.storage.memory

import org.folio.inventory.storage.ItemCollectionExamples

class InMemoryItemCollectionExamples extends ItemCollectionExamples {
  InMemoryItemCollectionExamples() {
    super(new InMemoryCollections())
  }
}
