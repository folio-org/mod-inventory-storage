package org.folio.inventory.storage

import org.folio.inventory.domain.ItemCollection
import org.folio.inventory.storage.external.ExternalStorageModuleItemCollection
import org.folio.inventory.storage.memory.InMemoryItemCollection
import org.folio.metadata.common.VertxAssistant
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.runners.Parameterized
import support.FakeInventoryStorageModule

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class ExternalItemCollectionExamples extends ItemCollectionExamples {

  ExternalItemCollectionExamples() {
    super(ExternalStorageSuite.useVertx { new ExternalStorageModuleItemCollection(it) })
  }
}
