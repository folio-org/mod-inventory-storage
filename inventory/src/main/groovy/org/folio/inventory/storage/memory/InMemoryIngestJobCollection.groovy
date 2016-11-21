package org.folio.inventory.storage.memory

import org.folio.inventory.resources.ingest.IngestJob
import org.folio.inventory.resources.ingest.IngestJobCollection
import org.folio.metadata.common.storage.memory.InMemoryCollection

class InMemoryIngestJobCollection implements IngestJobCollection {

  private final collection = new InMemoryCollection<IngestJob>()

  @Override
  void empty(Closure completionCallback) {
    collection.empty(completionCallback)
  }

  @Override
  void add(IngestJob item, Closure resultCallback) {
    collection.add(item.copyWithNewId(UUID.randomUUID().toString()),
      resultCallback)
  }

  @Override
  void findById(String id, Closure resultCallback) {
    collection.findOne({ it.id == id }, resultCallback)
  }

  @Override
  void findAll(Closure resultCallback) {
    collection.all(resultCallback)
  }

  @Override
  def update(IngestJob ingestJob, Closure completionCallback) {
    collection.replace(ingestJob, completionCallback)
  }
}
